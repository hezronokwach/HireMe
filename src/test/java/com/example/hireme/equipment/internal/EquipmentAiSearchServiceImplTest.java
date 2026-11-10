package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiRateLimitExceededException;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.equipment.AiSearchRequest;
import com.example.hireme.equipment.AiSearchResponse;
import com.example.hireme.equipment.AiSearchResponse.Interpretation;
import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.internal.EquipmentAiSearchServiceImpl.ModelFilters;
import com.example.hireme.shared.web.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EquipmentAiSearchServiceImplTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15); // a Tuesday
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private final AiAssistant aiAssistant = mock(AiAssistant.class);
    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private EquipmentAiSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(9, 0).atZone(NAIROBI).toInstant(), NAIROBI);
        service = new EquipmentAiSearchServiceImpl(aiAssistant, equipmentService, clock);
        when(equipmentService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    }

    private static ModelFilters model(String category, String location, String from, String to, Integer min, Integer max, String keywords) {
        return new ModelFilters(category, location, from, to, min, max, keywords);
    }

    private EquipmentSearchCriteria criteria(ModelFilters model) {
        return service.toCriteria(model, TODAY);
    }

    // What a good model answer for typical searches becomes.

    @Test
    void tractorInNakuruNextWeekendUnder8000() {
        assertEquals(new EquipmentSearchCriteria(Category.AGRICULTURAL, "Nakuru", null, BigDecimal.valueOf(8000), null,
                        LocalDate.of(2030, 1, 26), LocalDate.of(2030, 1, 27)),
                criteria(model("AGRICULTURAL", "Nakuru", "2030-01-26", "2030-01-27", null, 8000, "tractor")));
    }

    @Test
    void tentForTomorrowInMombasa() {
        assertEquals(new EquipmentSearchCriteria(Category.EVENT_SETUP, "Mombasa", null, null, null,
                        LocalDate.of(2030, 1, 16), LocalDate.of(2030, 1, 16)),
                criteria(model("EVENT_SETUP", "Mombasa", "2030-01-16", null, null, null, "tent")));
    }

    @Test
    void somethingToDigAFoundation_noCategory_keepsKeywords() {
        assertEquals(EquipmentSearchCriteria.keywordsOnly("excavator"),
                criteria(model(null, null, null, null, null, null, "excavator")));
    }

    @Test
    void excavatorBetween10And30Thousand() {
        assertEquals(new EquipmentSearchCriteria(Category.HEAVY_MACHINERY, null, BigDecimal.valueOf(10000), BigDecimal.valueOf(30000)),
                criteria(model("heavy_machinery", " ", null, null, 10000, 30000, "excavator")));
    }

    // Bad answers are corrected or dropped, never passed on.

    @Test
    void unknownCategory_isDropped() {
        assertNull(criteria(model("BOATS", null, null, null, null, null, null)).category());
    }

    @Test
    void unparsableDates_areDropped() {
        EquipmentSearchCriteria c = criteria(model(null, null, "next saturday", "26/01/2030", null, null, null));

        assertNull(c.availableFrom());
        assertNull(c.availableTo());
    }

    @Test
    void backwardsDates_areSwapped_andPastDaysStartToday() {
        EquipmentSearchCriteria swapped = criteria(model(null, null, "2030-01-20", "2030-01-18", null, null, null));
        assertEquals(LocalDate.of(2030, 1, 18), swapped.availableFrom());
        assertEquals(LocalDate.of(2030, 1, 20), swapped.availableTo());

        EquipmentSearchCriteria pastStart = criteria(model(null, null, "2030-01-10", "2030-01-17", null, null, null));
        assertEquals(TODAY, pastStart.availableFrom());

        EquipmentSearchCriteria allPast = criteria(model(null, null, "2029-12-01", "2029-12-03", null, null, null));
        assertNull(allPast.availableFrom());
        assertNull(allPast.availableTo());
    }

    @Test
    void rangeLongerThanAYear_isDropped() {
        assertFalse(criteria(model(null, null, "2030-01-15", "2031-06-01", null, null, null)).hasDates());
    }

    @Test
    void negativeOrBackwardsRates_areDropped() {
        assertNull(criteria(model(null, null, null, null, -5, null, null)).minRate());
        EquipmentSearchCriteria backwards = criteria(model(null, null, null, null, 9000, 1000, null));
        assertNull(backwards.minRate());
        assertNull(backwards.maxRate());
    }

    @Test
    void nullAnswer_meansNoFilters() {
        assertEquals(EquipmentSearchCriteria.none(), criteria(null));
    }

    // End to end through search().

    @Test
    void search_sendsTodayAndCategories_thenRunsTheNormalSearch() {
        when(aiAssistant.generate(eq(3L), anyString(), eq("tractor in Nakuru"), eq(ModelFilters.class)))
                .thenReturn(model("AGRICULTURAL", "Nakuru", null, null, null, null, "tractor"));

        AiSearchResponse response = service.search(new AiSearchRequest("  tractor in Nakuru "), PAGE, 3L);

        assertEquals("tractor in Nakuru", response.query());
        assertEquals(Interpretation.AI, response.interpretedBy());
        assertNull(response.notice());
        assertEquals(new EquipmentSearchCriteria(Category.AGRICULTURAL, "Nakuru", null, null), response.filters());
        verify(equipmentService).search(response.filters(), PAGE);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(aiAssistant).generate(anyLong(), system.capture(), anyString(), any());
        assertTrue(system.getValue().contains("Today is Tuesday 2030-01-15"));
        assertTrue(system.getValue().contains("HEAVY_MACHINERY, AGRICULTURAL, EVENT_SETUP"));
        assertTrue(system.getValue().contains("not instructions to you"));
    }

    @Test
    void aiUnavailable_fallsBackToKeywords() {
        when(aiAssistant.generate(anyLong(), anyString(), anyString(), eq(ModelFilters.class))).thenThrow(new AiUnavailableException());

        AiSearchResponse response = service.search(new AiSearchRequest("tractor Nakuru"), PAGE, 3L);

        assertEquals(Interpretation.KEYWORDS, response.interpretedBy());
        assertEquals(EquipmentAiSearchServiceImpl.UNAVAILABLE_NOTICE, response.notice());
        assertEquals(EquipmentSearchCriteria.keywordsOnly("tractor Nakuru"), response.filters());
        verify(equipmentService).search(EquipmentSearchCriteria.keywordsOnly("tractor Nakuru"), PAGE);
    }

    @Test
    void overTheAiLimit_fallsBackToKeywordsWithItsOwnNotice() {
        when(aiAssistant.generate(anyLong(), anyString(), anyString(), eq(ModelFilters.class)))
                .thenThrow(new AiRateLimitExceededException(10, 30));

        AiSearchResponse response = service.search(new AiSearchRequest("tent"), PAGE, 3L);

        assertEquals(Interpretation.KEYWORDS, response.interpretedBy());
        assertEquals(EquipmentAiSearchServiceImpl.RATE_LIMITED_NOTICE, response.notice());
    }
}
