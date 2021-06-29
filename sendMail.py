# IMPORT smtplib FOR THE ACTUAL SENDING FUNCTION
import smtplib
import os

# IMPORT THE EMAIL MODULES WE'LL NEED.
from email.MIMEText import MIMEText

# OPEN A PLAIN TEXT FILE FOR READING.

# CONFIGURATION VARIABLES:
mailfrom = os.getenv('EMAIL_FROM', 'il2@smfconsulting.es')
mailto   = os.getenv('EMAIL_TO', 'staff@smfconsulting.es')
server   = os.getenv('EMAIL_SERVER', 'smtp.gmail.com')
port     = os.getenv('EMAIL_PORT', '587')
tls      = os.getenv('EMAIL_TLS', True)
user     = os.getenv('EMAIL_FROM', 'il2@smfconsulting.es')
password = os.getenv('EMAIL_PASSWORD')
entorno  = os.getenv('EMAIL_ENVIRONMENT')
body_file = os.getenv('EMAIL_TEMP_FILE', '/tmp/mailText.txt')

# CREATING A text/plain MESSAGE.

fp = open(body_file, 'rb')
msg = MIMEText(fp.read())
fp.close()

msg['Subject'] = os.getenv('EMAIL_SUBJECT')
msg['From']    = mailfrom
msg['To']      = mailto

# SEND THE MESSAGE VIA THE SMTP SERVER.
server = smtplib.SMTP(server, port)

try:
    server.set_debuglevel(True)

    # identify ourselves, prompting server for supported features
    server.ehlo()

    # If we can encrypt this session, do it
    if tls:
        server.starttls()
        server.ehlo() # re-identify ourselves over TLS connection

    server.login(user, password)
    server.sendmail(mailfrom, [mailto], msg.as_string())
finally:
    server.quit()
