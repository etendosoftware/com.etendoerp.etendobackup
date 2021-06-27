package com.smf.backup

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class BackupModule {

    static Logger log
    static CommandLine commandLine
    static File logFile
    static Project project

    static void load (Project project) {

        def extension = project.extensions.create("backup", BackupPluginExtension)

        this.log = Logger.getLogger(project)
        this.commandLine = CommandLine.getCommandLine(project)
        this.project = project

        project.ext.logFile = this.logFile

        project.gradle.taskGraph.afterTask { Task task, TaskState state ->
            if (task.name.startsWith("backup")) {
                if (state.failure) {
                    Throwable throwable = state.failure
                    def failureMessage ="Error on $task "
                    this.log.logToFile(LogLevel.ERROR, failureMessage, this.logFile, throwable)
                }
            }
        }

        // Test task
        project.task("backupEtendoExtension") {
            doLast {
                String configPath = extension.configPath.get() ?: "undefined"
                println "Config path: ${configPath}"
                // Command line Test
                def (exit, out) = commandLine.run(false,"find", "...")
                log.logToFile(LogLevel.INFO, "Output ls: ${out.toString()}")
            }
        }

        project.task("etendoBackup") {
            doLast {
                println "Etendo backup is running..."
            }
        }
    }

}