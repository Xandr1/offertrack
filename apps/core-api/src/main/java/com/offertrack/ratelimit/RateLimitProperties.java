package com.offertrack.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
  public static final String LOCAL_KEY_SECRET = "local-dev-rate-limit-key-secret-change-me";

  @NotBlank private String keySecret = LOCAL_KEY_SECRET;
  private boolean failOpen = true;

  @Valid private Policy loginEmail = new Policy(5, Duration.ofMinutes(15));
  @Valid private Policy loginIp = new Policy(20, Duration.ofMinutes(15));
  @Valid private Policy registrationEmail = new Policy(3, Duration.ofHours(1));
  @Valid private Policy registrationIp = new Policy(5, Duration.ofHours(1));
  @Valid private Policy verificationResendEmail = new Policy(3, Duration.ofHours(1));
  @Valid private Policy verificationResendIp = new Policy(10, Duration.ofHours(1));
  @Valid private Policy forgotPasswordEmail = new Policy(5, Duration.ofHours(1));
  @Valid private Policy forgotPasswordIp = new Policy(20, Duration.ofHours(1));
  @Valid private Policy resetPasswordToken = new Policy(10, Duration.ofHours(1));
  @Valid private Policy resetPasswordIp = new Policy(20, Duration.ofHours(1));
  @Valid private Policy aiUserMinute = new Policy(10, Duration.ofMinutes(1));
  @Valid private Policy aiUserDay = new Policy(100, Duration.ofDays(1));

  public String getKeySecret() {
    return keySecret;
  }

  public void setKeySecret(String keySecret) {
    this.keySecret = keySecret;
  }

  public boolean isFailOpen() {
    return failOpen;
  }

  public void setFailOpen(boolean failOpen) {
    this.failOpen = failOpen;
  }

  public Policy getLoginEmail() {
    return loginEmail;
  }

  public void setLoginEmail(Policy loginEmail) {
    this.loginEmail = loginEmail;
  }

  public Policy getLoginIp() {
    return loginIp;
  }

  public void setLoginIp(Policy loginIp) {
    this.loginIp = loginIp;
  }

  public Policy getRegistrationEmail() {
    return registrationEmail;
  }

  public void setRegistrationEmail(Policy registrationEmail) {
    this.registrationEmail = registrationEmail;
  }

  public Policy getRegistrationIp() {
    return registrationIp;
  }

  public void setRegistrationIp(Policy registrationIp) {
    this.registrationIp = registrationIp;
  }

  public Policy getVerificationResendEmail() {
    return verificationResendEmail;
  }

  public void setVerificationResendEmail(Policy verificationResendEmail) {
    this.verificationResendEmail = verificationResendEmail;
  }

  public Policy getVerificationResendIp() {
    return verificationResendIp;
  }

  public void setVerificationResendIp(Policy verificationResendIp) {
    this.verificationResendIp = verificationResendIp;
  }

  public Policy getForgotPasswordEmail() {
    return forgotPasswordEmail;
  }

  public void setForgotPasswordEmail(Policy forgotPasswordEmail) {
    this.forgotPasswordEmail = forgotPasswordEmail;
  }

  public Policy getForgotPasswordIp() {
    return forgotPasswordIp;
  }

  public void setForgotPasswordIp(Policy forgotPasswordIp) {
    this.forgotPasswordIp = forgotPasswordIp;
  }

  public Policy getResetPasswordToken() {
    return resetPasswordToken;
  }

  public void setResetPasswordToken(Policy resetPasswordToken) {
    this.resetPasswordToken = resetPasswordToken;
  }

  public Policy getResetPasswordIp() {
    return resetPasswordIp;
  }

  public void setResetPasswordIp(Policy resetPasswordIp) {
    this.resetPasswordIp = resetPasswordIp;
  }

  public Policy getAiUserMinute() {
    return aiUserMinute;
  }

  public void setAiUserMinute(Policy aiUserMinute) {
    this.aiUserMinute = aiUserMinute;
  }

  public Policy getAiUserDay() {
    return aiUserDay;
  }

  public void setAiUserDay(Policy aiUserDay) {
    this.aiUserDay = aiUserDay;
  }

  public static class Policy {
    @Min(1)
    private int maxAttempts;

    @NotNull private Duration window;

    public Policy() {}

    public Policy(int maxAttempts, Duration window) {
      this.maxAttempts = maxAttempts;
      this.window = window;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration window) {
      this.window = window;
    }

    @AssertTrue(message = "rate-limit window must be at least one second")
    public boolean isWindowAtLeastOneSecond() {
      return window != null && !window.isNegative() && window.compareTo(Duration.ofSeconds(1)) >= 0;
    }
  }
}
