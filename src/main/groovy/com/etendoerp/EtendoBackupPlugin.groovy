package com.etendoerp

import com.etendoerp.backup.BackupModule
import com.etendoerp.restore.RestoreModule
import org.gradle.api.Plugin
import org.gradle.api.Project

public class EtendoBackupPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        BackupModule.load(project)
        RestoreModule.load(project)
    }
}