package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

import com.etendoerp.conventions.ConventionNames as CN
import com.etendoerp.backup.BackupModule as BM

class BackupCompressWebappTask {

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressWebappConfig") {
            doLast {

                if (!project.hasProperty("includeWebapp")) {
                    throw new StopExecutionException("Skipping webapp folder")
                }

                def confProps = BackupUtils.loadConfigurationProperties(project)
                def etendoConf = BackupUtils.loadEtendoBackupConf(project)

                def contextName = confProps?.context_name ?: "undefined"
                def tomcatLocation = (etendoConf?.TOMCAT_PATH ?: CN.DEFAULT_TOMCAT_FOLDER as String).concat(contextName as String)

                def tomcatLocFile = project.file(tomcatLocation)

                if (tomcatLocFile.exists()) {
                    log.logToFile(LogLevel.INFO, "Tomcat webapp folder: ${tomcatLocation} Will be compressed", project.findProperty(BM.FILE_TO_LOG) as File)
                } else {
                    log.logToFile(LogLevel.WARN, "Tomcat webapp folder: ${tomcatLocation} Not found. Skipping...", project.findProperty(BM.FILE_TO_LOG) as File)
                    throw new StopExecutionException("Tomcat webapp folder: ${tomcatLocation} Not found. Skipping...")
                }
                
                File tmpDir = BackupUtils.generateTmpDir(project)
                Task webappTar = project.tasks.named("backupCompressWebapp").get() as Tar

                webappTar.archiveFileName.set("${CN.WEBAPP_TAR_NAME}${CN.EXTENSION}")
                webappTar.destinationDirectory.set(tmpDir)
                webappTar.from(tomcatLocFile)
            }
        }

        project.tasks.register("backupCompressWebapp", Tar) {
            try {
                log.logToFile(LogLevel.INFO, "Starting backupCompressWebapp Configuration", project.findProperty(BM.FILE_TO_LOG) as File)
                mustRunAfter = ["backupConfig"]
                dependsOn "backupCompressWebappConfig"
                compression = Compression.GZIP
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupCompressWebapp Configuration", project.findProperty(BM.FILE_TO_LOG) as File, e)
                throw e
            }

            doFirst {
                if (!project.hasProperty("includeWebapp")) {
                    throw new StopExecutionException("Skipping webapp folder")
                }

                log.logToFile(LogLevel.INFO, "Compressing webapp folder", project.findProperty(BM.FILE_TO_LOG) as File)
            }

            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing webapp' execution finalized.", project.findProperty(BM.FILE_TO_LOG) as File)
            }
        }

    }

}
