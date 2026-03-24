package com.nomnom.mvp.api;

import com.nomnom.mvp.domain.ListItem;
import com.nomnom.mvp.domain.UserListView;
import com.nomnom.mvp.domain.Venue;
import com.nomnom.mvp.service.AuthService;
import com.nomnom.mvp.service.ListService;
import com.nomnom.mvp.service.VenueService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lists")
public class ListController {
    private final AuthService authService;
    private final ListService listService;
    private final VenueService venueService;

    public ListController(AuthService authService, ListService listService, VenueService venueService) {
        this.authService = authService;
        this.listService = listService;
        this.venueService = venueService;
    }

    @GetMapping("/default")
    public DefaultListResponse getDefaultList(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        UUID userId = authService.requireUserId(authorization);
        UserListView view = listService.getDefaultList(userId);
        List<DefaultListItem> items = view.items().stream().map(this::toItem).toList();
        return new DefaultListResponse(view.listId(), view.name(), items);
    }

    @PostMapping("/{listId}/items")
    public AddListItemResponse addItem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                       @PathVariable UUID listId,
                                       @RequestBody AddListItemRequest request) {
        UUID userId = authService.requireUserId(authorization);
        ListItem item = listService.addItem(userId, listId, request.venueId(), "NOMNOM", null, request.note());
        return new AddListItemResponse(item.id());
    }

    private DefaultListItem toItem(ListItem item) {
        Venue venue = venueService.get(item.venueId());
        return new DefaultListItem(
                item.id(),
                venue.id(),
                venue.name(),
                venue.avgPrice(),
                venue.rating(),
                null,
                item.note(),
                item.sourceProvider()
        );
    }

    public record DefaultListResponse(
            UUID listId,
            String name,
            List<DefaultListItem> items
    ) {
    }

    public record DefaultListItem(
            UUID itemId,
            UUID venueId,
            String name,
            int avgPrice,
            double rating,
            Integer distanceMeters,
            String note,
            String sourceProvider
    ) {
    }

    public record AddListItemRequest(
            UUID venueId,
            String note
    ) {
    }

    public record AddListItemResponse(
            UUID itemId
    ) {
    }
}
