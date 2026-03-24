package com.nomnom.mvp.service;

import com.nomnom.mvp.domain.ListItem;
import com.nomnom.mvp.domain.UserListView;
import com.nomnom.mvp.support.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ListService {
    private final Map<UUID, UserListState> listsByUserId = new ConcurrentHashMap<>();

    public UserListView getDefaultList(UUID userId) {
        UserListState state = ensureState(userId);
        return new UserListView(state.listId(), "我的收藏", List.copyOf(state.itemsById().values()));
    }

    public ListItem addItem(UUID userId, UUID listId, UUID venueId, String sourceProvider, UUID sourceImportJobId, String note) {
        UserListState state = ensureState(userId);
        if (!state.listId().equals(listId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LIST_NOT_FOUND", "List not found");
        }
        for (ListItem existing : state.itemsById().values()) {
            if (existing.venueId().equals(venueId)) {
                return existing;
            }
        }
        Instant now = Instant.now();
        ListItem item = new ListItem(UUID.randomUUID(), venueId, sourceProvider, sourceImportJobId, note, false, now, now);
        state.itemsById().put(item.id(), item);
        return item;
    }

    public boolean contains(UUID userId, UUID venueId) {
        return ensureState(userId).itemsById().values().stream().anyMatch(item -> item.venueId().equals(venueId));
    }

    public List<UUID> favoriteVenueIds(UUID userId) {
        return ensureState(userId).itemsById().values().stream()
                .map(ListItem::venueId)
                .distinct()
                .toList();
    }

    public UUID defaultListId(UUID userId) {
        return ensureState(userId).listId();
    }

    private UserListState ensureState(UUID userId) {
        return listsByUserId.computeIfAbsent(userId, ignored -> new UserListState(UUID.randomUUID(), new LinkedHashMap<>()));
    }

    private record UserListState(
            UUID listId,
            LinkedHashMap<UUID, ListItem> itemsById
    ) {
    }
}
