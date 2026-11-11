package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentBookingChecker;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.equipment.internal.AssistantTools.DatesResult;
import com.example.hireme.equipment.internal.AssistantTools.SearchResult;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.shared.web.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssistantToolsTest {

    static final LocalDate TODAY = LocalDate.of(2030, 1, 15);

    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private final EquipmentBookingChecker bookingChecker = mock(EquipmentBookingChecker.class);
    private AssistantTools tools;

    static EquipmentResponse listing(long id, String name, Category category, int rate, EquipmentStatus status) {
        return new EquipmentResponse(id, 5L, name, category, "x".repeat(250), BigDecimal.valueOf(rate), "Nakuru",
                status, Instant.now(), null);
    }

    static final EquipmentResponse TRACTOR = listing(1, "Tractor", Category.AGRICULTURAL, 6000, EquipmentStatus.AVAILABLE);
    static final EquipmentResponse PLOUGH = listing(2, "Plough", Category.AGRICULTURAL, 2500, EquipmentStatus.AVAILABLE);

    @BeforeEach
    void setUp() {
        EquipmentAiSearchServiceImpl filters = new EquipmentAiSearchServiceImpl(mock(AiAssistant.class), equipmentService,
                Clock.systemUTC());
        tools = new AssistantTools(equipmentService, bookingChecker, filters, TODAY);
    }

    private static PageResponse<EquipmentResponse> page(EquipmentResponse... items) {
        return new PageResponse<>(List.of(items), 0, AssistantTools.MAX_RESULTS, items.length, 1);
    }

    @Test
    void search_keepsTheWordsWithTheCategory_cheapestFirst_andRemembersResults() {
        when(equipmentService.search(any(), any())).thenReturn(page(TRACTOR));

        SearchResult result = tools.searchListings("agricultural", "Nakuru", null, 8000, "tractor", null, null);

        ArgumentCaptor<EquipmentSearchCriteria> criteria = ArgumentCaptor.forClass(EquipmentSearchCriteria.class);
        verify(equipmentService).search(criteria.capture(), any());
        assertEquals(Category.AGRICULTURAL, criteria.getValue().category());
        assertEquals("tractor", criteria.getValue().keywords());
        assertEquals(BigDecimal.valueOf(8000), criteria.getValue().maxRate());
        assertNull(result.note());
        assertEquals(List.of(1L), result.listings().stream().map(AssistantTools.ListingInfo::id).toList());
        assertEquals(201, result.listings().getFirst().description().length(), "descriptions are cut to keep prompts small");
        assertSame(TRACTOR, tools.seen().get(1L));
    }

    @Test
    void search_withWordsNothingMentions_fallsBackToTheCategory_andSaysSo() {
        when(equipmentService.search(any(), any())).thenReturn(page()).thenReturn(page(PLOUGH, TRACTOR));

        SearchResult result = tools.searchListings("AGRICULTURAL", null, null, null, "massey", null, null);

        assertEquals(2, result.listings().size());
        assertTrue(result.note().contains("No listing mentions \"massey\""));
    }

    @Test
    void search_badModelFilters_areCleanedNotFailed() {
        when(equipmentService.search(any(), any())).thenReturn(page());

        SearchResult result = tools.searchListings("BOATS", null, -5, null, null, "next friday", "2029-01-01");

        ArgumentCaptor<EquipmentSearchCriteria> criteria = ArgumentCaptor.forClass(EquipmentSearchCriteria.class);
        verify(equipmentService).search(criteria.capture(), any());
        assertNull(criteria.getValue().category());
        assertNull(criteria.getValue().minRate());
        assertEquals("Nothing matches these filters.", result.note());
    }

    @Test
    void checkDates_pricesBothDays_andChecksBookings() {
        when(equipmentService.getById(1L)).thenReturn(TRACTOR);
        when(bookingChecker.findBookedEquipmentIds(LocalDate.of(2030, 1, 19), LocalDate.of(2030, 1, 21))).thenReturn(Set.of());

        DatesResult result = tools.checkDates(1L, "2030-01-19", "2030-01-21");

        assertEquals(3, result.days());
        assertEquals(0, BigDecimal.valueOf(18000).compareTo(result.totalKes()));
        assertTrue(result.free());
        assertSame(TRACTOR, tools.seen().get(1L));
    }

    @Test
    void checkDates_bookedOrUnderMaintenance_isNotFree() {
        EquipmentResponse paused = listing(3, "Paused", Category.AGRICULTURAL, 1000, EquipmentStatus.UNDER_MAINTENANCE);
        when(equipmentService.getById(1L)).thenReturn(TRACTOR);
        when(equipmentService.getById(3L)).thenReturn(paused);
        when(bookingChecker.findBookedEquipmentIds(any(), any())).thenReturn(Set.of(1L));

        assertFalse(tools.checkDates(1L, "2030-01-20", "2030-01-20").free());
        DatesResult maintenance = tools.checkDates(3L, "2030-01-20", "2030-01-20");
        assertFalse(maintenance.free());
        assertFalse(maintenance.takingBookings());
    }

    @Test
    void checkDates_explainsBadInput_insteadOfFailing() {
        when(equipmentService.getById(99L)).thenThrow(new EquipmentNotFoundException());

        assertTrue(tools.checkDates(1L, "2030-01-10", "2030-01-12").note().contains("past"));
        assertTrue(tools.checkDates(1L, "soon", null).note().contains("YYYY-MM-DD"));
        assertTrue(tools.checkDates(1L, "2030-01-16", "2031-06-01").note().contains("more than a year"));
        assertTrue(tools.checkDates(99L, "2030-01-16", "2030-01-16").note().contains("no listing 99"));
        assertTrue(tools.seen().isEmpty());
    }

    @Test
    void reply_isRecordedForTheService() {
        assertNull(tools.finalReply());

        tools.reply("Here you go", List.of(1L), "2030-01-19", "2030-01-21", List.of("Cheaper?"));

        assertEquals(new EquipmentAssistantServiceImpl.ModelReply("Here you go", List.of(1L), "2030-01-19",
                "2030-01-21", List.of("Cheaper?")), tools.finalReply());
    }
}
