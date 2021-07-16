package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

import com.etendoerp.backup.BackupModule as BM

class CommandLine {

    Project project
    static String sudoPassword = ""
    Logger log

    private CommandLine(Project project) {
        this.project = project
        this.log = Logger.getLogger(project)
    }

    static CommandLine getCommandLine(Project project) {
        return new CommandLine(project)
    }

    def run(Boolean ignoreStderr = true, String... commands) {
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        def result = this.project.exec {
            commandLine(commands)
            standardOutput = stdout
            errorOutput = stderr
            ignoreExitValue true
        }

        def output = stdout.toString()

        if (result.getExitValue() == 1) {
            output = stderr.toString()
            def errMsg = "Exit value 1 for command: ${commands.join(" ")}. ${output}"
            if (!ignoreStderr) {
                throw new IllegalStateException(errMsg)
            }
            log.logToFile(LogLevel.WARN, errMsg, this.project.findProperty(BM.FILE_TO_LOG) as File)
        }
        return [result.getExitValue(), output]
    }

    def runSudo(Boolean ignoreStderr = true,Map env = [:], String command) {
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        def password = sudoPassword

        def result = this.project.exec {
            environment(env)
            environment "HISTIGNORE", "*sudo -S*"
            executable "sh"
            args "-c", "echo $password | sudo -S ${command}"
            standardOutput = stdout
            errorOutput = stderr
            ignoreExitValue true
        }

        def output = stdout.toString()

        if (result.getExitValue() == 1) {
            output = stderr.toString()
            def errMsg = "Exit value 1 for command: $command. ${output}"
            if (!ignoreStderr) {
                throw new IllegalStateException(errMsg)
            }
            log.logToFile(LogLevel.WARN, errMsg, this.project.findProperty(BM.FILE_TO_LOG) as File)
        }
        return [result.getExitValue(), output]
    }

}
