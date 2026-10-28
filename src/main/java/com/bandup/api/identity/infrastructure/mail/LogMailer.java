package com.bandup.api.identity.infrastructure.mail;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.port.Mailer;

/**
 * {@link Mailer} for local development and tests: nothing is sent, the email
 * (with its link) is written to the log. Never active in production.
 */
@Component
@ConditionalOnProperty(name = "bandup.mail.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
class LogMailer implements Mailer {

    @Override
    public void send(Message message) {
        log.info("Email (not sent, local mode) to {} — {}\n{}", message.to(), message.subject(), message.text());
    }
}
