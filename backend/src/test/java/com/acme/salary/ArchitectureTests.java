package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

class ArchitectureTests {
  @Test
  void discoversEveryDeclaredModuleAndVerifiesBoundaries() {
    var modules = ApplicationModules.of(SalaryManagementApplication.class);
    assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
        .containsExactlyInAnyOrder(
            "identity",
            "reference",
            "audit",
            "employee",
            "compensation",
            "reporting",
            "transfer",
            "platform");
    modules.verify();
  }

  @ParameterizedTest
  @CsvSource({"forbidden,Allowed targets", "internal,non-exposed", "cycle,Cycle detected"})
  void rejectsDeliberatelyInvalidModuleArrangements(String fixture, String diagnostic) {
    assertThatThrownBy(
            () ->
                ApplicationModules.of(
                        "com.acme.archfixtures." + fixture, (ImportOption) location -> true)
                    .verify())
        .isInstanceOf(Violations.class)
        .hasMessageContaining(diagnostic);
  }
}
