package com.signalfeed.exception;

/**
 * Thrown by {@link com.signalfeed.service.EmailService} when an email cannot
 * be constructed or delivered.
 *
 * <p>Wraps {@link org.springframework.mail.MailException} (transport failures)
 * and {@link jakarta.mail.MessagingException} (MIME construction failures).
 * Callers (the scheduler) catch this to log the failure and continue to the
 * next scheduled run without rethrowing.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
