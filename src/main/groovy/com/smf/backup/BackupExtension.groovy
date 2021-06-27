package com.smf.backup

import org.gradle.api.provider.Property

abstract class BackupPluginExtension {

    final static String CONFIG_PATH = "etendo-backup.conf.properties"

    abstract Property<String> getConfigPath()

    BackupPluginExtension() {
        configPath.convention(CONFIG_PATH)
    }
}
