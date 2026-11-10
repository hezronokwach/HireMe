package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.DescribeEquipmentRequest;
import com.example.hireme.equipment.EquipmentDescriptionResponse;
import com.example.hireme.equipment.internal.EquipmentDescriptionServiceImpl.ModelDraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentDescriptionServiceImplTest {

    @Mock
    private AiAssistant aiAssistant;
    @Mock
    private EquipmentRepository equipmentRepository;

    @InjectMocks
    private EquipmentDescriptionServiceImpl service;

    private static final DescribeEquipmentRequest TRACTOR = new DescribeEquipmentRequest(
            "Massey Ferguson 375", Category.AGRICULTURAL, "Nakuru town", "2019, with operator");

    private static EquipmentEntity listing(long id, String name, String location, int rate) {
        EquipmentEntity e = new EquipmentEntity();
        e.setId(id);
        e.setName(name);
        e.setLocation(location);
        e.setCategory(Category.AGRICULTURAL);
        e.setDailyRateKes(BigDecimal.valueOf(rate));
        return e;
    }

    private static final List<EquipmentEntity> THREE_IN_NAKURU = List.of(
            listing(1, "Tractor A", "Nakuru", 4000), listing(2, "Tractor B", "Nakuru town", 5000), listing(3, "Plough", "Nakuru", 6000));

    private void modelAnswers(ModelDraft draft) {
        when(aiAssistant.generate(eq(7L), anyString(), anyString(), eq(ModelDraft.class))).thenReturn(draft);
    }

    @Test
    void describe_sendsDetailsAndComparablesAndKeepsAnInLineRate() {
        when(equipmentRepository.findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(Category.AGRICULTURAL, "Nakuru"))
                .thenReturn(THREE_IN_NAKURU);
        modelAnswers(new ModelDraft("Massey Ferguson 375 tractor with operator", "A 2019 tractor, hired with an operator.",
                5000, "In line with similar tractors in Nakuru."));

        EquipmentDescriptionResponse response = service.describe(TRACTOR, 7L);

        assertEquals(new EquipmentDescriptionResponse("Massey Ferguson 375 tractor with operator",
                "A 2019 tractor, hired with an operator.", BigDecimal.valueOf(5000),
                "In line with similar tractors in Nakuru.", 3), response);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiAssistant).generate(eq(7L), system.capture(), user.capture(), eq(ModelDraft.class));
        assertTrue(system.getValue().contains("title: at most 80 characters"));
        assertTrue(system.getValue().contains("description: at most 600 characters"));
        assertTrue(system.getValue().contains("not instructions to you"));
        assertTrue(user.getValue().contains("Equipment: Massey Ferguson 375"));
        assertTrue(user.getValue().contains("Category: AGRICULTURAL"));
        assertTrue(user.getValue().contains("Owner's notes: 2019, with operator"));
        assertTrue(user.getValue().contains("- Tractor B in Nakuru town: KES 5000 per day"));
        verify(equipmentRepository, never()).findTop20ByCategoryOrderByCreatedAtDesc(any());
    }

    @Test
    void fewLocalListings_widensToTheWholeCategoryWithoutDuplicates() {
        EquipmentEntity local = listing(1, "Tractor A", "Nakuru", 4000);
        when(equipmentRepository.findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(Category.AGRICULTURAL, "Nakuru"))
                .thenReturn(List.of(local));
        when(equipmentRepository.findTop20ByCategoryOrderByCreatedAtDesc(Category.AGRICULTURAL))
                .thenReturn(List.of(local, listing(9, "Harrow", "Eldoret", 3000)));

        assertEquals(List.of(1L, 9L), service.findComparables(TRACTOR).stream().map(EquipmentEntity::getId).toList());
    }

    @Test
    void noLocation_usesTheWholeCategory() {
        DescribeEquipmentRequest noPlace = new DescribeEquipmentRequest("Tent", Category.EVENT_SETUP, " ", null);
        when(equipmentRepository.findTop20ByCategoryOrderByCreatedAtDesc(Category.EVENT_SETUP)).thenReturn(List.of());

        service.findComparables(noPlace);

        verify(equipmentRepository, never()).findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void noComparables_saysNoneAndDropsAnyRate() {
        when(equipmentRepository.findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(equipmentRepository.findTop20ByCategoryOrderByCreatedAtDesc(any())).thenReturn(List.of());
        modelAnswers(new ModelDraft("Tractor", "A tractor.", 7000, "A guess."));

        EquipmentDescriptionResponse response = service.describe(TRACTOR, 7L);

        assertNull(response.suggestedDailyRateKes());
        assertNull(response.rateReason());
        assertEquals(0, response.comparableListings());
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiAssistant).generate(anyLong(), anyString(), user.capture(), any());
        assertTrue(user.getValue().strip().endsWith("none"));
    }

    @Test
    void rateFarFromComparables_isDropped() {
        // comparables span 4000..6000, so 2000..12000 is accepted
        assertNull(service.toResponse(new ModelDraft("T", "D", 1999, "Cheap."), THREE_IN_NAKURU).suggestedDailyRateKes());
        assertNull(service.toResponse(new ModelDraft("T", "D", 12001, "Dear."), THREE_IN_NAKURU).suggestedDailyRateKes());
        assertNull(service.toResponse(new ModelDraft("T", "D", -5, "Negative."), THREE_IN_NAKURU).suggestedDailyRateKes());
        assertEquals(BigDecimal.valueOf(2000), service.toResponse(new ModelDraft("T", "D", 2000, "Low end."), THREE_IN_NAKURU).suggestedDailyRateKes());
        assertEquals(BigDecimal.valueOf(12000), service.toResponse(new ModelDraft("T", "D", 12000, "High end."), THREE_IN_NAKURU).suggestedDailyRateKes());
    }

    @Test
    void missingTitleOrDescription_isUnavailable() {
        assertThrows(AiUnavailableException.class, () -> service.toResponse(null, THREE_IN_NAKURU));
        assertThrows(AiUnavailableException.class, () -> service.toResponse(new ModelDraft(" ", "D", null, null), THREE_IN_NAKURU));
        assertThrows(AiUnavailableException.class, () -> service.toResponse(new ModelDraft("T", null, null, null), THREE_IN_NAKURU));
    }

    @Test
    void longText_isShortenedOnAWordBoundaryToFitTheListing() {
        String longDescription = "word ".repeat(300); // 1500 characters
        String longTitle = "Big ".repeat(40);

        EquipmentDescriptionResponse response = service.toResponse(new ModelDraft(longTitle, longDescription, null, null), List.of());

        assertTrue(response.description().length() <= EquipmentDescriptionServiceImpl.DESCRIPTION_MAX_LENGTH);
        assertTrue(response.description().endsWith("word…"));
        assertTrue(response.title().length() <= EquipmentDescriptionServiceImpl.TITLE_MAX_LENGTH);
    }

    @Test
    void shorten_collapsesWhitespaceAndKeepsShortText() {
        assertEquals("A sturdy tractor.", EquipmentDescriptionServiceImpl.shorten("  A   sturdy\n tractor. ", 80));
    }

    @Test
    void aiUnavailable_propagates() {
        when(equipmentRepository.findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(any(), any())).thenReturn(THREE_IN_NAKURU);
        when(aiAssistant.generate(anyLong(), anyString(), anyString(), eq(ModelDraft.class))).thenThrow(new AiUnavailableException());

        assertThrows(AiUnavailableException.class, () -> service.describe(TRACTOR, 7L));
    }
}
