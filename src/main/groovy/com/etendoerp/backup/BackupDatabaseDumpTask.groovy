package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Exec

import com.etendoerp.conventions.ConventionNames as CN

class BackupDatabaseDumpTask {

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupDatabaseDumpExecConfig") {
            doLast {
                def dbConf = BackupUtils.loadConfigurationProperties(project)
                def tmpDir = BackupUtils.generateTmpDir(project)
                def dumpName = "${tmpDir.absolutePath}/${CN.DUMP_NAME}"
                Task dbDump = project.tasks.named("backupDatabaseDumpExec").get() as Exec
                dbDump.environment("PGPASSWORD", dbConf.db_pass)
                dbDump.commandLine("pg_dump","-p","${dbConf.db_port}","-h","${dbConf.db_host}","-U","${dbConf.db_login}","-Fc","-b","-f","${dumpName}","${dbConf.db_name}")
            }
        }

        project.tasks.register("backupDatabaseDumpExec", Exec) {
            try {
                log.logToFile(LogLevel.INFO, "Starting backupDatabaseDumpExec Configuration", project.findProperty("extFileToLog") as File)

                mustRunAfter = ["backupConfig", "backupCompressDatabaseDumpConfig"]
                dependsOn ("backupDatabaseDumpExecConfig", "backupDatabaseDumpFixScript")
                
                errorOutput = new ByteArrayOutputStream()
                ignoreExitValue true
            } catch (Exception e) {
                log.logToFile(LogLevel.ERROR, "Error on backupDatabaseDumpExec Configuration", project.findProperty("extFileToLog") as File, e)
                throw e
            }

            doFirst {
                log.logToFile(LogLevel.INFO, "Creating database dump", project.findProperty("extFileToLog") as File)
            }

            doLast {
                // Exit value 0 = Success
                // Exit value 1 = Error
                def exitValue = executionResult.get().getExitValue()

                if (exitValue == 1) {
                    throw new IllegalArgumentException(errorOutput.toString())
                }
                log.logToFile(
                        LogLevel.INFO,
                        "'Creating database dump' execution finalized. Exit Value: ${exitValue}",
                        project.findProperty("extFileToLog") as File
                )
            }
        }

    }

}