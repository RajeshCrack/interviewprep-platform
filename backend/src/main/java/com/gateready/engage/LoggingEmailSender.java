package com.gateready.engage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used until SMTP is configured: writes the email to the log instead of sending it. */
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email not sent (no SMTP configured). To: {} | {} | {}", to, subject, body);
    }
}
