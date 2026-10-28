package com.acme.webfixtures;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration(proxyBeanMethods = false)
public class ProbeSecurityConfiguration {
  @Bean
  @Order(0)
  SecurityFilterChain isolatedProbeRoutes(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/v1/probe/**", "/api/v1/probe", "/api/v1/missing")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
