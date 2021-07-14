package com.etendoerp.restore.verification

import com.etendoerp.restore.CommandLine
import com.etendoerp.restore.RestoreDecompressAll
import com.etendoerp.restore.RestoreUtils
import org.gradle.api.Project
import com.etendoerp.restore.RestoreModule as RM

class VerificationMessages {

    final static int PHASES = 5

    static headerMessage(Project project, String title, int number) {
        def message = "*****************************************************************************\n" +
                      "*                           ETENDO RESTORE ${number}/$PHASES                 \n" +
                      "*            WARNING !! The restore will DELETE with undo option             \n" +
                      "*                 Make sure the next options are correct.                    \n" +
                      "*                         $title                                             \n" +
                      "* -------------------------------------------------------------------------- \n"
        return message
    }

    static userPermissionsVerification(Project project) {
        CommandLine commandLine = CommandLine.getCommandLine(project)
        def header = headerMessage(project, "USER VERIFICATION", 1)
        def (exit, output) = commandLine.run(false, "id","-u","-n")

        def currentUser = (output as String).replace("\n","")

        header += "* The restore needs to be run with a user with SUDO access \n"
        header += "* Current user: ${currentUser}                             \n"

        project.ext.setProperty(RM.CURRENT_USER, currentUser)

        println(header)

        def choiceContinue = "1"
        def choiceSetPassword = "2"

        def userChoice = ""

        while (userChoice != choiceContinue) {
            def options = [:]

            (exit, output) = commandLine.runSudo("true")
            def message = ""
            if (exit != 0) {
                message += "* The user has not SUDO access \n"
                options.put(choiceSetPassword,"Insert password")
            } else {
                message += "* The user has SUDO access     \n"
                options.put(choiceContinue, "Continue")
           }

            userChoice = VerificationHelper.antMenu(
                    project,
                    "Select an option",
                    options,
                    message
            )

            if (userChoice == choiceSetPassword) {
                def userInput = VerificationHelper.antUserInput(
                        project,
                        "Insert password:",
                        ""
                )
                CommandLine.setSudoPassword(userInput)
            }
        }
    }

    static preDecompressVerification(Project project) {

        def tmpDir = RestoreUtils.loadTmpDir(project) as File
        def backupLocation = RestoreUtils.loadBackupLocation(project) as File

        def header = headerMessage(project, "DECOMPRESSING BACKUP", 2)
        header += "* Backup location: ${backupLocation.absolutePath}    \n" +
                  "* Backup decompress location: ${tmpDir.absolutePath} \n"

        println(header)

        def projectDir = project.rootDir.absolutePath

        def defaultDestDir = projectDir

        def choiceContinue = "1"
        def choiceChangeDir = "2"
        def options = [
                (choiceContinue) :"Continue (Backup extract will start)",
                (choiceChangeDir) : "Change Sources destination directory"
        ]

        def userChoice = ""

        while (userChoice != choiceContinue) {
            // Confirm sources destination directory

            def destDirMessage = "* The Sources destination directory is set to: \n"
            destDirMessage +=    "* '${project.file(defaultDestDir).absolutePath}' "
            destDirMessage +=    "${(defaultDestDir == projectDir) ? "(Current project dir)":""}\n"

            if (!project.file(defaultDestDir).exists())  {
                destDirMessage += "* The destination directory does not exists       \n"
                destDirMessage += "* The destination will be created if you continue \n"
            } else {
                destDirMessage += "* The files inside the destination will be OVERWRITTEN or DELETED \n"
            }

            userChoice = VerificationHelper.antMenu(
                    project,
                    "Select an option",
                    options,
                    destDirMessage
            )

            if (userChoice == choiceChangeDir) {
                defaultDestDir = VerificationHelper.inputNewDir(project, defaultDestDir as String)
            }

        }

        project.ext.setProperty(RM.SOURCES_DESTINATION_DIR, defaultDestDir)
        def destDir = RestoreUtils.loadSourcesDestinationDir(project)
        project.logger.info("Sources destination directory: ${destDir.absolutePath}")

        return header
    }

