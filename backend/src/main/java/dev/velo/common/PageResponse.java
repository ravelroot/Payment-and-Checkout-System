package dev.velo.common;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long total, int pages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new PageResponse<>(items, page, size, total, pages);
    }

    public <R> PageResponse<R> map(Function<T, R> f) {
        return new PageResponse<>(items.stream().map(f).toList(), page, size, total, pages);
    }
}
