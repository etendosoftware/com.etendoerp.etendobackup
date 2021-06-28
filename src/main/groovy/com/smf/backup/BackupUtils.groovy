package com.smf.backup

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class BackupUtils {

    static loadBackupConfigurations(Project project) {
        Logger log = Logger.getLogger(project)
        try {

            CommandLine commandLine = CommandLine.getCommandLine(project)

            loadEtendoBackupConf(project)

            def confPath = project.extensions.getByName("backup").configPath.get()

            // Set backup DATE, original to the script format
            def date = "nodate"
            def (exit, output) = commandLine.run("date", "-u", '+"%Y%m%d-%H%M-%Z"')
            if (exit == 0) {
                date = output.replace("\"","").replace("\n","")
                project.setProperty("bkpDate", date)
            }

            // Create file to log (in tmp folder)
            // TODO: Select location from the conf file
            if (!project.ext.has("extFileToLog") || project.ext.get("extFileToLog") == null) {
                File logFile = project.file("backups-logs-${date}.txt")
                project.ext.set("extFileToLog", logFile)
            }

            log.logToFile(LogLevel.INFO, "Starting backup configuration", project.findProperty("extFileToLog") as File)
            log.logToFile(LogLevel.INFO, "Configuration properties: ${confPath}", project.findProperty("extFileToLog") as File)

            // Get private IP
            (exit, output) = commandLine.run("hostname","-I")
            if (exit == 0) {
                log.logToFile(LogLevel.INFO, "Private IP: ${output}", project.findProperty("extFileToLog") as File)
            }

            // Get public IP
            (exit, output) = commandLine.run("wget","-qO-","ifconfig.me")
            if (exit == 0) {
                log.logToFile(LogLevel.INFO, "Public IP: ${output}", project.findProperty("extFileToLog") as File)
            }

            // Configure Mode
            def mode = project.findProperty("bkpMode")
            if (mode != "auto" && mode != "manual") {
                throw new IllegalArgumentException(
                        "Invalid backup mode ${mode?.toString()}, valid options: auto , manual. \n" +
                                "Provide a correct bkpMode property or -PbkpMode flag. Ex -PbkpMode=manual"
                )
            }

            log.logToFile(LogLevel.INFO, "Backup mode: ${mode}", project.findProperty("extFileToLog") as File)

            // TODO: change user:group, add it to the conf file
            (exit, output) = commandLine.run(false, "id","-u","-n")
            if (exit == 0) {
                if (output.replace("\n","") != "futit") {
                    throw new IllegalArgumentException("You need to run this as openbravo user. Actual user: ${output}")
                }
            }

            def bkpEnabled = project.findProperty("etendoConf")?.BACKUP_ENABLED
            if (bkpEnabled != "yes") {
                throw new IllegalArgumentException("Backup NOT done. Please enable and configure the backup in ${confPath}")
            }

        } catch (Exception e) {
            log.logToFile(LogLevel.ERROR, "Error on backup configuration")
            throw e
        }

    }

    static loadEtendoBackupConf(Project project) {

        if (project.findProperty("etendoConf")) {
            return project.findProperty("etendoConf")
        }

        def confPath = project.extensions.getByName("backup").configPath.get()

        def confFile = project.file(confPath)
        if (!confFile.exists()) {
            throw new IllegalArgumentException("Backup configuration file: ${confPath} not found")
        }

        def etendoConf = new Properties()
        confFile.withInputStream { etendoConf.load(it) }
        project.ext.setProperty("etendoConf", etendoConf)
        return etendoConf
    }

    static loadConfigurationProperties(Project project) {
        Logger log = Logger.getLogger(project)

        // Configuration already exits
        if (project.ext.has("confProperties") && project.ext.get("confProperties") != null) {
            return project.ext.get("confProperties") as Map
        }

        log.logToFile(LogLevel.INFO, "Generating configuration properties", project.findProperty("extFileToLog") as File)

        def tmpMap = [:]

        def confPath = "config/Openbravo.properties"

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
        //extLogToFile(LogLevel.INFO, "Database properties: ${tmpMap.toString()}\n", project.findProperty("extFileToLog"))
        log.logToFile(LogLevel.INFO, "Configuration properties: \n" +
                " * db_login     ${tmpMap.db_login} \n" +
                " * db_pass      ${tmpMap.db_pass} \n" +
                " * db_name      ${tmpMap.db_name} \n" +
                " * db_host      ${tmpMap.db_host} \n" +
                " * db_port      ${tmpMap.db_port} \n" +
                " * db_syspass   ${tmpMap.db_syspass} \n" +
                " * context_name ${tmpMap.context_name} \n" +
                " * attach_path  ${tmpMap.attach_path}", project.findProperty("extFileToLog") as File)

        project.ext.setProperty("confProperties", tmpMap)
        return tmpMap
    }

    static generateTmpDir(Project project) {
        Logger log = Logger.getLogger(project)
        CommandLine commandLine = CommandLine.getCommandLine(project)

        if (project.ext.has("tmpBackupDir") && project.ext.get("tmpBackupDir") != null) {
            return project.ext.get("tmpBackupDir") as File
        }

        log.logToFile(LogLevel.INFO, "Starting creation of tmp backup dir", project.findProperty("extFileToLog") as File)
        File tmpDir = File.createTempDir()

        // TODO: change user:group, add it to the conf file
        def bkpTmpDir = project.findProperty("etendoConf")?.BACKUPS_TMP_DIR
        if (bkpTmpDir) {
            def tmpDirPath = "${bkpTmpDir}/${tmpDir.name}"
            commandLine.run(false, "mkdir","-p", tmpDirPath)
            commandLine.run(false,"sudo","chown","futit:futit",tmpDirPath)
            tmpDir = project.file(tmpDirPath)
        }

        project.ext.set("tmpBackupDir", tmpDir)
        log.logToFile(LogLevel.INFO, "tmp backup dir created: ${tmpDir.absolutePath}", project.findProperty("extFileToLog") as File)

        return tmpDir
    }

    static generateBackupDir(Project project) {
        Logger log = Logger.getLogger(project)
        CommandLine commandLine = CommandLine.getCommandLine(project)

        def bkpDir = project.findProperty("finalBkpDir")
        if (bkpDir) {
            return bkpDir
        }

        def mode = project.findProperty("bkpMode")

        // Configure Backup dir
        // Searches the System property bkpDir, could be passed in console with -DbkpDir option
        // If the system property does not exists, searches in the Project
        def etendoBkpDir = project.findProperty("etendoConf")?.BACKUPS_DIR
        def baseBackupDir = (System.getProperty("bkpDir") ?: etendoBkpDir ?: project.findProperty("bkpDir") ?: "/backups").toString()
        def finalBackupDir = "${baseBackupDir}/${mode}"

        log.logToFile(LogLevel.INFO, "Creating backup dir: ${finalBackupDir}", project.findProperty("extFileToLog") as File)

        commandLine.run(false,"sudo","mkdir","-p",baseBackupDir)
        commandLine.run(false,"sudo","chown","futit:futit",baseBackupDir)
        commandLine.run(false,"sudo","mkdir","-p",finalBackupDir)
        commandLine.run(false,"sudo","chown","futit:futit",finalBackupDir)

        log.logToFile(LogLevel.INFO, "Backup dir: ${finalBackupDir} created", project.findProperty("extFileToLog") as File)

        project.ext.setProperty("finalBkpDir", finalBackupDir)
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
            log.logToFile(LogLevel.INFO, "Skipping sources", project.findProperty("extFileToLog") as File)
            deps.remove("backupCompressSourcesTar")
        }

        if (project.hasProperty("skipDatabase")) {
            log.logToFile(LogLevel.INFO, "Skipping database", project.findProperty("extFileToLog") as File)
            deps.remove("backupCompressDatabaseDump")
        }

        // TODO: see if include webapp folder from conf file or Project properties (command line)
        if (project.hasProperty("includeWebapp")) {
            log.logToFile(LogLevel.INFO, "Including webapp", project.findProperty("extFileToLog") as File)
            deps.add("backupCompressWebapp")
        }

        def attachPath = project.findProperty("confProperties")?.attach_path
        def attachCopy = project.findProperty("etendoConf")?.ATTACH_COPY
        if (attachCopy == "yes" && attachPath != "${project.rootDir.absolutePath}/attachments") {
            deps.add("backupCompressExternalAttachments")
        }

        return deps
    }

    static runRotation(Project project) {
        Logger log = Logger.getLogger(project)
        try {
            log.logToFile(LogLevel.INFO, "Running rotation", project.findProperty("extFileToLog") as File)

            def etendoConf = loadEtendoBackupConf(project)

            def rotNumToMaintain = etendoConf?.ROTATION_NUM_TO_MAINTAIN as String

            if (!rotNumToMaintain || !rotNumToMaintain.isInteger() || rotNumToMaintain.toInteger() <= 0) {
                log.logToFile(LogLevel.WARN,"Rotation NOT done", project.findProperty("extFileToLog") as File)
                log.logToFile(LogLevel.WARN,"ROTATION_NUM_TO_MAINTAIN variable in /etc/openbravo-backup.conf must be a number greater than 0.", project.findProperty("extFileToLog") as File)
                return
            }

            rotNumToMaintain = rotNumToMaintain.toInteger()

            def bkpDir = project.findProperty("finalBkpDir")
            File bkpFolder = new File(bkpDir)

            // Get list of backups
            def files = []
            bkpFolder.traverse(maxDepth:0) {
                files.add(it)
            }

            // Sort list of backups
            files.sort()

            def numFilesToDelete = files.size() - rotNumToMaintain

            log.logToFile(LogLevel.INFO, "Rotation - Total number of backups: ${files.size()}, backups to maintain: ${rotNumToMaintain} ", project.findProperty("extFileToLog") as File)

            if (numFilesToDelete >= files.size()) {
                log.logToFile(LogLevel.WARN,"Rotation NOT done", project.findProperty("extFileToLog") as File)
                log.logToFile(LogLevel.WARN,"Selected all backups to delete, something is wrong, skipping delete.", project.findProperty("extFileToLog") as File)
                return
            }

            if (numFilesToDelete >= 1) {
                log.logToFile(LogLevel.INFO, "Rotation - Files to delete: ${numFilesToDelete}", project.findProperty("extFileToLog") as File)
                for (int i = 0; i < numFilesToDelete; i++) {
                    log.logToFile(LogLevel.INFO,"Rotation - Deleting ${files.get(i)}", project.findProperty("extFileToLog") as File)
                    project.delete(files.get(i))
                }
            }

            log.logToFile(LogLevel.INFO, "Rotation finalized", project.findProperty("extFileToLog") as File)

        } catch (Exception e) {
            log.logToFile(LogLevel.WARN, "Rotation NOT done", project.findProperty("extFileToLog") as File, e)
        }
    }


}