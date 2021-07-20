package com.etendoerp.restore

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Sync
import com.etendoerp.restore.RestoreModule as RM

import com.etendoerp.conventions.ConventionNames as CN

class RestoreSources {

    // Files that the 'sources' backup folder can contain
    static List<String> originalSourceFiles = [
         'gradle.properties',
         'settings.gradle',
         'buildSrc',
         'gradlew.bat',
         'gradlew',
         'gradle/',
         '.gradle',
         '.idea'
    ]

    // Files to prevent delete
    // User might add files to the list to prevent delete in the destination directory
    static List<String> filesToKeep = [
        'gradle.properties',
        'settings.gradle',
        'build.gradle',
        'buildSrc',
        'buildSrc/**',
        'gradlew.bat',
        'gradlew',
        'gradle/',
        '.gradle',
        '.gradle/**',
        '.idea/**'
    ]

    static List<String> filesToOverwrite = [
        'gradle.properties',
        'settings.gradle',
    ]

    /**
     * Generates a list of files to exclude copy from 'sources' to 'destDir'
     * if they already exists in the destination directory
     * to prevent overwriting them.
     *
     * A user might want this files to be copied if the destination directory
     * is different from the root project.
     *
     * @param destDir
     * @return List of files to exclude from copy
     */
    static List<String> filesToExcludeFromCopy(Project project, String destDir, List<String> files) {
        List<String> excludedFiles = []
        for (String file : files) {
            def absFile = project.file( "$destDir/$file")
            if (absFile.exists()) {
                project.logger.info("File $absFile already exists in the destination directory, excluding from copy.")
                excludedFiles.add(file)
            }
        }
        return excludedFiles
    }

    static void load(Project project) {

        project.tasks.register("restoreSourcesConfig") {
            doLast {

                def tmpDir = RestoreUtils.loadTmpDir(project)

                def sourcesFile = project.file("${tmpDir.absolutePath}/${CN.SOURCES_TAR_NAME}")
                if (!sourcesFile.exists()) {
                    throw new IllegalArgumentException("The Sources folder ${sourcesFile.absolutePath} does not exists")
                }

                def keepOriginalProperties = project.findProperty(RM.KEEP_ORIGINAL_PROPERTIES)
                def destDir = RestoreUtils.loadSourcesDestinationDir(project) as File

                def excludedFiles = originalSourceFiles

                // If the user select to keep the original properties
                if (keepOriginalProperties) {
                    project.logger.info("The next files: $filesToOverwrite will be overwriten in the destination directory.")
                    excludedFiles.removeAll(filesToOverwrite)
                }

                excludedFiles = filesToExcludeFromCopy(project, destDir.absolutePath, excludedFiles)

                Task restSrc = project.tasks.named("restoreSources").get() as Sync

                def copySourcesAttach = project.findProperty(RM.COPY_SOURCES_ATTACHMENTS)
                def attachLocInsideSources = project.findProperty(RM.ATTACH_LOCATION_IN_SOURCES) ?: ""

                restSrc.from(sourcesFile) {
                    // Prevents overwriting
                    exclude(excludedFiles)
                    // Exclude 'attachments' from copy
                    if (!copySourcesAttach) {
                        project.logger.info("The attachments inside the Sources '${CN.DEFAULT_ATTACH_FOLDER}'" +
                                            "${attachLocInsideSources ? " - '${attachLocInsideSources}'" : ""} will not be copied.")
                        exclude CN.DEFAULT_ATTACH_FOLDER
                        exclude attachLocInsideSources
                    }
                }

                restSrc.into(destDir)
                attachLocInsideSources = attachLocInsideSources ? "${attachLocInsideSources}/**" : ""

                restSrc.preserve {
                    // Prevents deleting the following files if already exists
                    include(filesToKeep)
                    // Prevents deleting 'attachments'
                    if (!copySourcesAttach) {
                        include "${CN.DEFAULT_ATTACH_FOLDER}/**"
                        include "${attachLocInsideSources}"
                    }
                }
            }
        }

        project.tasks.register("restoreSources", Sync) {
            dependsOn("restoreSourcesConfig")
            doLast {
                def keepOriginalProperties = project.findProperty(RM.KEEP_ORIGINAL_PROPERTIES)

                def destDir = RestoreUtils.loadSourcesDestinationDir(project) as File
                def gradleProps = "${destDir.absolutePath}/gradle.properties"
                def openbravoProps = "${destDir.absolutePath}/${CN.DEFAULT_CONFIG_PROPERTIES_LOCATION}"

                if (!keepOriginalProperties) {
                    // Copy gradle.properties
                    project.copy {
                        from project.file("gradle.properties")
                        into destDir
                    }

                    // Update the Openbravo.properties file in the destination directory
                    RestoreUtils.updatePropertiesFile(project, gradleProps, openbravoProps)
                }

                // Update attach.path and sources.path with the actual locations

                // Set the attach.path original to the Openbravo.properties
                def finalAttachmentsLocation = project.findProperty(RM.FINAL_ATTACH_LOCATION)

                if (!finalAttachmentsLocation) {
                    project.logger.warn("WARNING - The final attachments location is NOT SET. Should be changed manually.")
                }

                project.ant.propertyfile(file: openbravoProps) {
                    entry(key: 'attach.path', value: finalAttachmentsLocation)
                    entry(key: 'source.path', value: "${destDir.absolutePath}")
                }

                project.logger.info("restoreSources Finalized.")
            }
        }

    }
}