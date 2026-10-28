package com.acme.webfixtures;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints, outside the application's component scan and module root. */
@RestController
public class ProbeController {
  @PostMapping(value = "/api/v1/probe", consumes = "application/json")
  public Input validate(@Valid @RequestBody Input input) {
    return input;
  }

  @GetMapping("/api/v1/probe/query")
  public int query(@RequestParam int count) {
    return count;
  }

  @GetMapping("/api/v1/probe/failure")
  public void fail() {
    throw new IllegalStateException("salary=123456 secret SQL SELECT");
  }

  public record Input(@Positive(message = "Rejected salary: ${validatedValue}") int amount) {}
}
