package com.etendoerp.backup

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class Logger {

    Project project
    private static Logger logger

    private Logger(Project project) {
        this.project = project
    }

    static Logger getLogger(Project project) {
        if (logger == null)  {
            logger = new Logger(project)
        }
        return logger
    }

    def logToFile(LogLevel logLevel, String message, File log = null, Throwable throwable = null, Boolean printStackTrace = false) {
        try {
            def msg = "${logLevel.toString()} - ${getCurrentDate()} * ${message} \n"
            project.logger.log(logLevel, msg)

            if (throwable) {
                def cause = throwable.getCause() ?: throwable
                msg += "Cause: ${cause?.toString()}\n"
                if (printStackTrace) {
                    StringWriter sw = new StringWriter()
                    throwable.printStackTrace(new PrintWriter(sw))
                    String exceptionAsString = sw.toString()
                    msg += "Stacktrace: \n" + "${exceptionAsString}" + "\n"
                }
            }

            if (log) {
                log << msg
            }

            return msg
        } catch (Exception e) {
            logLevel = LogLevel.ERROR
            def msg = "${logLevel.toString()} - ${getCurrentDate()} * ${e.getMessage()} \n"
            project.logger.info(msg)
            if (log) {
                log << msg
            }
            throw e
        } finally {
            if (logLevel == LogLevel.ERROR && !project.findProperty("errorHandled")) {
                project.ext.setProperty("errorHandled", true)
                BackupUtils.handleError(project)
            }
        }
    }

    static getCurrentDate() {
        return new Date(System.currentTimeMillis()).format("yyyy-MM-dd HH:mm:ssZ").toString()
    }

}