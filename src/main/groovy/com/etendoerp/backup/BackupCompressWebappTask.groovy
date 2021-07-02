package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class BackupCompressWebappTask {

    final static String WEBAPP_TAR_NAME = "wepapp"
    final static String DEFAULT_TOMCAT_FOLDER = "/var/lib/tomcat/webapps/"

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressWebappConfig") {
            doLast {

                def confProps = BackupUtils.loadConfigurationProperties(project)
                def etendoConf = BackupUtils.loadEtendoBackupConf(project)

                def contextName = confProps?.context_name ?: "undefined"
                def tomcatLocation = (etendoConf?.TOMCAT_PATH ?: DEFAULT_TOMCAT_FOLDER as String).concat(contextName)

                def tomcatLocFile = project.file(tomcatLocation)

                if (project.hasProperty("includeWebapp")) {
                    if (tomcatLocFile.exists()) {
                        log.logToFile(LogLevel.INFO, "Tomcat webapp folder: ${tomcatLocation} Will be compressed", project.findProperty("extFileToLog") as File)
                    } else {
                        log.logToFile(LogLevel.WARN, "Tomcat webapp folder: ${tomcatLocation} Not found. Skipping...", project.findProperty("extFileToLog") as File)
                    }
                }

                File tmpDir = BackupUtils.generateTmpDir(project)
                Task webappTar = project.tasks.named("backupCompressWebapp").get() as Tar

                webappTar.archiveFileName.set("${WEBAPP_TAR_NAME}.tar.gz")
                webappTar.destinationDirectory.set(tmpDir)
                webappTar.from(tomcatLocFile)
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
                if (!project.hasProperty("includeWebapp")) {
                    throw new StopExecutionException("Skipping webapp folder")
                }

                log.logToFile(LogLevel.INFO, "Compressing webapp folder", project.findProperty("extFileToLog") as File)
            }

            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing webapp' execution finalized.", project.findProperty("extFileToLog") as File)
            }
        }

    }

}
