package com.nomnom.mvp.service;

import com.nomnom.mvp.domain.ImportJob;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.support.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImportService {
    private final Map<UUID, ImportJob> jobs = new ConcurrentHashMap<>();
    private final VenueService venueService;
    private final ListService listService;

    public ImportService(VenueService venueService, ListService listService) {
        this.venueService = venueService;
        this.listService = listService;
    }

    public List<ImportJob> importSharedLinks(UUID userId, List<SharePayload> payloads) {
        return payloads.stream().map(payload -> importSingle(userId, payload)).toList();
    }

    public ImportJob completeImport(UUID userId, UUID importJobId, ManualVenueDraft draft) {
        ImportJob job = requireOwnedJob(userId, importJobId);
        if (job.status() != ImportJob.Status.PENDING_MANUAL_COMPLETION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_NOT_EDITABLE", "Import job is not waiting for manual completion");
        }

        Venue venue = venueService.createManualVenue(
                draft.name(),
                draft.category(),
                draft.avgPrice(),
                draft.district(),
                draft.businessArea(),
                draft.address(),
                draft.tags(),
                job.sourceProvider(),
                job.sharedUrl()
        );
        listService.addItem(userId, listService.defaultListId(userId), venue.id(), job.sourceProvider(), job.id(), null);
        ImportJob completed = new ImportJob(
                job.id(),
                userId,
                job.sourceProvider(),
                job.sharedText(),
                job.sharedUrl(),
                venue.name(),
                job.parsedExternalId(),
                ImportJob.Status.IMPORTED,
                null,
                venue.id(),
                false,
                job.createdAt(),
                Instant.now()
        );
        jobs.put(completed.id(), completed);
        return completed;
    }

    private ImportJob importSingle(UUID userId, SharePayload payload) {
        Instant now = Instant.now();
        Venue matched = matchExistingVenue(payload.sharedText(), payload.sharedUrl());
        ImportJob job;

        if (matched != null) {
            boolean alreadyInFavorites = listService.contains(userId, matched.id());
            ImportJob.Status status = alreadyInFavorites ? ImportJob.Status.DUPLICATED : ImportJob.Status.IMPORTED;
            if (!alreadyInFavorites) {
                listService.addItem(userId, listService.defaultListId(userId), matched.id(), payload.sourceProvider(), null, null);
            }
            job = new ImportJob(
                    UUID.randomUUID(),
                    userId,
                    payload.sourceProvider(),
                    payload.sharedText(),
                    payload.sharedUrl(),
                    matched.name(),
                    matched.id().toString(),
                    status,
                    null,
                    matched.id(),
                    false,
                    now,
                    now
            );
        } else {
            String parsedName = parseName(payload.sharedText());
            job = new ImportJob(
                    UUID.randomUUID(),
                    userId,
                    payload.sourceProvider(),
                    payload.sharedText(),
                    payload.sharedUrl(),
                    parsedName,
                    null,
                    ImportJob.Status.PENDING_MANUAL_COMPLETION,
                    null,
                    null,
                    true,
                    now,
                    now
            );
        }

        jobs.put(job.id(), job);
        return job;
    }

    private Venue matchExistingVenue(String sharedText, String sharedUrl) {
        return venueService.search(new com.nomnom.mvp.domain.VenueSearchQuery("shanghai", null, null, null, null, null, null, null, null, null, null, "RECOMMENDED", 1, 100))
                .items()
                .stream()
                .filter(venue -> containsIgnoreCase(sharedText, venue.name()) || containsIgnoreCase(sharedUrl, venue.sourceUrl()))
                .findFirst()
                .orElse(null);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private String parseName(String sharedText) {
        if (sharedText == null || sharedText.isBlank()) {
            return null;
        }
        String compact = sharedText.strip();
        int separator = compact.indexOf('，');
        if (separator > 0) {
            return compact.substring(0, separator);
        }
        int space = compact.indexOf(' ');
        return space > 0 ? compact.substring(0, space) : compact;
    }

    private ImportJob requireOwnedJob(UUID userId, UUID importJobId) {
        ImportJob job = jobs.get(importJobId);
        if (job == null || !job.userId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_JOB_NOT_FOUND", "Import job not found");
        }
        return job;
    }

    public record SharePayload(
            String sourceProvider,
            String sharedText,
            String sharedUrl
    ) {
    }

    public record ManualVenueDraft(
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
