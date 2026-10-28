package com.bandup.api.identity.application.port;

/** Port: sends an email (SMTP in production; the log in local development and tests). */
public interface Mailer {

    /** Never throws: a failure is logged by the adapter (the user can ask for the email again). */
    void send(Message message);

    /** A plain-text email. */
    record Message(String to, String subject, String text) {
    }
}
