package com.smf.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class BackupCompressSourcesTask {

    static load(Project project) {
        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressSourcesTarConfig") {
            doLast {
                Task sourcesTar = project.tasks.named("backupCompressSourcesTar").get() as Tar

                File tmpDir = BackupUtils.generateTmpDir(project)
                sourcesTar.archiveFileName.set("sources.tar.gz")
                sourcesTar.destinationDirectory.set(tmpDir)
                sourcesTar.from(project.rootProject.rootDir, {
                    def attachInBkp = project.findProperty("etendoConf")?.ATTACH_IN_BKP
                    def attchMsg = "with attachments"
                    if (attachInBkp && attachInBkp != "yes") {
                        attchMsg = "without attachments"
                        exclude "attachments"
                    }
                    log.logToFile(LogLevel.INFO, "Sources ${project.rootProject.rootDir} will be compressed ${attchMsg}", project.findProperty("extFileToLog") as File)
                })
            }
        }

        project.tasks.register("backupCompressSourcesTar", Tar) {
            try {
                mustRunAfter = ["backupConfig"]
                dependsOn "backupCompressSourcesTarConfig"
                compression = Compression.GZIP
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupCompressSourcesTar Configuration", project.findProperty("extFileToLog") as File, e)
                throw e
            }
            doFirst {
                log.logToFile(LogLevel.INFO, "Compressing sources", project.findProperty("extFileToLog") as File)
            }
            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing sources' execution finalized.", project.findProperty("extFileToLog") as File)
            }
        }

    }

}