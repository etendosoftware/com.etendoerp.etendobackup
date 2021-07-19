package com.etendoerp.backup

import com.etendoerp.backup.email.EmailSender
import com.etendoerp.backup.email.EmailType
import com.etendoerp.backup.mode.Mode
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

import com.etendoerp.conventions.ConventionNames as CN

class BackupModule {

    static Logger log
    static CommandLine commandLine
    static Project project

    /**
     * Project property to be inserted via command line.
     */
    final static String BACKUP_MODE  = "bkpMode"
    final static String BACKUP_MAC   = "mac"

    final static String TMP_DIR      = "backupTmpDir"
    final static String FINAL_DIR    = "backupFinalDir"
    final static String BASE_DIR     = "backupBaseDir"
    final static String CURRENT_DATE = "backupCurrentDate"
    final static String FILE_TO_LOG  = "backupFileToLog"
    final static String BACKUP_NAME  = "backupName"

    final static String EMAIL_IS_SENDING = "backupEmailIsSending"
    final static String ERROR_HANDLED    = "backupErrorHandled"
    final static String WARNING_FLAG     = "backupWarningFlag"
    final static String BACKUP_DONE_FLAG = "backupDoneFlag"

    /**
     * Properties extracted from '/config' directory
     */
    final static String CONFIG_PROPERTIES = "backupConfigProperties"

    /**
     * Properties extracted from 'etendo-backup.conf.properties' file
     */
    final static String ETENDO_BACKUP_PROPERTIES = "backupEtendoConfigProperties"


    static void load (Project project) {

        def extension = project.extensions.create("backup", BackupPluginExtension)

        log = Logger.getLogger(project)
        commandLine = CommandLine.getCommandLine(project)
        this.project = project

        project.ext.set(TMP_DIR      ,null)
        project.ext.set(FINAL_DIR    ,null)
        project.ext.set(BASE_DIR     ,null)
        project.ext.set(CURRENT_DATE ,null)
        project.ext.set(FILE_TO_LOG  ,null)
        project.ext.set(BACKUP_NAME  ,null)

        project.ext.set(EMAIL_IS_SENDING ,false)
        project.ext.set(ERROR_HANDLED    ,false)
        project.ext.set(WARNING_FLAG     ,false)
        project.ext.set(BACKUP_DONE_FLAG ,false)

        project.ext.set(CONFIG_PROPERTIES        ,null)
        project.ext.set(ETENDO_BACKUP_PROPERTIES ,[:])

        project.gradle.taskGraph.afterTask { Task task, TaskState state ->
            if (task.name.startsWith("backup")) {
                if (state.failure) {
                    Throwable throwable = state.failure
                    def failureMessage ="Error on $task "
                    log.logToFile(LogLevel.ERROR, failureMessage, project.findProperty(FILE_TO_LOG) as File, throwable)
                }
            }
        }

        // Load compress sources tasks
        BackupCompressSourcesTask.load(project)

        // Load compress external attachments tasks
        BackupCompressExternalAttachmentsTask.load(project)

        // Load database fix script tasks
        BackupDatabaseFixScriptTask.load(project)

        // Load database dump tasks
        BackupDatabaseDumpTask.load(project)

        // Load compress database dump tasks
        BackupCompressDatabaseDumpTask.load(project)

        // Load compress webapp tasks
        BackupCompressWebappTask.load(project)

        // backup task

        project.tasks.register("backupConfig") {
            doLast {
                BackupUtils.loadBackupConfigurations(project)
                BackupUtils.loadConfigurationProperties(project)
                def tmpDir = BackupUtils.generateTmpDir(project)
                def bkpDir = BackupUtils.generateBackupDir(project)

                // Configure 'backup' task from another task to prevent 'Eagerly' configuration
                Task backup = project.tasks.named("backup").get() as Tar

                def backupName = "${CN.BACKUP_TAR_NAME}-${project.ext.get(CURRENT_DATE)}${CN.EXTENSION}"
                project.ext.setProperty(BACKUP_NAME, backupName)
                backup.archiveFileName.set(backupName)
                backup.destinationDirectory.set(bkpDir)
                backup.from(project.file(tmpDir))
            }
        }

        project.tasks.register("backupDeleteTmpFolder") {
            doLast {
                if (BackupUtils.deleteTmpDir(project)) {
                    log.logToFile(LogLevel.INFO, "Tmp folder deleted", project.findProperty(FILE_TO_LOG) as File)
                }
            }
        }

        project.tasks.register("backup", Tar) {
            try {
                def taskDep = BackupUtils.generateTaskDep(project)
                dependsOn "backupConfig"
                dependsOn (taskDep)
                compression = Compression.GZIP
                finalizedBy project.tasks.named("backupDeleteTmpFolder")
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backup Configuration", project.findProperty(FILE_TO_LOG) as File, e)
                throw e
            }

            doFirst {
                log.logToFile(LogLevel.INFO, "Calculating sha1 checksums", project.findProperty(FILE_TO_LOG) as File)
                File tmpDir = project.ext.getProperty(TMP_DIR) as File

                // Default ubuntu command
                def sha = "sha1sum"

                if (project.hasProperty(BACKUP_MAC)) {
                    sha = "shasum -a 1"
                }

                commandLine.run("sh","-c",""" cd ${tmpDir.absolutePath} && $sha * > sha1""")
                log.logToFile(LogLevel.INFO, "Creating the backup file", project.findProperty(FILE_TO_LOG) as File)
            }

            doLast {
                project.ext.setProperty(BACKUP_DONE_FLAG, true)
                def folderName = (destinationDirectory as DirectoryProperty).get().asFile.absolutePath
                log.logToFile(LogLevel.INFO, "Backup done: ${folderName}/${archiveFileName.get()} Created ", project.findProperty(FILE_TO_LOG) as File)

                // Sync and rotation

                // Rotation
                def mode = project.findProperty(BACKUP_MODE)
                def rotationEnabled = project.findProperty(ETENDO_BACKUP_PROPERTIES)?.ROTATION_ENABLED
                if (mode == Mode.AUTO.value && rotationEnabled == "yes" ) {
                    BackupUtils.runRotation(project)
                }

                log.logToFile(LogLevel.INFO, "Backup Finalized", project.findProperty(FILE_TO_LOG) as File)

                BackupUtils.sendFinalizedEmail(project)
                BackupUtils.saveLogs(project)
            }
        }
    }
}