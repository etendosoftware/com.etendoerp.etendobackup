package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class CommandLine {

    private static CommandLine commandLine
    Project project
    Logger log

    private CommandLine(Project project) {
        this.project = project
        this.log = Logger.getLogger(project)
    }

    static CommandLine getCommandLine(Project project) {
        if (commandLine == null) {
            commandLine = new CommandLine(project)
        }
        return  commandLine
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
            log.logToFile(LogLevel.WARN, errMsg, this.project.findProperty("extFileTolog") as File)
        }
        return [result.getExitValue(), output]
    }

}
