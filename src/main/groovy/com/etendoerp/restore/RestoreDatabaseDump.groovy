package com.etendoerp.restore

import com.etendoerp.restore.verification.VerificationMessages
import org.gradle.api.Project

import com.etendoerp.conventions.ConventionNames as CN

class RestoreDatabaseDump {

    final static String DUMP_FOLDER_NAME = "db-dump"
    final static String DUMP_NAME = "db.dump"

    static void load(Project project) {

        project.tasks.register("restoreDatabaseDump") {
            doLast {
                CommandLine commandLine = CommandLine.getCommandLine(project)

                def gradleProps = RestoreUtils.loadGradleProperties(project)
                VerificationMessages.databaseVerifications(project, gradleProps)

                def dbName = gradleProps?.db_name
                def owner = gradleProps?.db_user
                def password = gradleProps?.db_pass

                if (!dbName) {
                    throw new IllegalArgumentException("Database name not provided")
                }

                if (!owner) {
                    throw new IllegalArgumentException("Database owner not provided")
                }

                def tmpDir = project.findProperty(RestoreModule.TMP_DIR)
                if (!tmpDir) {
                    throw new IllegalArgumentException("Tmp dir not found")
                }

                tmpDir = project.file(tmpDir)
                if (!tmpDir.exists()) {
                    throw new IllegalArgumentException("Tmp dir: ${tmpDir.absolutePath} does not exists.")
                }

                def dumpFile = project.file("${tmpDir.absolutePath}/${CN.DUMP_TAR_NAME}/${CN.DUMP_NAME}")

                if (!dumpFile.exists()) {
                    throw new IllegalArgumentException("Dump file: ${dumpFile.absolutePath} not found")
                }

                // Kill connections, procpid or pid
                def (exit, out) = commandLine.runSudo(true,"-u postgres psql -c \"select pg_terminate_backend(procpid) from pg_stat_activity where datname = '$dbName' \"")
                if (exit == 1) {
                    project.logger.info("Kill connections fail, trying other form")
                    (exit, out) = commandLine.runSudo(false,"-u postgres psql -c \"select pg_terminate_backend(pid) from pg_stat_activity where datname = '$dbName' \"")
                }

                // Delete the database
                project.logger.info("Deleting database")
                (exit, out) = commandLine.runSudo("-u postgres psql -c 'drop database $dbName'")
                project.logger.info("Deleting database output: $out")

                // Restore database
                project.logger.info("Restoring database")
                (exit, out) = commandLine.runSudo(false,"-u postgres psql -c 'ALTER ROLE $owner WITH SUPERUSER;'")
                project.logger.info("Alter role output: $out")

                (exit, out) = commandLine.runSudo(false,"-u postgres psql -c 'create database $dbName WITH ENCODING='UTF8' OWNER=$owner;'")
                project.logger.info("Create database output: $out")

                def env = ["PGPASSWORD":password]
                (exit, out) = commandLine.run(true, env, "pg_restore","-U","$owner","-h","localhost","-d",dbName as String,"-O",dumpFile.absolutePath)
                project.logger.info("PG RESTORE Output: $out")

                commandLine.runSudo(false,"-u postgres psql -c 'ALTER ROLE $owner WITH NOSUPERUSER;'")

                project.logger.info("restoreDecompressDatabase Finalized.")
            }
        }
    }

}