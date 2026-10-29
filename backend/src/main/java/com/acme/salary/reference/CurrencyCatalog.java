package com.acme.salary.reference;

import java.util.OptionalInt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Read-only reference-data contract for currency input validation. */
@Service
public class CurrencyCatalog {
  private final JdbcClient jdbc;

  CurrencyCatalog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public OptionalInt minorUnits(String currencyCode) {
    return jdbc
        .sql("SELECT minor_units FROM currencies WHERE code = :code")
        .param("code", currencyCode)
        .query(Integer.class)
        .optional()
        .stream()
        .mapToInt(Integer::intValue)
        .findFirst();
  }
}
