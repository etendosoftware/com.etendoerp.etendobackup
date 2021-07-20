package com.etendoerp.restore

import com.etendoerp.restore.verification.VerificationHelper
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.Sync
import com.etendoerp.restore.RestoreModule as RM

import com.etendoerp.conventions.ConventionNames as CN

class RestoreExternalAttachments {

    static externalAttachChecks(Project project) {
        if (!project.findProperty(RM.COPY_EXTERNALS_ATTACHMENTS)) {
            throw new StopExecutionException("Ignore copy of external attachments")
        }

        if (!project.findProperty(RM.HAS_EXTERNAL_ATTACHMENTS)) {
            throw new StopExecutionException("The backup has not external attachments")
        }
    }

    static load(Project project) {

        project.tasks.register("restoreExternalAttachmentsConfig") {
            doLast {

                externalAttachChecks(project)

                def attachDestinationDir = RestoreUtils.loadExternalAttachmentsDir(project)

                if (!attachDestinationDir || !project.file(attachDestinationDir).exists()) {
                    throw new StopExecutionException("External attachments destination dir does not exists")
                }

                if (VerificationHelper.forbiddenDirs.contains(attachDestinationDir)) {
                    throw new StopExecutionException("FORBIDDEN External attachments destination")
                }

                def tmpDir = RestoreUtils.loadTmpDir(project)

                def attachLocation = "${tmpDir.absolutePath}/${CN.ATTACH_TAR_NAME}"

                Task restExtAttach = project.tasks.named("restoreExternalAttachments").get() as Sync

                restExtAttach.from(attachLocation)
                restExtAttach.into(attachDestinationDir)
            }
        }

        project.tasks.register("restoreExternalAttachments", Sync) {
            dependsOn("restoreExternalAttachmentsConfig")
            doFirst {
                externalAttachChecks(project)
            }
            doLast {
                project.logger.info("restoreExternalAttachments Finalized.")
            }
        }
    }
}