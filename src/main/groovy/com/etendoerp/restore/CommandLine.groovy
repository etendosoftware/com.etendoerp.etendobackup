package com.etendoerp.restore


import org.gradle.api.Project

class CommandLine {

    private static CommandLine commandLine
    private static String sudoPassword = ""
    Project project

    private CommandLine(Project project) {
        this.project = project
    }

    static CommandLine getCommandLine(Project project) {
        if (commandLine == null) {
            commandLine = new CommandLine(project)
        }
        return  commandLine
    }

    static void setSudoPassword(String password) {
        sudoPassword = password
    }

    def run(Boolean ignoreStderr = true, Map env = [:], String... commands) {
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        def result = this.project.exec {
            environment(env)
            commandLine(commands)
            standardOutput = stdout
            errorOutput = stderr
            ignoreExitValue true
        }

        def output = stdout.toString()
        def exitValue = result.getExitValue()

        project.logger.info("Command: ${commands.join(" ")}")
        project.logger.info("Exit: ${exitValue}")
        project.logger.info("stdout: ${stdout.toString()}")
        project.logger.info("stderr: ${stderr.toString()}")

        if (exitValue != 0) {
            output = stderr.toString()
            def errMsg = "Exit value '${exitValue}' for command: ${commands.join(" ")}. ${output}"
            if (!ignoreStderr) {
                throw new IllegalStateException(errMsg)
            }
        }
        return [exitValue, output]
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
        }
        return [result.getExitValue(), output]
    }

}
