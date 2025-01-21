package com.etendoerp.backup

import com.etendoerp.backup.email.EmailSender
import com.etendoerp.backup.email.EmailType
import com.etendoerp.backup.mode.Mode
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

import com.etendoerp.conventions.ConventionNames as CN
import com.etendoerp.backup.BackupModule as BM

class BackupUtils {

    final static String DEFAULT_USER  = "undefined"
    final static String DEFAULT_GROUP = "undefined"

    final static String LOG_EXTENSION = ".log"

    static loadBackupConfigurations(Project project) {
        Logger log = Logger.getLogger(project)

        CommandLine commandLine = CommandLine.getCommandLine(project)

        def etendoConf = loadEtendoBackupConf(project)

        def confPath = project.extensions.getByName("backup").configPath.get()

        // Set backup DATE, original to the script format
        def date = "nodate"
        def (exit, output) = commandLine.run("date", "-u", '+"%Y%m%d-%H%M-%Z"')
        if (exit == 0) {
            date = output.replace("\"","").replace("\n","")
            project.setProperty(BM.CURRENT_DATE, date)
        }

        // Create file to log in a selected location
        // User should have permissions to create the backup log file
        if (!project.ext.has(BM.FILE_TO_LOG) || project.ext.get(BM.FILE_TO_LOG) == null) {
            def tmpLogLocation = (etendoConf?.EMAIL_TEMP_FILE as String).replace(LOG_EXTENSION,"")
            tmpLogLocation = tmpLogLocation.concat("-${date}${LOG_EXTENSION}")
            File logFile = project.file(tmpLogLocation)
            project.ext.set(BM.FILE_TO_LOG, logFile)
        }

        log.logToFile(LogLevel.INFO, "Starting backup configuration", project.findProperty(BM.FILE_TO_LOG) as File)
        log.logToFile(LogLevel.INFO, "Configuration properties: ${confPath}", project.findProperty(BM.FILE_TO_LOG) as File)

        // Get private IP
        (exit, output) = commandLine.run("hostname","-I")
        if (exit == 0) {
            log.logToFile(LogLevel.INFO, "Private IP: ${output.replace("\n","")}", project.findProperty(BM.FILE_TO_LOG) as File)
        }

        // Get public IP
        (exit, output) = commandLine.run("wget","-qO-","ifconfig.me")
        if (exit == 0) {
            log.logToFile(LogLevel.INFO, "Public IP: ${output}", project.findProperty(BM.FILE_TO_LOG) as File)
        }

        // Configure Mode
        def mode = project.findProperty(BM.BACKUP_MODE)

        // Set default mode
        if (!mode) {
            mode = Mode.MANUAL
            project.ext.set(BM.BACKUP_MODE, mode)
        }

        if (!Mode.containsVal(mode as String)) {
            throw new IllegalArgumentException(
                    "Invalid backup mode ${mode?.toString()}, valid options: ${Mode.values()*.value}. \n" +
                            "Provide a correct ${BM.BACKUP_MODE} property or -P${BM.BACKUP_MODE} flag. Ex -P${BM.BACKUP_MODE}=${Mode.MANUAL.value}"
            )
        }

        log.logToFile(LogLevel.INFO, "Backup mode: ${mode}", project.findProperty(BM.FILE_TO_LOG) as File)

        def user = etendoConf?.USER ?: DEFAULT_USER

        (exit, output) = commandLine.run(false, "id","-u","-n")
        def currentUser = "undefined"

        if (exit == 0) {
            currentUser = (output as String).replace("\n","")
            if (currentUser != user) {
                throw new IllegalArgumentException("The current user running the program (${currentUser}) " +
                        "is not the same specified in the etendo backup config properties (${user})")
            }
        }

        (exit, output) = commandLine.run("true")
        if (exit != 0) {
            throw new IllegalArgumentException("* The user ${currentUser} has not SUDO access")
        }

        def bkpEnabled = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.BACKUP_ENABLED
        if (bkpEnabled != "yes") {
            throw new IllegalArgumentException("Backup NOT done. Please enable and configure the backup in ${confPath}")
        }

    }

