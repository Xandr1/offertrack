package com.offertrack.auth;

import com.offertrack.users.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthEmailService {
  private final JavaMailSender mailSender;
  private final String mailFrom;
  private final String appWebUrl;

  public AuthEmailService(
      JavaMailSender mailSender,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${app.web-url}") String appWebUrl) {
    this.mailSender = mailSender;
    this.mailFrom = mailFrom;
    this.appWebUrl = appWebUrl;
  }

  public void sendVerificationEmail(User user, String token) {
    String verificationUrl = normalizedWebUrl() + "/verify-email?token=" + token;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailFrom);
    message.setTo(user.email());
    message.setSubject("Verify your OfferTrack email");
    message.setText(
        """
        Verify your email address for OfferTrack:

        %s

        This link expires in 24 hours.
        """
            .formatted(verificationUrl));

    mailSender.send(message);
  }

  public void sendPasswordResetEmail(User user, String token) {
    String resetUrl = normalizedWebUrl() + "/reset-password?token=" + token;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailFrom);
    message.setTo(user.email());
    message.setSubject("Reset your OfferTrack password");
    message.setText(
        """
        Reset your OfferTrack password:

        %s

        This link expires in 1 hour. If you did not request it, you can ignore this email.
        """
            .formatted(resetUrl));

    mailSender.send(message);
  }

  private String normalizedWebUrl() {
    if (appWebUrl.endsWith("/")) {
      return appWebUrl.substring(0, appWebUrl.length() - 1);
    }

    return appWebUrl;
  }
}
