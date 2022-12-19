package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.StopExecutionException

import com.etendoerp.backup.BackupModule as BM

class BackupDatabaseFixScriptTask {

    static load(Project project) {

        Logger log = Logger.getLogger(project)

        project.tasks.register("backupDatabaseDumpFixScriptConfig") {
            doLast {
                BackupUtils.loadEtendoBackupConf(project)

                def fixScript = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.EXEC_FIX_SCRIPT
                if (fixScript != "yes") {
                    throw new StopExecutionException("Skipping DB FIX SCRIPT")
                }

                def dbConf = BackupUtils.loadConfigurationProperties(project)

                Task dbFixScript = project.tasks.named("backupDatabaseDumpFixScript").get() as Exec
                def scriptFile = project.file(project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.SCRIPT_FILE)
                dbFixScript.environment("PGPASSWORD", dbConf.db_pass)
                dbFixScript.commandLine("psql","-h","${dbConf.db_host}","-p","${dbConf.db_port}","-U","${dbConf.db_login}","-d","${dbConf.db_name}","-a","-f","${scriptFile.absolutePath}")
            }
        }

        project.tasks.register("backupDatabaseDumpFixScript", Exec) {
            dependsOn "backupDatabaseDumpFixScriptConfig"

            standardOutput = new ByteArrayOutputStream()
            errorOutput = new ByteArrayOutputStream()
            ignoreExitValue true

            doFirst {
                def fixScript = project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.EXEC_FIX_SCRIPT
                if (fixScript != "yes") {
                    throw new StopExecutionException("Skipping DB FIX SCRIPT")
                }
                log.logToFile(LogLevel.INFO, "DB FIX SCRIPT is running", project.findProperty(BM.FILE_TO_LOG) as File)
            }

            doLast {
                def exitValue = executionResult.get().getExitValue()
                def output = (exitValue == 0) ? standardOutput.toString() : errorOutput.toString()

                if (exitValue == 1) {
                    throw new IllegalStateException(output)
                }

                // Save output in a log file
                // User must have permissions and the folder should exists
                def outputFile = project.file(project.findProperty(BM.ETENDO_BACKUP_PROPERTIES)?.OUTPUT_FILE)

                // Concat the stderr to the output (2>&1)
                outputFile.write(output.concat(errorOutput.toString()))

                log.logToFile(
                        LogLevel.INFO,
                        "'DB FIX SCRIPT' execution finalized. Exit Value: ${exitValue}",
                        project.findProperty(BM.FILE_TO_LOG) as File
                )
            }
        }

    }

}
