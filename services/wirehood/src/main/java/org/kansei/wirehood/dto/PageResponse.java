package org.kansei.wirehood.dto;

import java.util.List;

// Offset-based (page/size), not cursor-based (comments, admin thumbnail queue, library), none of which need to stay stable under concurrent inserts the way a live feed would
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }
}
