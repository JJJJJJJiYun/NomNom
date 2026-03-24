package com.nomnom.mvp.domain;

import java.util.List;
import java.util.UUID;

public record UserListView(
        UUID listId,
        String name,
        List<ListItem> items
) {
}
