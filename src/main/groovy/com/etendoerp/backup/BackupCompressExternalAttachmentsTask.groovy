package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

import com.etendoerp.conventions.ConventionNames as CN
import com.etendoerp.backup.BackupModule as BM

class BackupCompressExternalAttachmentsTask {

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressExternalAttachmentsConfig") {
            doLast {
                File tmpDir = BackupUtils.generateTmpDir(project)
                def conf = BackupUtils.loadConfigurationProperties(project)
                Task extAttachTar = project.tasks.named("backupCompressExternalAttachments").get() as Tar
                extAttachTar.destinationDirectory.set(tmpDir)
                extAttachTar.archiveFileName.set("${CN.ATTACH_TAR_NAME}${CN.EXTENSION}")
                extAttachTar.from(project.file(conf.attach_path))
            }
        }

        project.tasks.register("backupCompressExternalAttachments", Tar) {
            try {
                mustRunAfter = ["backupConfig"]
                log.logToFile(LogLevel.INFO, "Starting backupCompressExternalAttachments Configuration", project.findProperty(BM.FILE_TO_LOG) as File)
                dependsOn "backupCompressExternalAttachmentsConfig"
                compression = Compression.GZIP
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupCompressExternalAttachments Configuration", project.findProperty(BM.FILE_TO_LOG) as File, e)
                throw e
            }

            doFirst {
                def attachPath = project.findProperty(BM.CONFIG_PROPERTIES)?.attach_path
                def attachCopy = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.ATTACH_COPY
                if (!(attachCopy == "yes" && attachPath != "${project.rootDir.absolutePath}${CN.DEFAULT_ATTACH_FOLDER}")) {
                    throw new StopExecutionException("Skip external attachments")
                }
                log.logToFile(LogLevel.INFO, "Compressing external attachments", project.findProperty(BM.FILE_TO_LOG) as File)
            }

            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing external attachments' execution finalized.", project.findProperty(BM.FILE_TO_LOG) as File)
            }
        }

    }

}