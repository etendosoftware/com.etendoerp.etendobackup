package com.etendoerp.restore


import com.etendoerp.restore.verification.VerificationMessages
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskState


class RestoreModule {

    final static String TMP_DIR                    = "restoreTmpDir"
    final static String BACKUP_LOCATION            = "restoreBackupLocation"
    final static String SOURCES_DESTINATION_DIR    = "restoreSourcesDestinationDir"

    final static String ATTACH_LOCATION_IN_SOURCES = "restoreAttachLocationInsideSources"
    final static String SOURCES_ATTACH_LOCATION    = "restoreSourcesAttachLocation"
    final static String HAS_EXTERNAL_ATTACHMENTS   = "restoreHasExternalAttachments"
    final static String FINAL_ATTACH_LOCATION      = "restoreFinalAttachLocation"

    final static String COPY_SOURCES_ATTACHMENTS   = "restoreCopySourcesAttachments"
    final static String COPY_EXTERNALS_ATTACHMENTS = "restoreCopyExternalsAttachments"

    final static String KEEP_ORIGINAL_PROPERTIES   = "restoreKeepOriginalProperties"
    final static String CURRENT_USER               = "restoreCurrentUser"
    final static String CURRENT_GROUP              = "restoreCurrentGroup"
    final static String GRADLE_PROPERTIES          = "restoreGradleProperties"

    static void load(Project project) {

        def extension = project.extensions.create("restore", RestorePluginExtension)

        project.ext.setProperty(TMP_DIR, null)
        project.ext.setProperty(BACKUP_LOCATION, null)
        project.ext.setProperty(SOURCES_DESTINATION_DIR, null)

        project.ext.setProperty(ATTACH_LOCATION_IN_SOURCES, null)
        project.ext.setProperty(SOURCES_ATTACH_LOCATION, null)
        project.ext.setProperty(HAS_EXTERNAL_ATTACHMENTS, null)

        project.ext.setProperty(FINAL_ATTACH_LOCATION,"")

        // Default copy of attachments set to FALSE
        project.ext.setProperty(COPY_SOURCES_ATTACHMENTS, false)
        project.ext.setProperty(COPY_EXTERNALS_ATTACHMENTS, false)
        project.ext.setProperty(KEEP_ORIGINAL_PROPERTIES, false)

        project.ext.setProperty(CURRENT_USER, null)
        project.ext.setProperty(GRADLE_PROPERTIES, null)

        // Load decompress backup tasks
        RestoreDecompressBackup.load(project)

        // Load decompress all tar files tasks
        RestoreDecompressAll.load(project)

        // Load pg restore database dump tasks
        RestoreDatabaseDump.load(project)

        // Load restore sources tasks
        RestoreSources.load(project)

        // Load restore external attachments tasks
        RestoreExternalAttachments.load(project)

        project.gradle.taskGraph.afterTask { Task task, TaskState state ->
            if (task.name.startsWith("restore")) {
                if (state.failure) {
                    Throwable throwable = state.failure
                    def cause = throwable.getCause() ?: throwable
                    project.logger.info("Error on ${task}: ${cause.toString()}")
                    RestoreUtils.deleteTmpDir(project)
                }
            }
        }

        project.tasks.register("restoreConfig") {
            doLast {
                RestoreUtils.loadTmpDir(project)
                RestoreUtils.loadBackupLocation(project)
                VerificationMessages.userPermissionsVerification(project)
                VerificationMessages.preDecompressVerification(project)
            }
        }

        project.tasks.register("restoreDeleteTmpDir") {
            doLast {
                RestoreUtils.deleteTmpDir(project)
            }
        }

        project.tasks.register("restore") {
            def tasks = [
                    "restoreConfig",
                    "restoreDecompressAll",
                    "restoreDatabaseDump",
                    "restoreSources",
                    "restoreExternalAttachments"
            ]
            RestoreUtils.setTasksOrder(project, tasks)
            dependsOn(tasks)
            finalizedBy project.tasks.named("restoreDeleteTmpDir")
            doLast {
                project.logger.info("restore Finalized.")
            }
        }
    }
}
