package com.acme.salary.transfer.internal;

import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class ImportPreviewService {
  private final CsvImportParser parser;
  private final ImportPreviewWriter writer;

  ImportPreviewService(CsvImportParser parser, ImportPreviewWriter writer) {
    this.parser = parser;
    this.writer = writer;
  }

  ImportPreviewWriter.ImportPreviewResponse validate(MultipartFile file, UUID ownerId) {
    if (file.getSize() > CsvImportParser.MAX_FILE_BYTES) {
      throw new ImportProblem(HttpStatus.CONTENT_TOO_LARGE, "IMPORT_FILE_TOO_LARGE");
    }
    var filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new ImportProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMPORT_FILE");
    }
    return writer.create(ownerId, parser.parse(file));
  }
}