    /**
     * Loads the properties extracted from the 'backup.properties' file.
     * A user can customize this properties inside the file
     * @param project
     * @return
     */
    static loadEtendoBackupConf(Project project) {

        if (project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)) {
            return project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)
        }

        def confPath = project.extensions.getByName("backup").configPath.get()

        def confFile = project.file(confPath)
        if (!confFile.exists()) {
            throw new IllegalArgumentException("Backup configuration file: ${confPath} not found")
        }

        def etendoConf = new Properties()
        confFile.withInputStream { etendoConf.load(it) }
        project.ext.setProperty(BM.ETENDO_BACKUP_PROPERTIES, etendoConf)
        return etendoConf
    }


    /**
     * Loads the properties extracted from the sources 'config' directory
     * @param project
     * @return
     */
    static loadConfigurationProperties(Project project) {
        Logger log = Logger.getLogger(project)

        // Configuration already exits
        if (project.ext.has(BM.CONFIG_PROPERTIES) && project.ext.get(BM.CONFIG_PROPERTIES) != null) {
            return project.ext.get(BM.CONFIG_PROPERTIES) as Map
        }

        log.logToFile(LogLevel.INFO, "Generating configuration properties", project.findProperty(BM.FILE_TO_LOG) as File)

        def tmpMap = [:]

        def confPath = CN.DEFAULT_CONFIG_PROPERTIES_LOCATION

        def confFile = project.file(confPath)

        if (!confFile.exists()) {
            throw new IllegalArgumentException("Configuration properties path: ${confPath} not found")
        }

        def opb_props = new Properties()
        confFile.withInputStream { opb_props.load(it) }

        def db_url_arr   = opb_props.getProperty("bbdd.url").split(":")

        tmpMap.put("db_host"      , db_url_arr[2].replace("/",""))
        tmpMap.put("db_port"      , db_url_arr[3])
        tmpMap.put("db_login"     , opb_props.getProperty("bbdd.user", "tad"))
        tmpMap.put("db_pass"      , opb_props.getProperty("bbdd.password", "tad"))
        tmpMap.put("db_name"      , opb_props.getProperty("bbdd.sid", "etendo"))
        tmpMap.put("db_syspass"   , opb_props.getProperty("bbdd.systemPassword", "syspass"))
        tmpMap.put("context_name" , opb_props.getProperty("context.name", "etendo"))
        tmpMap.put("attach_path"  , opb_props.getProperty("attach.path", ""))

        log.logToFile(LogLevel.INFO, "Configuration properties: \n" +
                " * db_login     ${tmpMap.db_login} \n" +
                " * db_pass      ${tmpMap.db_pass} \n" +
                " * db_name      ${tmpMap.db_name} \n" +
                " * db_host      ${tmpMap.db_host} \n" +
                " * db_port      ${tmpMap.db_port} \n" +
                " * db_syspass   ${tmpMap.db_syspass} \n" +
                " * context_name ${tmpMap.context_name} \n" +
                " * attach_path  ${tmpMap.attach_path}", project.findProperty(BM.FILE_TO_LOG) as File)

        project.ext.setProperty(BM.CONFIG_PROPERTIES, tmpMap)
        return tmpMap
    }

    static generateTmpDir(Project project) {
        Logger log = Logger.getLogger(project)
        CommandLine commandLine = CommandLine.getCommandLine(project)

        if (project.ext.has(BM.TMP_DIR) && project.ext.get(BM.TMP_DIR) != null) {
            return project.ext.get(BM.TMP_DIR) as File
        }

        log.logToFile(LogLevel.INFO, "Starting creation of tmp backup dir", project.findProperty(BM.TMP_DIR) as File)

        File tmpDir

        def etendoConf = loadEtendoBackupConf(project)
        def user  = etendoConf?.USER ?: DEFAULT_USER
        def group = etendoConf?.GROUP ?: DEFAULT_GROUP
        def bkpTmpDir = etendoConf?.BACKUPS_TMP_DIR
        if (bkpTmpDir) {
            def uuid = UUID.randomUUID().toString()
            def tmpDirPath = "${bkpTmpDir}/backup-tmp-${uuid}"
            commandLine.run(false, "mkdir","-p", tmpDirPath)
            tmpDir = project.file(tmpDirPath)
        } else {
            tmpDir = File.createTempDir()
        }

        project.ext.set(BM.TMP_DIR, tmpDir)
        log.logToFile(LogLevel.INFO, "tmp backup dir created: ${tmpDir.absolutePath}", project.findProperty(BM.FILE_TO_LOG) as File)

        return tmpDir
    }

    static generateBackupDir(Project project) {
        Logger log = Logger.getLogger(project)
        CommandLine commandLine = CommandLine.getCommandLine(project)

        def bkpDir = project.findProperty(BM.FINAL_DIR)
        if (bkpDir) {
            return bkpDir
        }

        def mode = project.findProperty(BM.BACKUP_MODE)

        // Configure Backup dir
        // Searches the System property bkpDir, could be passed in console with -DbkpDir option
        // If the system property does not exists, searches in the Project
        def etendoBkpDir = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.BACKUPS_DIR
        def baseBackupDir = (System.getProperty("bkpDir") ?: etendoBkpDir ?: project.findProperty("bkpDir") ?: "/backups").toString()
        def finalBackupDir = "${baseBackupDir}/${mode}"

        log.logToFile(LogLevel.INFO, "Creating backup dir: ${finalBackupDir}", project.findProperty(BM.FILE_TO_LOG) as File)

        def etendoConf = loadEtendoBackupConf(project)
        def user  = etendoConf?.USER ?: DEFAULT_USER
        def group = etendoConf?.GROUP ?: DEFAULT_GROUP

        try {
            File backupPath = new File(baseBackupDir)
            if (!backupPath.exists()) {
                String errorMsg = "Backup directory '${baseBackupDir}' does not exist. Please create it and grant the necessary permissions."
                throw new IllegalStateException(errorMsg)
            } else if (!backupPath.canWrite()) {
                String errorMsg = "No write permissions on directory '${baseBackupDir}'. Please grant write permissions."
                throw new IllegalStateException(errorMsg)
            }
            
            log.logToFile(LogLevel.INFO, "Creating backup dir: ${finalBackupDir}", project.findProperty(BM.FILE_TO_LOG) as File)
            if (!new File(finalBackupDir).exists()) {
                def exit = commandLine.run(false, "mkdir", "-p", finalBackupDir)
                if (exit != 0) {
                    throw new IllegalStateException("Failed to create final backup directory: ${finalBackupDir}")
                }
            }
        } catch (Exception e) {
            throw e
        }

        log.logToFile(LogLevel.INFO, "Backup dir: ${finalBackupDir} created", project.findProperty(BM.FILE_TO_LOG) as File)

        project.ext.setProperty(BM.BASE_DIR, baseBackupDir)
        project.ext.setProperty(BM.FINAL_DIR, finalBackupDir)
        return project.file(finalBackupDir)
    }


    static generateTaskDep(Project project) {
        Logger log = Logger.getLogger(project)

        def deps = [
                "backupCompressSourcesTar",
                "backupCompressDatabaseDump",
                "backupCompressExternalAttachments"
            ]

        if (project.hasProperty("skipSources")) {
            log.logToFile(LogLevel.INFO, "Skipping sources", project.findProperty(BM.FILE_TO_LOG) as File)
            deps.remove("backupCompressSourcesTar")
        }

        if (project.hasProperty("skipDatabase")) {
            log.logToFile(LogLevel.INFO, "Skipping database", project.findProperty(BM.FILE_TO_LOG) as File)
            deps.remove("backupCompressDatabaseDump")
        }

        if (project.hasProperty("includeWebapp")) {
            log.logToFile(LogLevel.INFO, "Including webapp folder", project.findProperty(BM.FILE_TO_LOG) as File)
            deps.add("backupCompressWebapp")
        }

        return deps
    }

    static runRotation(Project project) {
        Logger log = Logger.getLogger(project)
        try {
            log.logToFile(LogLevel.INFO, "Running rotation", project.findProperty(BM.FILE_TO_LOG) as File)

            def etendoConf = loadEtendoBackupConf(project)

            def rotNumToMaintain = etendoConf?.ROTATION_NUM_TO_MAINTAIN as String

            if (!rotNumToMaintain || !rotNumToMaintain.isInteger() || rotNumToMaintain.toInteger() <= 0) {
                log.logToFile(LogLevel.WARN,"Rotation NOT done", project.findProperty(BM.FILE_TO_LOG) as File)
                def confPath = project.extensions.getByName("backup").configPath.get()
                log.logToFile(LogLevel.WARN,"ROTATION_NUM_TO_MAINTAIN variable in ${confPath} must be a number greater than 0.", project.findProperty(BM.FILE_TO_LOG) as File)
                return
            }

            rotNumToMaintain = rotNumToMaintain.toInteger()

            def bkpDir = project.findProperty(BM.FINAL_DIR)
            File bkpFolder = new File(bkpDir)

            // Get list of backups
            def files = []
            bkpFolder.traverse(maxDepth:0) {
                files.add(it)
            }

            // Sort list of backups
            files.sort()

            def numFilesToDelete = files.size() - rotNumToMaintain

            log.logToFile(LogLevel.INFO, "Rotation - Total number of backups: ${files.size()}, backups to maintain: ${rotNumToMaintain} ", project.findProperty(BM.FILE_TO_LOG) as File)

            if (numFilesToDelete >= files.size()) {
                log.logToFile(LogLevel.WARN,"Rotation NOT done", project.findProperty(BM.FILE_TO_LOG) as File)
                log.logToFile(LogLevel.WARN,"Selected all backups to delete, something is wrong, skipping delete.", project.findProperty(BM.FILE_TO_LOG) as File)
                return
            }

            if (numFilesToDelete >= 1) {
                log.logToFile(LogLevel.INFO, "Rotation - Files to delete: ${numFilesToDelete}", project.findProperty(BM.FILE_TO_LOG) as File)
                for (int i = 0; i < numFilesToDelete; i++) {
                    log.logToFile(LogLevel.INFO,"Rotation - Deleting ${files.get(i)}", project.findProperty(BM.FILE_TO_LOG) as File)
                    project.delete(files.get(i))
                }
            }

            log.logToFile(LogLevel.INFO, "Rotation finalized", project.findProperty(BM.FILE_TO_LOG) as File)

        } catch (Exception e) {
            log.logToFile(LogLevel.WARN, "Rotation NOT done", project.findProperty(BM.FILE_TO_LOG) as File, e)
        }
    }

    static handleError(Project project) {
        try {
            // Delete tmp folder and current backup if exists
            deleteTmpDir(project)

            def backupName = project.findProperty(BM.BACKUP_NAME)
            def finalBkpDir = project.findProperty(BM.FINAL_DIR)

            if (finalBkpDir && backupName && !project.findProperty(BM.BACKUP_DONE_FLAG)) {
                finalBkpDir = project.file(finalBkpDir)
                def backupFile = project.file("${finalBkpDir.absolutePath}/${backupName}")
                if (backupFile.exists()) {
                    project.logger.info("Deleting backup file: ${backupFile.absolutePath}")
                    project.delete(backupFile)
                }
            }

        } catch (Exception e) {
            project.logger.info("Error deleting tmp/backup file: ${e.getMessage()}")
            throw e
        } finally {
            saveLogs(project)
            def logFile = project.findProperty(BM.FILE_TO_LOG) as File
            // Send email in 'auto' backup
            EmailSender emailSender = new EmailSender(project)
            emailSender.sendEmailWithLog(logFile, EmailType.ERROR)
        }
    }

    static saveLogs(Project project) {
        try {
            CommandLine commandLine = CommandLine.getCommandLine(project)

            def mode = project.findProperty(BM.BACKUP_MODE)
            def logFile = project.findProperty(BM.FILE_TO_LOG) as File

            if (!mode || !logFile || !Mode.containsVal(mode as String)) {
                throw new IllegalArgumentException("Mode:$mode or logFile:${logFile?.absolutePath} not found")
            }

            // Generates the base dir if not exists
            generateBackupDir(project)

            def baseDir = project.findProperty(BM.BASE_DIR)

            def finalLogDir = "$baseDir/logs/$mode"

            def etendoConf = loadEtendoBackupConf(project)
            def user  = etendoConf?.USER ?: DEFAULT_USER
            def group = etendoConf?.GROUP ?: DEFAULT_GROUP

            // Create the directory where to save logs
            commandLine.run(false,"mkdir","-p",finalLogDir)

            // Move the log
            project.logger.info("Moving ${logFile.absolutePath} into ${finalLogDir}")
            commandLine.run(false,"mv","${logFile.absolutePath}","$finalLogDir")

            // Update the log file location
            def newLogFileLoc = project.file("${finalLogDir}/${logFile.name}")
            project.ext.set(BM.FILE_TO_LOG, newLogFileLoc)

        } catch (Exception e) {
            project.logger.info("Error saving the logs: ${e.getMessage()}")
        }
    }

    /**
     * Sends an email when the backups finalizes,
     * only if the 'SEND_EMAIL_ON_SUCCESS' property flag is activated
     * or there is a WARNING and the 'SEND_EMAIL_ON_WARNING' flag is set to 'yes'
     * @param project
     */
    static sendFinalizedEmail(Project project) {
        // Check if send email on WARNING or SUCCESS

        def sendEmailOnWarning = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.SEND_EMAIL_ON_WARNING
        def sendEmailOnSuccess = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.SEND_EMAIL_ON_SUCCESS

        def logFile = project.file(project.findProperty(BM.FILE_TO_LOG))
        EmailType emailType = null

        if (sendEmailOnSuccess == "yes") {
            emailType = EmailType.SUCCESS
        }

        // WARNING has priority
        if(sendEmailOnWarning == "yes" && project.findProperty(BM.WARNING_FLAG)) {
            emailType = EmailType.WARNING
        }

        if (emailType) {
            EmailSender emailSender = new EmailSender(project);
            emailSender.sendEmailWithLog(logFile, emailType)
        }
    }

    static deleteTmpDir(Project project) {
        def tmpDir = project.findProperty(BM.TMP_DIR) as File
        if (tmpDir && project.file(tmpDir).exists()) {
            project.logger.info("Deleting tmp dir: ${tmpDir.absolutePath}")
            return project.delete(tmpDir)
        }
        return false
    }
}