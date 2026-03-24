package com.nomnom.mvp.api;

import com.nomnom.mvp.domain.ImportJob;
import com.nomnom.mvp.service.AuthService;
import com.nomnom.mvp.service.ImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {
    private final AuthService authService;
    private final ImportService importService;

    public ImportController(AuthService authService, ImportService importService) {
        this.authService = authService;
        this.importService = importService;
    }

    @PostMapping("/share-links")
    public ShareImportResponse importShareLinks(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @RequestBody ShareImportRequest request) {
        UUID userId = authService.requireUserId(authorization);
        List<ImportJobResult> results = importService.importSharedLinks(userId, request.imports().stream()
                        .map(item -> new ImportService.SharePayload(item.sourceProvider(), item.sharedText(), item.sharedUrl()))
                        .toList())
                .stream()
                .map(this::toResult)
                .toList();
        return new ShareImportResponse(results);
    }

    @PostMapping("/{importJobId}/complete")
    public ImportJobResult completeImport(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                          @PathVariable UUID importJobId,
                                          @RequestBody CompleteImportRequest request) {
        UUID userId = authService.requireUserId(authorization);
        ImportJob completed = importService.completeImport(userId, importJobId, new ImportService.ManualVenueDraft(
                request.name(),
                request.category(),
                request.avgPrice(),
                request.district(),
                request.businessArea(),
                request.address(),
                request.tags()
        ));
        return toResult(completed);
    }

    private ImportJobResult toResult(ImportJob job) {
        return new ImportJobResult(job.id(), job.status().name(), job.venueId(), job.requiresManualCompletion());
    }

    public record ShareImportRequest(
            List<ShareImportItem> imports
    ) {
    }

    public record ShareImportItem(
            String sourceProvider,
            String sharedText,
            String sharedUrl
    ) {
    }

    public record ShareImportResponse(
            List<ImportJobResult> results
    ) {
    }

    public record ImportJobResult(
            UUID importJobId,
            String status,
            UUID venueId,
            boolean requiresManualCompletion
    ) {
    }

    public record CompleteImportRequest(
            String name,
            String category,
            int avgPrice,
            String district,
            String businessArea,
            String address,
            List<String> tags
    ) {
    }
}
