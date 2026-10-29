package com.acme.salary.transfer.internal;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
class ImportPreviewController {
  private final ImportPreviewService imports;

  ImportPreviewController(ImportPreviewService imports) {
    this.imports = imports;
  }

  @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ImportPreviewWriter.ImportPreviewResponse validate(
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal(expression = "id") UUID ownerId) {
    return imports.validate(file, ownerId);
  }
}
