package pl.portala.statemachine.domain;

import java.util.List;

/**
 * Offset-based page of a read query (the decision taken for this project
 * is offset pagination with a default page size of 10 and case-insensitive name
 * matching; documented in README.md).
 */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, boolean hasNext) {
}
