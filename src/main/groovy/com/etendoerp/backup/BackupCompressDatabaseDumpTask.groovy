package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

import com.etendoerp.conventions.ConventionNames as CN

class BackupCompressDatabaseDumpTask {

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupCompressDatabaseDumpConfig") {
            doLast {
                File tmpDir = BackupUtils.generateTmpDir(project)
                Task databaseTar = project.tasks.named("backupCompressDatabaseDump").get() as Tar

                databaseTar.archiveFileName.set("${CN.DUMP_TAR_NAME}${CN.EXTENSION}")
                databaseTar.destinationDirectory.set(tmpDir)
                databaseTar.from("${tmpDir.absolutePath}/${CN.DUMP_NAME}")
            }
        }

        project.tasks.register("backupDeleteDatabaseDump") {
            doLast {
                if (project.ext.has("tmpBackupDir") && project.ext.get("tmpBackupDir") != null) {
                    def dumpName = CN.DUMP_NAME
                    log.logToFile(LogLevel.INFO, "Deleting ${dumpName}", project.findProperty("extFileToLog") as File)
                    project.delete("${(project.ext.get("tmpBackupDir") as File).absolutePath}/${dumpName}")
                }
            }
        }

        project.tasks.register("backupCompressDatabaseDump", Tar) {
            try {
                log.logToFile(LogLevel.INFO, "Starting backupCompressDatabaseDump Configuration", project.findProperty("extFileToLog") as File)
                mustRunAfter = ["backupConfig"]
                dependsOn ("backupCompressDatabaseDumpConfig", "backupDatabaseDumpExec")
                finalizedBy project.tasks.named("backupDeleteDatabaseDump")
                compression = Compression.GZIP
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupCompressDatabaseDump Configuration", project.findProperty("extFileToLog") as File, e)
                throw e
            }

            doFirst {
                log.logToFile(LogLevel.INFO, "Compressing database dump", project.findProperty("extFileToLog") as File)
            }

            doLast {
                log.logToFile(LogLevel.INFO, "'Compressing database dump' execution finalized.", project.findProperty("extFileToLog") as File)
            }
        }

    }

}
