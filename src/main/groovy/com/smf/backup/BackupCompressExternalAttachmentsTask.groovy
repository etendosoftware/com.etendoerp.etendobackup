package com.smf.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class BackupCompressExternalAttachmentsTask {

    final static String ATTACH_TAR_NAME = "attach"

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressExternalAttachmentsConfig") {
            doLast {
                File tmpDir = BackupUtils.generateTmpDir(project)
                def conf = BackupUtils.loadConfigurationProperties(project)
                Task extAttachTar = project.tasks.named("backupCompressExternalAttachments").get() as Tar
                extAttachTar.destinationDirectory.set(tmpDir)
                extAttachTar.archiveFileName.set("${ATTACH_TAR_NAME}.tar.gz")
                extAttachTar.from(project.file(conf.attach_path))
            }
        }

        project.tasks.register("backupCompressExternalAttachments", Tar) {
            try {
                mustRunAfter = ["backupConfig"]
                log.logToFile(LogLevel.INFO, "Starting backupCompressExternalAttachments Configuration", project.findProperty("extFileToLog") as File)
                dependsOn "backupCompressExternalAttachmentsConfig"
                compression = Compression.GZIP
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupCompressExternalAttachments Configuration", project.findProperty("extFileToLog") as File, e)
                throw e
            }

            doFirst {
                def attachPath = project.findProperty("confProperties")?.attach_path
                def attachCopy = project.findProperty("etendoConf")?.ATTACH_COPY
                if (!(attachCopy == "yes" && attachPath != "${project.rootDir.absolutePath}/attachments")) {
                    throw new StopExecutionException("Skip external attachments")
                }
                log.logToFile(LogLevel.INFO, "Compressing external attachments", project.findProperty("extFileToLog") as File)
            }

            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing external attachments' execution finalized.", project.findProperty("extFileToLog") as File)
            }
        }

    }

}