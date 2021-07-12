package com.etendoerp.restore.verification


import org.gradle.api.Project

class VerificationHelper {

    // Directories that never should be used
    static List<String> forbiddenDirs = [
            "/"
    ]

    static antMenu(Project project, String titleOptions, Map options, String preMessage = null, String antProp = null) {

        def customAntProp = antProp ?: generateRandomAntProp()

        def message = preMessage ?: ""

        def defaultExitValue = "0"
        message += "* ${titleOptions}  \n"
        message += "* [$defaultExitValue] - Exit restore \n"

        def optionsToChoose = [defaultExitValue]

        options.each {
            def key = it.key as String
            message += "* [${key}] - ${it.value} \n"
            optionsToChoose.add(key)
        }
        message += "* Insert one of the following options:"

        def validArguments = optionsToChoose.join(",")

        project.ant.input(message: message, validargs: validArguments, addproperty: customAntProp)
        def selectedOption = project.ant[customAntProp]
        println("* Option selected: ${selectedOption} - ${options.getOrDefault(selectedOption,"")} \n")

        if (selectedOption == defaultExitValue) {
            throw new IllegalArgumentException("The restore will not continue.")
        }

        return  selectedOption
    }

    static antUserInput(Project project, String message, String defaultValue, antProp = null) {
        def customAntProp = antProp ?: generateRandomAntProp()

        project.ant.input(message: message,addproperty: customAntProp, defaultValue: defaultValue)

        def userInput = project.ant[customAntProp]
        println("* User input: ${userInput}")
        return userInput
    }

    static generateRandomAntProp() {
        return "randomAntProp${UUID.randomUUID().toString()}"
    }

    static inputNewDir(Project project, String defaultDestDir) {
        def selectedDir = antUserInput(
                project,
                "Insert the new directory:",
                defaultDestDir.toString()
        )

        if (forbiddenDirs.contains(selectedDir)) {
            println("*** WARNING !!! The destination directory inserted is FORBIDDEN. \n")
            selectedDir = defaultDestDir
        }

        return selectedDir
    }

}