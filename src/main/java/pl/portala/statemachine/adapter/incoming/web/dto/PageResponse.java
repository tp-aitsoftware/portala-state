package pl.portala.statemachine.adapter.incoming.web.dto;

import pl.portala.statemachine.domain.PageResult;

import java.util.List;

/** Offset-paginated list response (  page is 0-based). */
public record PageResponse<T>(List<T> content, int page, int size, long totalItems, boolean hasNext) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalItems(), result.hasNext());
    }
}
