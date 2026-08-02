package org.kansei.auth.service;

import org.kansei.auth.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(User user, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        send(user.getEmail(), "Confirm your email",
                "Hi " + user.getUsername() + ",\n\n"
                        + "Confirm your email by visiting the link below:\n" + link
                        + "\n\nIf you didn't create this account, ignore this email.");
    }

    public void sendPasswordResetEmail(User user, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        send(user.getEmail(), "Reset your password",
                "Hi " + user.getUsername() + ",\n\n"
                        + "Reset your password by visiting the link below:\n" + link
                        + "\n\nIf you didn't request this, ignore this email - your password won't change.");
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
