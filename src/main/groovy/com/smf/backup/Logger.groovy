package com.smf.backup

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class Logger {

    Project project
    private static Logger logger

    private Logger(Project project) {
        this.project = project
    }

    public static Logger getLogger(Project project) {
        if (logger == null)  {
            logger = new Logger(project)
        }
        return logger
    }

    def logToFile(LogLevel logLevel, String message, File log = null, Throwable throwable = null, Boolean printStackTrace = false) {
        def msg = "${logLevel.toString()} - ${getCurrentDate()} * ${message} \n"
        project.logger.log(logLevel, msg)
    }

    def getCurrentDate() {
        return new Date(System.currentTimeMillis()).format("yyyy-MM-dd HH:mm:ssZ").toString()
    }

}