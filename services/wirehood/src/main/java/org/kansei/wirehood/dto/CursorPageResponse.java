package org.kansei.wirehood.dto;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasMore, long totalElements) {
    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor, long totalElements) {
        return new CursorPageResponse<>(items, nextCursor, nextCursor != null, totalElements);
    }
}
