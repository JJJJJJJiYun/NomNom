package com.nomnom.mvp.service;

import com.nomnom.mvp.domain.ImportJob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImportServiceTest {
    @Test
    void importsKnownVenueIntoFavorites() {
        VenueService venueService = new VenueService();
        ListService listService = new ListService();
        ImportService importService = new ImportService(venueService, listService);
        UUID userId = UUID.randomUUID();

        ImportJob job = importService.importSharedLinks(userId, List.of(
                new ImportService.SharePayload(
                        "DIANPING",
                        "鸟居烧肉，快来看看吧 https://m.dianping.com/shopshare/torii-yakiniku",
                        "https://m.dianping.com/shopshare/torii-yakiniku"
                )
        )).getFirst();

        assertEquals(ImportJob.Status.IMPORTED, job.status());
        assertNotNull(job.venueId());
        assertFalse(listService.getDefaultList(userId).items().isEmpty());
    }

    @Test
    void createsPendingManualImportWhenNoKnownVenueMatches() {
        VenueService venueService = new VenueService();
        ListService listService = new ListService();
        ImportService importService = new ImportService(venueService, listService);

        ImportJob job = importService.importSharedLinks(UUID.randomUUID(), List.of(
                new ImportService.SharePayload(
                        "DIANPING",
                        "神秘新店，快来看看吧 https://m.dianping.com/shopshare/unknown",
                        "https://m.dianping.com/shopshare/unknown"
                )
        )).getFirst();

        assertEquals(ImportJob.Status.PENDING_MANUAL_COMPLETION, job.status());
        assertEquals("神秘新店", job.parsedName());
    }
}
