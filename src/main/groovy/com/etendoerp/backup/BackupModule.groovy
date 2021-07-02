package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class BackupModule {

    static Logger log
    static CommandLine commandLine
    static File logFile
    static Project project

    static void load (Project project) {

        def extension = project.extensions.create("backup", BackupPluginExtension)

        log = Logger.getLogger(project)
        commandLine = CommandLine.getCommandLine(project)
        this.project = project

        project.ext.extFileTolog = logFile

        project.ext.setProperty("tmpBackupDir", null)
        project.ext.setProperty("finalBkpDir", null)
        project.ext.setProperty("baseBkpDir", null)
        project.ext.setProperty("confProperties", null)
        project.ext.setProperty("bkpDate", null)
        project.ext.setProperty("etendoConf", [:])

        project.gradle.taskGraph.afterTask { Task task, TaskState state ->
            if (task.name.startsWith("backup")) {
                if (state.failure) {
                    Throwable throwable = state.failure
                    def failureMessage ="Error on $task "
                    log.logToFile(LogLevel.ERROR, failureMessage, project.findProperty("extFileToLog") as File, throwable)
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

                def backupName = "backup-${project.ext.get("bkpDate")}.tar.gz"
                project.ext.setProperty("backupName", backupName)
                backup.archiveFileName.set(backupName)
                backup.destinationDirectory.set(bkpDir)
                backup.from(project.file(tmpDir))

            }
        }

        project.tasks.register("backupDeleteTmpFolder") {
            doLast {
                if ( project.ext.has("tmpBackupDir") && project.ext.get("tmpBackupDir") != null) {
                    log.logToFile(LogLevel.INFO, "Deleting tmp folder", project.findProperty("extFileToLog") as File)
                    project.delete("${(project.ext.get("tmpBackupDir") as File).absolutePath}")
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
                log.logToFile(LogLevel.ERROR, "Error on backup Configuration", project.findProperty("extFileToLog") as File, e)
                throw e
            }

            doFirst {
                log.logToFile(LogLevel.INFO, "Calculating sha1 checksums", project.findProperty("extFileToLog") as File)
                File tmpDir = project.ext.getProperty("tmpBackupDir") as File

                // Default ubuntu command
                def sha = "sha1sum"

                if (project.hasProperty("mac")) {
                    sha = "shasum -a 1"
                }

                commandLine.run("sh","-c",""" cd ${tmpDir.absolutePath} && $sha * > sha1""")
                log.logToFile(LogLevel.INFO, "Creating the backup file", project.findProperty("extFileToLog") as File)
            }

            doLast {
                def folderName = (destinationDirectory as DirectoryProperty).get().asFile.absolutePath
                log.logToFile(LogLevel.INFO, "Backup done: ${folderName}/${archiveFileName.get()} Created ", project.findProperty("extFileToLog") as File)

                // Sync and rotation

                // Rotation
                def mode = project.findProperty("bkpMode")
                def rotationEnabled = project.findProperty("etendoConf")?.ROTATION_ENABLED
                if (mode == "auto" && rotationEnabled == "yes" ) {
                    BackupUtils.runRotation(project)
                }

                log.logToFile(LogLevel.INFO, "Backup Finalized", project.findProperty("extFileToLog") as File)
                project.ext.set("checker", "finalized")

                BackupUtils.saveLogs(project)
            }
        }
    }
}