package com.etendoerp.backup

import org.gradle.api.provider.Property


abstract class BackupPluginExtension {

    final static String CONFIG_PATH = "config/backup.properties"

    abstract Property<String> getConfigPath()

    BackupPluginExtension() {
        configPath.convention(CONFIG_PATH)
    }
}
