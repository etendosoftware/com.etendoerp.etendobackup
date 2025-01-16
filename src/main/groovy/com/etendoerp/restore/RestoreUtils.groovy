package com.etendoerp.restore

import com.etendoerp.restore.verification.VerificationHelper
import org.gradle.api.Project
import com.etendoerp.restore.RestoreModule as RM

import com.etendoerp.conventions.ConventionNames as CN


class RestoreUtils {

    static loadSourcesDestinationDir(Project project) {

        def destDir = project.findProperty(RM.SOURCES_DESTINATION_DIR)

        // Set default destination dir to root project
        if (!destDir) {
            destDir = project.rootDir
        }

        if (VerificationHelper.forbiddenDirs.contains(project.file(destDir).absolutePath)) {
            throw new IllegalArgumentException("The directory: ${destDir.absolutePath} is FORBIDDEN.")
        }

        // Create dir if not exists
        if (!project.file(destDir).exists()) {
            def currentUser = project.findProperty(RM.CURRENT_USER)
            def currentGroup = project.findProperty(RM.CURRENT_GROUP)
            createDirWithOwner(project, destDir as String, currentUser as String, currentGroup as String)
            project.logger.info("Sources destination dir created: ${destDir}")
        }

        destDir = project.file(destDir)

        project.ext.setProperty(RM.SOURCES_DESTINATION_DIR, destDir)
        project.logger.info("Sources destination directory is set to: ${destDir.absolutePath}")
        return destDir as File
    }

    static loadExternalAttachmentsDir(Project project) {

        def finalAttachLocation = project.findProperty(RM.FINAL_ATTACH_LOCATION)
        def copyExternalAttach = project.findProperty(RM.COPY_EXTERNALS_ATTACHMENTS)

        if (!finalAttachLocation || !copyExternalAttach) {
            return
        }

        if (VerificationHelper.forbiddenDirs.contains(project.file(finalAttachLocation).absolutePath)) {
            throw new IllegalArgumentException("The directory: ${finalAttachLocation} is FORBIDDEN.")
        }

        if (!project.file(finalAttachLocation).exists()) {
            def currentUser = project.findProperty(RM.CURRENT_USER)
            def currentGroup = project.findProperty(RM.CURRENT_GROUP)
            createDirWithOwner(project, finalAttachLocation as String , currentUser as String, currentGroup as String)
            project.logger.info("External attachments dir created: $finalAttachLocation")
        }

        return finalAttachLocation
    }

    static createDirWithOwner(Project project, String dir, String owner, String group = null) {
        CommandLine commandLine = CommandLine.getCommandLine(project)
        commandLine.run(false, "mkdir -p ${dir}")
        if (owner) {
            def own = (group) ? "${owner}:${group}" : "${owner}:${owner}"
            commandLine.run(false, "chown $own $dir")
        }
        return dir
    }

    static loadBackupLocation(Project project) {

        def backupLocation = project.findProperty(RM.BACKUP_LOCATION)
        if (backupLocation) {
            return backupLocation
        }

        final String backupPathProp = "backupPath"

        def backupPath = project.findProperty(backupPathProp)
        if (!backupPath) {
            throw new IllegalArgumentException("You should provide the backup path.\n" +
                                               " Ex: -P${backupPathProp}=/path/to/backup")
        }

        def backupFile = project.file(backupPath)
        if (!backupFile.exists()) {
            throw new IllegalArgumentException("The backup file: ${backupFile.absolutePath} does not exist.")
        }

        if (!backupFile.absolutePath.endsWith(CN.EXTENSION)) {
            throw new IllegalArgumentException("The backup file: ${backupFile.absolutePath} is not a '${CN.EXTENSION}' file.")
        }

        project.ext.setProperty(RM.BACKUP_LOCATION, backupFile)
        return backupFile
    }

    static loadTmpDir(Project project) {

        def tmpDir = project.findProperty(RM.TMP_DIR)

        if (tmpDir) {
            return tmpDir as File
        }

        tmpDir = File.createTempDir()
        project.logger.info("Tmp dir created: ${tmpDir.absolutePath}")
        project.ext.setProperty(RM.TMP_DIR, tmpDir)
        return tmpDir
    }

    static deleteTmpDir(Project project) {
        def tmpDir = project.findProperty(RM.TMP_DIR) as File
        if (tmpDir && project.file(tmpDir).exists()) {
            project.logger.info("Deleting tmp dir: ${tmpDir.absolutePath}")
            project.delete(tmpDir)
        }
    }

    /**
     * Load the gradle properties, a file can be passed to update the properties
     * @param project
     * @param updateFile
     * @return
     */
    static loadGradleProperties(Project project, String updateFile = null ) {

        // Properties already exits
        if (project.ext.has(RM.GRADLE_PROPERTIES) && project.ext.get(RM.GRADLE_PROPERTIES) != null && updateFile == null) {
            return project.ext.get(RM.GRADLE_PROPERTIES) as Map
        }

        def tmpMap = [:]

        def fileName = updateFile ?: "gradle.properties"

        def propFile = project.file(fileName)

        if (!propFile.exists()) {
            throw new IllegalArgumentException("Gradle properties file: ${fileName} not found")
        }

        def gradleProps = new Properties()
        propFile.withInputStream { gradleProps.load(it) }

        tmpMap.put("context_name" , gradleProps.getProperty("context.name", "etendo"))
        tmpMap.put("db_name"      , gradleProps.getProperty("bbdd.sid", "etendo"))
        tmpMap.put("db_port"      , gradleProps.getProperty("bbdd.port", "5432"))
        tmpMap.put("db_user"      , gradleProps.getProperty("bbdd.user", "tad"))
        tmpMap.put("db_pass"      , gradleProps.getProperty("bbdd.password", "tad"))
        tmpMap.put("db_sysuser"   , gradleProps.getProperty("bbdd.systemUser", "postgres"))
        tmpMap.put("db_syspass"   , gradleProps.getProperty("bbdd.systemPassword", "syspass"))

        project.logger.info("Gradle properties: ${tmpMap.toString()}")

        project.ext.setProperty(RM.GRADLE_PROPERTIES, tmpMap)
        return tmpMap
    }

    static setTasksOrder(Project project, List<String> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            project.tasks.named(tasks.get(i + 1)).get().mustRunAfter(project.tasks.named(tasks.get(i)))
        }
    }

    /**
     * Updates the properties of a file
     * @param project
     * @param originPropFile
     * @param dstPropFile
     * @return
     */
    static updatePropertiesFile(Project project, String originPropFile, String dstPropFile) {

        if (!project.file(originPropFile).exists()) {
            throw new IllegalArgumentException("The origin properties file $originPropFile does not exists")
        }

        if (!project.file(dstPropFile).exists()) {
            throw new IllegalArgumentException("The destination properties file $dstPropFile does not exists ")
        }

        // Load properties files
        def originProps = new Properties()
        project.file(originPropFile).withInputStream { originProps.load(it)}

        def destProps = new Properties()
        project.file(dstPropFile).withInputStream {destProps.load(it)}

        project.ant.propertyfile(file: dstPropFile) {
            originProps.each {
                String key = it.key as String
                if (key.startsWith("org.gradle") || !destProps.containsKey(key)) {
                    return
                }
                entry(key: key, value: it.value)
            }

            // Update url
            if (!originProps.containsKey("bbdd.url")) {
                def host = originProps.getProperty("bbdd.host","localhost")
                def port = originProps.getProperty("bbdd.port","5432")
                entry(key:"bbdd.url", value: "jdbc:postgresql://$host:$port")
            }

        }
    }

}