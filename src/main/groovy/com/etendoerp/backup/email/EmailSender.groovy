package com.etendoerp.backup.email

import com.etendoerp.backup.BackupUtils
import com.etendoerp.backup.Logger
import com.etendoerp.backup.mode.Mode
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

import javax.mail.*
import javax.mail.internet.*

import com.etendoerp.backup.BackupModule as BM

class EmailSender {

    Project project
    Logger log

    final static String EMAIL_PREFIX = "EMAIL"
    final static String PYTHON_EMAIL_PATH="/home/futit/sendMail.py"

    EmailSender(Project project) {
        this.project = project
        this.log = Logger.getLogger(project)
    }

    /**
     * Sends an email if the backup mode running is 'auto'.
     * Verifies if an emails has been send to prevent sending multiples emails.
     * @param logFile
     * @param emailType
     * @return
     */
    def sendEmailWithLog(File logFile, EmailType emailType = EmailType.ERROR) {
        def mode = project.findProperty(BM.BACKUP_MODE)
        // Prevent sending multiple emails
        if (mode == Mode.AUTO.value && logFile && !project.findProperty(BM.EMAIL_IS_SENDING)) {
            project.ext.setProperty(BM.EMAIL_IS_SENDING, true)
            sendLogToMail(logFile, emailType)
        }
    }

    def send(
            Map senderConf,
            String from,
            String to,
            String subject,
            String content,
            String mimeType,
            File file = null,
            String[] multipleCC = null
    ) {
        // Init constants of sender email account.
        String email    = senderConf.user
        String password = senderConf.password
        String host     = senderConf.host
        String port     = senderConf.port

        // Set up properties.
        Properties props = System.getProperties()
        props.put("mail.smtp.user", email)
        props.put("mail.smtp.host", host)
        props.put("mail.smtp.port", port)
        props.put("mail.smtp.starttls.enable","true")
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        props.put("mail.smtp.ssl.trust", "*") // Change host to "*" if you want to trust all host.

        // Set up message.
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(props))
        message.setFrom(new InternetAddress(from))
        message.addRecipients(Message.RecipientType.TO, new InternetAddress(to))

        // send to multiples cc
        if (multipleCC) {
            multipleCC.each {
                message.addRecipients(Message.RecipientType.CC, new InternetAddress(it))
            }
        }

        message.setSubject(subject)
        if (file && file.size() >= 0) {
            BodyPart messageBodyPart = new MimeBodyPart()
            messageBodyPart.setContent(content, mimeType)

            Multipart multipart = new MimeMultipart()
            multipart.addBodyPart(messageBodyPart)

            messageBodyPart = new MimeBodyPart()
            messageBodyPart.attachFile(file)
            multipart.addBodyPart(messageBodyPart)
            message.setContent(multipart)
        } else {
            message.setContent(content, mimeType)
        }

        // Send mail.
        Transport.send(message, email, password)
        log.logToFile(LogLevel.INFO, "Email sended with 'GRADLE'", project.findProperty(BM.FILE_TO_LOG) as File)
    }

    def sendPythonEmail(File logFile, EmailType emailType = EmailType.ERROR) {

        def confProps = BackupUtils.loadConfigurationProperties(project)
        def etendoConf = BackupUtils.loadEtendoBackupConf(project)

        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        def to = getEmailPropertyValue(emailType, EmailProperty.TO)
        def subject = getEmailPropertyValue(emailType, EmailProperty.SUBJECT) ?: "${emailType.value} on Etendo backup."

        subject = subject.concat(" Project name: ${project.name} - Context name: ${confProps?.context_name ?: "undefined"}")

        def pythonScriptLocation = etendoConf?.EMAIL_PYTHON_LOCATION ?: PYTHON_EMAIL_PATH

        def result = project.exec {
            environment "EMAIL_FROM", etendoConf?.EMAIL_FROM
            environment "EMAIL_TO", to
            environment "EMAIL_SERVER",etendoConf?.EMAIL_SERVER
            environment "EMAIL_PORT", etendoConf?.EMAIL_PORT
            environment "EMAIL_TLS", etendoConf?.EMAIL_TLS
            environment "EMAIL_USER", etendoConf?.EMAIL_USER
            environment "EMAIL_PASSWORD", etendoConf?.EMAIL_PASSWORD
            environment "EMAIL_ENVIRONMENT", etendoConf?.EMAIL_ENVIRONMENT ?: project.name
            environment "EMAIL_TEMP_FILE", logFile.absolutePath
            environment "EMAIL_SUBJECT", subject

            // Path to python script
            commandLine "python", pythonScriptLocation
            standardOutput = stdout
            errorOutput = stderr
            ignoreExitValue true
        }

        def output = (result.getExitValue() == 0) ? stdout.toString() : stderr.toString()

        return [result.getExitValue(), output]
    }

    def sendLogToMail(File logFile, EmailType emailType = EmailType.ERROR) {
        try {
            // Try sending email with gradle function
            def confProps  = BackupUtils.loadConfigurationProperties(project)
            def etendoConf = BackupUtils.loadEtendoBackupConf(project)
            def senderConf = [:]
            senderConf.put("user", etendoConf?.EMAIL_USER as String)
            senderConf.put("password", etendoConf?.EMAIL_PASSWORD as String)
            senderConf.put("host", etendoConf?.EMAIL_SERVER as String)
            senderConf.put("port", etendoConf?.EMAIL_PORT as String)
            senderConf.put("tls", etendoConf?.EMAIL_TLS as String)

            def from    = etendoConf?.EMAIL_FROM as String
            def to = getEmailPropertyValue(emailType, EmailProperty.TO)
            def cc = (getEmailPropertyValue(emailType, EmailProperty.CC) as String).split(";")
            def subject = getEmailPropertyValue(emailType, EmailProperty.SUBJECT) ?: "${emailType.value} on Etendo backup."

            subject = subject.concat(" Project name: ${project.name} - Context name: ${confProps?.context_name ?: "undefined"}")

            send(senderConf as Map, from as String, to as String, subject as String, subject as String,"text/plain", project.file(logFile), cc)
        } catch (Exception e) {
            project.logger.info("Error sending email with 'GRADLE'")
            e.printStackTrace()
            project.logger.info("Sending email with python script")
            def (exit, output) = sendPythonEmail(project.file(logFile), emailType)
            if (exit == 1) {
                // Save email result in the logfile
                log.logToFile(LogLevel.WARN, "Error sending email with 'GRADLE' and 'PYTHON'", project.findProperty(BM.FILE_TO_LOG) as File, e)
                throw new IllegalStateException("Error sending email with python script: ${output}")
            }
        }
    }

    private getEmailPropertyValue(EmailType emailType, EmailProperty emailProperty) {
        def etendoConfig = BackupUtils.loadEtendoBackupConf(project)
        def value = "${EMAIL_PREFIX}_${emailType.value}_${emailProperty.value}"
        return etendoConfig[value]
    }

}