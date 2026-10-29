package com.acme.salary.reference;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Bulk reference-data snapshot for bounded import validation. */
@Service
public class ImportReferenceData {
  private final JdbcClient jdbc;

  ImportReferenceData(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Snapshot snapshot() {
    var departments =
        jdbc
            .sql("SELECT id, name FROM departments")
            .query(
                (row, rowNumber) ->
                    new Department(row.getObject("id", UUID.class), row.getString("name")))
            .list()
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    department -> department.name().toLowerCase(Locale.ROOT),
                    department -> department));
    var countries = Set.copyOf(jdbc.sql("SELECT code FROM countries").query(String.class).list());
    var currencies =
        jdbc
            .sql("SELECT code, minor_units FROM currencies")
            .query((row, rowNumber) -> Map.entry(row.getString("code"), row.getInt("minor_units")))
            .list()
            .stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    return new Snapshot(departments, countries, currencies);
  }

  public record Snapshot(
      Map<String, Department> departmentsByNormalizedName,
      Set<String> countryCodes,
      Map<String, Integer> currencyMinorUnits) {}

  public record Department(UUID id, String name) {}
}
