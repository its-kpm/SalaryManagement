package com.acme.salary.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
  private final AuthenticationManager authentication;
  private final SecurityContextRepository contexts;

  AuthController(AuthenticationManager authentication, SecurityContextRepository contexts) {
    this.authentication = authentication;
    this.contexts = contexts;
  }

  @GetMapping("/csrf")
  CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getHeaderName(), token.getToken());
  }

  @PostMapping("/login")
  UserResponse login(
      @Valid @RequestBody LoginRequest credentials,
      HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    try {
      var user =
          authentication.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  credentials.email().strip().toLowerCase(Locale.ROOT), credentials.password()));
      request.changeSessionId();
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(user);
      SecurityContextHolder.setContext(context);
      contexts.saveContext(context, request, response);
      var principal = (HrPrincipal) user.getPrincipal();
      return new UserResponse(principal.id(), principal.getUsername(), "HR");
    } catch (org.springframework.security.core.AuthenticationException failure) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
  }

  @GetMapping("/me")
  UserResponse me(@AuthenticationPrincipal HrPrincipal principal) {
    return new UserResponse(principal.id(), principal.getUsername(), "HR");
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(HttpServletRequest request) {
    SecurityContextHolder.clearContext();
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    return ResponseEntity.noContent().build();
  }

  record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank String password) {}

  record CsrfResponse(String headerName, String token) {}

  record UserResponse(UUID id, String email, String role) {}
}
