package com.gateready.engage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends real email when spring.mail.host is set (SPRING_MAIL_HOST), otherwise logs it. */
@Configuration
class EmailConfig {
    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mail, @Value("${app.mail.from:no-reply@gateready.local}") String from,
                            @Value("${spring.mail.host:}") String host) {
        // An empty SPRING_MAIL_HOST (common in env files) still creates a mail bean, so check the value too.
        JavaMailSender sender = host.isBlank() ? null : mail.getIfAvailable();
        return sender == null ? new LoggingEmailSender() : new SmtpEmailSender(sender, from);
    }
}
