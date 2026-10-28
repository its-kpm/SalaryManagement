package com.acme.salary.identity.internal;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
class HrUserRepository {
  private final JdbcTemplate jdbc;

  HrUserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  UserDetails findByEmail(String email) {
    return jdbc
        .query(
            "SELECT id, email, password_hash, enabled, role FROM hr_users WHERE email = ?",
            (rs, row) ->
                new HrPrincipal(
                    rs.getObject("id", UUID.class),
                    new User(
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getBoolean("enabled"),
                        true,
                        true,
                        true,
                        java.util.List.of(
                            new SimpleGrantedAuthority("ROLE_" + rs.getString("role"))))),
            email)
        .stream()
        .findFirst()
        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
  }
}
