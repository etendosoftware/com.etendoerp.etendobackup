package com.etendoerp.restore


import com.etendoerp.restore.verification.VerificationMessages
import groovy.io.FileType
import org.gradle.api.Project

import com.etendoerp.conventions.ConventionNames as CN

class RestoreDecompressAll {

    final static List<String> REQUIRED_FILES = [
            CN.SOURCES_TAR_NAME,
            CN.DUMP_TAR_NAME
    ]

    static decompressFile(Project project, String fromFile, String intoDir) {
        project.copy {
            from project.tarTree(fromFile)
            into project.file(intoDir)
        }
    }

    static void load(Project project) {

        project.tasks.register("restoreDecompressAll") {
            dependsOn("restoreDecompressBackup")
            doLast {

                def tmpDir = RestoreUtils.loadTmpDir(project)

                def files = []
                // Verify files
                project.file(tmpDir.absolutePath).traverse(maxDepth: 0, type: FileType.FILES) {
                    if (it.absolutePath.endsWith(CN.EXTENSION)) {
                        files.add(it.name - CN.EXTENSION)
                    }
                }

                REQUIRED_FILES.each {
                    if (!files.contains(it)) {
                        throw new IllegalArgumentException("The file: $it is required to complete the restore.")
                    }
                }
                project.ext.setProperty(RestoreModule.HAS_EXTERNAL_ATTACHMENTS, files.contains(CN.ATTACH_TAR_NAME))

                // SOURCES VERIFICATION
                // Decompress sources
                def sourcesLocation = "${tmpDir.absolutePath}/${CN.SOURCES_TAR_NAME}"
                decompressFile(
                        project,
                        sourcesLocation + CN.EXTENSION,
                        sourcesLocation
                )
                files.remove(CN.SOURCES_TAR_NAME)
                sourcesVerification(project, sourcesLocation)

                // Remove 'attach' from files to prevent decompress them
                if (!project.findProperty(RestoreModule.COPY_EXTERNALS_ATTACHMENTS)) {
                    files.remove(CN.ATTACH_TAR_NAME)
                }

                files.each {
                    decompressFile(
                            project,
                            "${tmpDir.absolutePath}/$it${CN.EXTENSION}",
                            "${tmpDir.absolutePath}/$it"
                    )
                }

                project.logger.info("restoreDecompressAll Finalized.")
            }
        }
    }

    static sourcesVerification(Project project, String sourcesLocation) {
        // Load config/Openbravo.properties
        def openbravoProps = new Properties()
        project.file("${sourcesLocation}/${CN.DEFAULT_CONFIG_PROPERTIES_LOCATION}").withInputStream { openbravoProps.load(it) }
        def sourcesAttachLocation = openbravoProps.getProperty("attach.path") ?: "/undefined"
        def originalSourcesDirectory = openbravoProps.getProperty("source.path", "")

        project.ext.setProperty(RestoreModule.SOURCES_ATTACH_LOCATION, sourcesAttachLocation)

        def attachLocationInsideSources = ""

        // The attachments are in the Source folder
        if (sourcesAttachLocation && sourcesAttachLocation.startsWith(originalSourcesDirectory)) {
            attachLocationInsideSources = sourcesAttachLocation.replace(originalSourcesDirectory, "")
            project.ext.setProperty(RestoreModule.ATTACH_LOCATION_IN_SOURCES, attachLocationInsideSources)
        }

        def hasExternalAttachments = project.findProperty(RestoreModule.HAS_EXTERNAL_ATTACHMENTS) ?: false

        // Attachments verifications
        VerificationMessages.attachmentsVerification(
                project,
                sourcesLocation,
                sourcesAttachLocation,
                hasExternalAttachments as Boolean,
                attachLocationInsideSources
        )

        project.logger.info("Final attachment path: ${project.findProperty(RestoreModule.FINAL_ATTACH_LOCATION)}")

        // Properties verifications
        def sourcesPropertiesLoc = "${sourcesLocation}/gradle.properties"
        VerificationMessages.propertiesVerifications(project, sourcesPropertiesLoc)
        project.logger.info("Keep original properties: ${project.findProperty(RestoreModule.KEEP_ORIGINAL_PROPERTIES)}")
    }
}