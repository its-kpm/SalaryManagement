package com.acme.salary.transfer.internal;

import java.util.UUID;
import org.slf4j.MDC;
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
  private final ImportApplicationService applications;

  ImportPreviewController(ImportPreviewService imports, ImportApplicationService applications) {
    this.imports = imports;
    this.applications = applications;
  }

  @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ImportPreviewWriter.ImportPreviewResponse validate(
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal(expression = "id") UUID ownerId) {
    return imports.validate(file, ownerId);
  }

  @PostMapping("/{previewId}/apply")
  ImportApplicationService.ImportReceipt apply(
      @org.springframework.web.bind.annotation.PathVariable UUID previewId,
      @AuthenticationPrincipal(expression = "id") UUID ownerId) {
    return applications.apply(previewId, ownerId, UUID.fromString(MDC.get("requestId")));
  }
}