    static attachmentsVerification(
            Project project,
            String sourcesLocation,
            String sourcesAttachLocation,
            Boolean hasExternalAttach,
            String attachLocationInsideSources
    ) {

        project.logger.info("sourcesLocation: $sourcesLocation")
        project.logger.info("sourcesAttachLocation: $sourcesAttachLocation")
        project.logger.info("hasExternalAttach: $hasExternalAttach")
        project.logger.info("attachLocationInsideSources: $attachLocationInsideSources")

        def destDir = RestoreUtils.loadSourcesDestinationDir(project) as File

        def header = headerMessage(project, "ATTACHMENTS VERIFICATION", 3)

        println(header)

        // Set the final attach location equal to the attach.path property
        project.ext.setProperty(RM.FINAL_ATTACH_LOCATION, sourcesAttachLocation)

        if (!hasExternalAttach && !attachLocationInsideSources) {
            def message = ""
            message += "* WARNING !! SOMETHING IS WRONG !                    \n"
            message += "* The backup has not external attachments and        \n"
            message += "* The attach path location is not inside the Sources \n"
            message += "* Sources attach location: $sourcesAttachLocation    \n"

            VerificationHelper.antMenu(
                    project,
                    "Select an option",
                    ["1":"Continue ($sourcesAttachLocation will be used as the location for attachments and MUST EXISTS.)"],
                    message
            )
            return
        }

        def externalAttachmentsPrio = true

        // The backups comes with external attachments (tar.gz) and
        // the attach.path property inside Openbravo.properties points inside the Sources (Link)
        // If the user continues, sources and external attachments are ignored
        if (hasExternalAttach && attachLocationInsideSources) {
            def message = ""
            message += "* WARNING !! SOMETHING IS WRONG ! \n"
            message += "* The backup comes with external attachments \n"
            message += "* but the attach.path property points to $attachLocationInsideSources inside the Sources \n"

            def choiceContinue       = "1"
            def choiceExternalAttach = "2"
            def choiceSourcesAttach  = "3"

            def options = [
                    (choiceContinue) : "Continue (Sources and external attachments will not be copied)",
                    (choiceExternalAttach) : "Preserve external attachments",
                    (choiceSourcesAttach)  : "Preserve Sources attachments"
            ]

            def res = VerificationHelper.antMenu(
                    project,
                    "Select an option",
                    options,
                    message
            )

            if (res == choiceContinue) {
                return
            }

            if (res == choiceSourcesAttach) {
                externalAttachmentsPrio = false
            }

        }

        def copyAttachOpt = "1"
        def ignoreAttachOpt = "2"
        def options = [(copyAttachOpt): "Copy attachments", (ignoreAttachOpt) : "Ignore copy of attachments"]

        // External attachments verifications
        if (hasExternalAttach && externalAttachmentsPrio) {
            def changeDestDir = "3"
            options.put((changeDestDir), "Change destination directory")

            def tmpAttachLoc = sourcesAttachLocation
            def userChoice = ""
            while (userChoice != copyAttachOpt && userChoice != ignoreAttachOpt) {

                def externalAttachMessage = ""
                externalAttachMessage += "* The backup has external attachments                                                    \n"
                externalAttachMessage += "* The external attachments will be copied in: ${project.file(tmpAttachLoc).absolutePath} \n"

                // Check if the location exists
                if (!project.file(tmpAttachLoc).exists()) {
                    externalAttachMessage += "* The destination does not exists (Will be created if copy is selected) \n"
                } else {
                    externalAttachMessage += "* WARNING The files in the destination will be OVERWRITTEN or DELETED   \n"
                }

                userChoice = VerificationHelper.antMenu(
                        project,
                        "Select an option",
                        options,
                        externalAttachMessage
                )

                if (userChoice == changeDestDir) {
                    tmpAttachLoc = VerificationHelper.inputNewDir(project, tmpAttachLoc.toString())
                }
            }

            if (userChoice == copyAttachOpt) {
                project.ext.setProperty(RM.FINAL_ATTACH_LOCATION, tmpAttachLoc)
                project.ext.setProperty(RM.COPY_EXTERNALS_ATTACHMENTS, true)
                def externalAttachDir = RestoreUtils.loadExternalAttachmentsDir(project)
                project.logger.info("External attachments destination directory: ${externalAttachDir}")
            }

            project.logger.info("Copy Externals Attachments: ${project.findProperty(RM.COPY_EXTERNALS_ATTACHMENTS)}")
            return
        }

        // Source attachments verification
        if (attachLocationInsideSources) {
            def destAttachLoc = "${destDir.absolutePath}$attachLocationInsideSources"

            project.ext.setProperty(RM.FINAL_ATTACH_LOCATION, destAttachLoc)

            def attachInsideSourceMessage = ""
            attachInsideSourceMessage += "* The backup attachments are located inside the Sources                    \n"
            attachInsideSourceMessage += "* Original sources directory '${attachLocationInsideSources}'              \n"
            attachInsideSourceMessage += "* Destination directory '${destAttachLoc}'                                 \n"
            attachInsideSourceMessage += "* WARNING The attachments in the destination directory will be overwritten \n"

            def res = VerificationHelper.antMenu(
                    project,
                    "Select an option",
                    options,
                    attachInsideSourceMessage
            )

            // 0: Exit restore - 1: Copy - 2: Ignore
            project.ext.setProperty(RM.COPY_SOURCES_ATTACHMENTS, (res == copyAttachOpt))
            project.logger.info("restoreCopySourcesAttachments: ${project.findProperty(RM.COPY_SOURCES_ATTACHMENTS)}")
        }
    }

