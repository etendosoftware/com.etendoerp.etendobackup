package com.etendoerp

import com.etendoerp.restore.RestoreModule
import org.gradle.api.Plugin
import org.gradle.api.Project

class EtendoBackupPlugin implements Plugin<Project>{





    // Load restore tasks
    @Override
    void apply(Project project) {
        RestoreModule.load(project)
    }

}