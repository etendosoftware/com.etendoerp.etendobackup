package com.smf.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class BackupCompressWebappTask {

    final static String WEBAPP_TAR_NAME = "wepapp"

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressWebappConfig") {
            doLast {
                // TODO: Get tomcat webapp folder
                def conf = BackupUtils.loadEtendoBackupConf(project)

                File tmpDir = BackupUtils.generateTmpDir(project)
                Task webappTar = project.tasks.named("backupCompressWebapp").get() as Tar

                webappTar.archiveFileName.set("${WEBAPP_TAR_NAME}.tar.gz")
                webappTar.destinationDirectory.set(tmpDir)
                webappTar.from(project.file("/var/lib/tomcat8/webapps/etendo"))
            }
        }

        project.tasks.register("backupCompressWebapp", Tar) {
            try {
                log.logToFile(LogLevel.INFO, "Starting backupCompressWebapp Configuration", project.findProperty("extFileToLog") as File)
                mustRunAfter = ["backupConfig"]
                dependsOn "backupCompressWebappConfig"
                compression = Compression.GZIP
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupCompressWebapp Configuration", project.findProperty("extFileToLog") as File, e)
                throw e
            }

            doFirst {
                if (!project.findProperty("includeWebapp")) {
                    throw new StopExecutionException("Skipping webapp folder")
                }
                log.logToFile(LogLevel.INFO, "Compressing webapp", project.findProperty("extFileToLog") as File)
            }

            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing webapp' execution finalized.", project.findProperty("extFileToLog") as File)
            }
        }

    }

}
