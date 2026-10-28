package com.acme.salary.identity.internal;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalHrBootstrap {
  @Bean
  @ConditionalOnProperty(name = "demo.hr.enabled", havingValue = "true")
  CommandLineRunner createLocalHr(
      JdbcTemplate jdbc, PasswordEncoder passwords, org.springframework.core.env.Environment env) {
    return args -> {
      var email = env.getRequiredProperty("DEMO_HR_EMAIL").strip().toLowerCase(Locale.ROOT);
      var password = env.getRequiredProperty("DEMO_HR_PASSWORD");
      if (email.isBlank() || password.length() < 12) {
        throw new IllegalArgumentException(
            "Demo HR email and a password of at least 12 characters are required");
      }
      jdbc.update(
          "INSERT INTO hr_users(id,email,password_hash,role,enabled) VALUES (?,?,?,?,true) ON CONFLICT (email) DO NOTHING",
          UUID.randomUUID(),
          email,
          passwords.encode(password),
          "HR");
    };
  }
}
