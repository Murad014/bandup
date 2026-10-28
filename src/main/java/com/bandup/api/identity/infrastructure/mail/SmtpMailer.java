package com.bandup.api.identity.infrastructure.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.port.Mailer;

/**
 * {@link Mailer} over SMTP ({@code bandup.mail.enabled=true}, production). The
 * server comes from {@code spring.mail.*} (any provider: Brevo, Resend,
 * Mailgun…); the sender address from {@code bandup.mail.from}.
 */
@Component
@ConditionalOnProperty(name = "bandup.mail.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class SmtpMailer implements Mailer {

    private final JavaMailSender sender;

    @Value("${bandup.mail.from}")
    private String from;

    @Override
    public void send(Message message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        try {
            sender.send(mail);
        } catch (MailException e) {
            log.error("Couldn't send \"{}\": {}", message.subject(), e.getMessage());
        }
    }
}
