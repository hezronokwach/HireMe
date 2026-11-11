package com.example.hireme.shared.web;

import com.example.hireme.shared.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Turns the {@code page}, {@code size} and {@code sort} query parameters into a {@link Pageable},
 * rejecting bad values with 400 instead of letting them reach the database.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param sort          {@code field} or {@code field,asc|desc}; null or blank means {@code defaultSort}
     * @param sortableFields the only fields callers may sort by
     * @param idField        appended as a tie-breaker so rows with equal sort values keep a stable order across pages
     */
    public static Pageable of(int page, int size, String sort, Set<String> sortableFields, String defaultSort, String idField) {
        if (page < 0) {
            throw new InvalidRequestException("'page' must be 0 or more");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("'size' must be between 1 and " + MAX_SIZE);
        }
        Sort order = parseSort(sort == null || sort.isBlank() ? defaultSort : sort, sortableFields);
        return PageRequest.of(page, size, order.and(Sort.by(idField)));
    }

    private static Sort parseSort(String sort, Set<String> sortableFields) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!sortableFields.contains(field) || parts.length > 2) {
            throw new InvalidRequestException("'sort' must be one of " + sortableFields.stream().sorted().toList()
                    + ", optionally followed by ',asc' or ',desc'");
        }
        String direction = parts.length == 2 ? parts[1].trim() : "asc";
        return switch (direction.toLowerCase()) {
            case "asc" -> Sort.by(Sort.Direction.ASC, field);
            case "desc" -> Sort.by(Sort.Direction.DESC, field);
            default -> throw new InvalidRequestException("Sort direction must be 'asc' or 'desc'");
        };
    }
}
