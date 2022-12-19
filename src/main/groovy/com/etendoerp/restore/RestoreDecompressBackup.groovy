package com.etendoerp.restore

import com.etendoerp.restore.verification.VerificationMessages
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy

class RestoreDecompressBackup {

    static void load(Project project) {

        project.tasks.register("restoreDecompressBackupConfig") {
            mustRunAfter = ["restoreConfig"]
            doLast {
                File tmpDir = RestoreUtils.loadTmpDir(project)

                def backupFile = RestoreUtils.loadBackupLocation(project)

                Task decompressBackup = project.tasks.named("restoreDecompressBackup").get() as Copy
                decompressBackup.from(project.tarTree(backupFile))
                decompressBackup.into(tmpDir)
            }
        }


        project.tasks.register("restoreDecompressBackup", Copy) {
            dependsOn("restoreDecompressBackupConfig")
            mustRunAfter = ["restoreConfig"]
            doLast {
                CommandLine commandLine = CommandLine.getCommandLine(project)

                def tmpDir = RestoreUtils.loadTmpDir(project)
                // Check checksums
                def (exit,output) = commandLine.run("sh","-c",""" cd ${tmpDir.absolutePath} && sha1sum -c sha1""")
                if (exit != 0) {
                    VerificationMessages.checksumsVerification(project, output as String)
                }

                // Stop tomcat
                (exit,output) = commandLine.run("sh","-c","""/etc/init.d/tomcat stop""")
                if (exit != 0) {
                    VerificationMessages.stopTomcatVerification(project, output as String)
                }

                project.logger.info("restoreDecompressBackup Finalized")
            }
        }

    }
}