    static propertiesVerifications(Project project, String sourcesLocationProps) {
        def header = headerMessage(project, "PROPERTIES VERIFICATION", 4)
        println(header)

        def propertiesFile = "gradle.properties"
        def selectedPropertiesToUse = ""

        // Options to show when the user selects the properties to use
        def choiceCurrentProjectProps = "1"
        def choiceOriginalSourcesProps = "2"
        def optionsProperties = [
                (choiceCurrentProjectProps) : "Current project properties",
                (choiceOriginalSourcesProps): "Original Sources properties"
        ]

        // Options to show when the user confirm or changes the selected properties
        def choiceConfirm = "1"
        def choiceChangeProps = "2"

        def userChoice = ""

        while (userChoice != choiceConfirm) {

            selectedPropertiesToUse = VerificationHelper.antMenu(
                    project,
                    "Select which properties will be used (gradle.properties) \n" +
                    "* The properties will change the database options ",
                    optionsProperties
            )

            def defaultPropsMsg = optionsProperties.getOrDefault(selectedPropertiesToUse,"Undefined")
            propertiesFile = "gradle.properties"

            if (selectedPropertiesToUse == choiceOriginalSourcesProps) {
                propertiesFile = sourcesLocationProps
            }

            def propMessage = "* ${defaultPropsMsg} \n"

            def propFile = project.file(propertiesFile)

            if (!propFile.exists()) {
                propMessage += "* WARNING The properties file: ${propFile.absolutePath} does not exists \n"
            } else {
                def gradleProps = new Properties()
                propFile.withInputStream { gradleProps.load(it) }
                gradleProps.each {
                    propMessage += "* ${it.key}: ${it.value} \n"
                }
            }

            def optionsToConfirmOrChange = [
                    (choiceConfirm): "Confirm properties (${defaultPropsMsg})",
                    (choiceChangeProps): "Change Properties"
            ]

            userChoice = VerificationHelper.antMenu(
                    project,
                    "\n* Select an option",
                    optionsToConfirmOrChange,
                    propMessage
            )
        }

        // Update properties
        project.ext.setProperty(RM.KEEP_ORIGINAL_PROPERTIES, (selectedPropertiesToUse == choiceOriginalSourcesProps))
        RestoreUtils.loadGradleProperties(project, propertiesFile)
    }

    static databaseVerifications(Project project, Map props) {
        def keepOriginalProperties = project.findProperty(RM.KEEP_ORIGINAL_PROPERTIES)
        def header = headerMessage(project, "DATABASE PROPERTIES", 5)

        if (keepOriginalProperties) {
            header += "* Using original properties from the backup sources \n"
        } else {
            header += "* Using the properties from the current project \n"
        }
        header += "* \n"
        header += "* Database name: ${props?.db_name} (WILL BE OVERWRITE IF EXISTS)\n"
        header += "* Database port: ${props?.db_port}                              \n"
        header += "* Database owner: ${props?.db_user}                             \n"
        header += "* Database password: ${props?.db_pass}                          \n"
        header += "* Database system user: ${props?.db_sysuser}                    \n"
        header += "* Database system password: ${props?.db_syspass}                \n"

        println(header)

        VerificationHelper.antMenu(
                project,
                "Select an option",
                ["1":"Continue (The restore will start)"]
        )

    }

    static checksumsVerification(Project project, String output) {
        def message = "************************************************************** \n"
        message    += "* Error checking sha1 checksums of the files inside the backup  \n"
        message    += "* Output: $output \n"
        VerificationHelper.antMenu(
                project,
                "Select an option",
                ["1":"Continue (WARNING: Files are corrupted)"],
                message
        )
    }

    static stopTomcatVerification(Project project, String output) {
        def message = "***************************************\n"
        message    += "* Error stopping TOMCAT                \n"
        message    += "* Output: $output                      \n"
        message    += "* Stop TOMCAT manually before continue \n"
        VerificationHelper.antMenu(
                project,
                "Select an option",
                ["1":"Continue"],
                message
        )
    }
}