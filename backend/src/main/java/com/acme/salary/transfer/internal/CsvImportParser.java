package com.acme.salary.transfer.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class CsvImportParser {
  static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
  static final int MAX_ROWS = 10_000;
  static final List<String> HEADERS =
      List.of(
          "employeeNumber",
          "fullName",
          "email",
          "department",
          "countryCode",
          "jobTitle",
          "jobLevel",
          "employmentStatus",
          "annualBaseAmount",
          "currencyCode",
          "effectiveFrom",
          "reason");

  ParsedCsv parse(MultipartFile file) {
    var hash = sha256(file);
    var rows = new ArrayList<RawRow>();
    var errors = new ArrayList<ImportError>();
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    var format =
        CSVFormat.RFC4180
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(false)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .get();
    try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), decoder));
        var parser = format.parse(reader)) {
      var actualHeaders = new ArrayList<>(parser.getHeaderNames());
      if (!actualHeaders.isEmpty()) {
        actualHeaders.set(0, actualHeaders.getFirst().replaceFirst("^\\uFEFF", ""));
      }
      if (!actualHeaders.equals(HEADERS)) {
        errors.add(
            new ImportError(
                1, "header", "INVALID_HEADER", "The CSV header does not match the template."));
        return new ParsedCsv(hash, List.of(), errors, 0);
      }
      for (var record : parser) {
        if (rows.size() == MAX_ROWS) {
          errors.add(
              new ImportError(
                  Math.toIntExact(record.getRecordNumber() + 1),
                  "file",
                  "TOO_MANY_ROWS",
                  "The CSV contains more than 10,000 data rows."));
          break;
        }
        var values = new ArrayList<String>(record.size());
        record.forEach(value -> values.add(value.strip()));
        var rowNumber = Math.toIntExact(record.getRecordNumber() + 1);
        if (values.size() != HEADERS.size()) {
          errors.add(
              new ImportError(
                  rowNumber,
                  "row",
                  "INVALID_COLUMN_COUNT",
                  "The row must contain exactly 12 columns."));
        } else {
          rows.add(new RawRow(rowNumber, List.copyOf(values)));
        }
      }
    } catch (IOException | UncheckedIOException | IllegalArgumentException failure) {
      errors.add(
          new ImportError(0, "file", "MALFORMED_CSV", "The file is not valid UTF-8 RFC 4180 CSV."));
    }
    if (rows.isEmpty() && errors.isEmpty()) {
      errors.add(new ImportError(0, "file", "EMPTY_FILE", "The CSV contains no data rows."));
    }
    return new ParsedCsv(hash, rows, errors, rows.size());
  }

  private static String sha256(MultipartFile file) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = new DigestInputStream(file.getInputStream(), digest)) {
        input.transferTo(java.io.OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    } catch (IOException failure) {
      throw new ImportProblem(
          org.springframework.http.HttpStatus.BAD_REQUEST, "IMPORT_READ_FAILED");
    }
  }

  record ParsedCsv(String contentHash, List<RawRow> rows, List<ImportError> errors, int rowCount) {}

  record RawRow(int rowNumber, List<String> values) {
    String value(int index) {
      return values.get(index);
    }
  }

  record ImportError(int row, String field, String code, String message) {}
}
