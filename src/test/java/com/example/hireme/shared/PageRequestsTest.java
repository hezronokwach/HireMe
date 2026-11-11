package com.example.hireme.shared;

import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.shared.web.PageRequests;
import com.example.hireme.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestsTest {

    private static final Set<String> FIELDS = Set.of("name", "createdAt");

    private Pageable of(int page, int size, String sort) {
        return PageRequests.of(page, size, sort, FIELDS, "createdAt,desc", "id");
    }

    @Test
    void noSort_usesDefaultAndAddsIdTieBreaker() {
        assertEquals(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))), of(0, 20, null));
        assertEquals(of(0, 20, null), of(0, 20, "  "));
    }

    @Test
    void fieldWithoutDirection_sortsAscending() {
        assertEquals(Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id")), of(0, 20, "name").getSort());
    }

    @Test
    void directionIsCaseInsensitiveAndTrimmed() {
        assertEquals(Sort.by(Sort.Direction.DESC, "name").and(Sort.by("id")), of(0, 20, " name , DESC ").getSort());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ownerId", "name,sideways", "name,asc,extra", "password"})
    void invalidSort_throws(String sort) {
        assertThrows(InvalidRequestException.class, () -> of(0, 20, sort));
    }

    @Test
    void pageAndSizeLimits() {
        assertDoesNotThrow(() -> of(0, 1, null));
        assertDoesNotThrow(() -> of(0, PageRequests.MAX_SIZE, null));
        assertThrows(InvalidRequestException.class, () -> of(-1, 20, null));
        assertThrows(InvalidRequestException.class, () -> of(0, 0, null));
        assertThrows(InvalidRequestException.class, () -> of(0, PageRequests.MAX_SIZE + 1, null));
    }

    @Test
    void pageResponse_fromPage_mapsContentAndCounts() {
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page, i -> "#" + i);

        assertEquals(new PageResponse<>(List.of("#1", "#2"), 1, 2, 5, 3), response);
    }
}
