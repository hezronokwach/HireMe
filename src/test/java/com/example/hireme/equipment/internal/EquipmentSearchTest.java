package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.shared.web.PageRequests;
import com.example.hireme.shared.web.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Runs the real search (specifications, paging, sorting) on H2; each test rolls back.
// Every location contains "Zq", which no other test data uses, so results only include these rows.
@SpringBootTest
@Transactional
class EquipmentSearchTest {

    private static final Set<String> SORTABLE = Set.of("dailyRateKes", "createdAt", "name");

    @Autowired
    private EquipmentService equipmentService;
    @Autowired
    private EquipmentRepository equipmentRepository;

    @BeforeEach
    void seed() {
        save("Tractor A", Category.AGRICULTURAL, "Nakuru Zq", 5000);
        save("Tractor B", Category.AGRICULTURAL, "NAKURU town Zq", 8000);
        save("Plough", Category.AGRICULTURAL, "Eldoret Zq", 2000);
        save("Excavator", Category.HEAVY_MACHINERY, "Nakuru Zq", 25000);
        save("Tent", Category.EVENT_SETUP, "Nairobi 50% off Zq", 3000);
    }

    private void save(String name, Category category, String location, int rate) {
        EquipmentEntity e = new EquipmentEntity();
        e.setOwnerId(1L);
        e.setName(name);
        e.setCategory(category);
        e.setLocation(location);
        e.setDailyRateKes(BigDecimal.valueOf(rate));
        e.setStatus(EquipmentStatus.AVAILABLE);
        e.setCreatedAt(Instant.now());
        equipmentRepository.save(e);
    }

    private List<String> names(EquipmentSearchCriteria criteria, String sort) {
        return search(criteria, 0, 100, sort).content().stream().map(EquipmentResponse::name).toList();
    }

    private PageResponse<EquipmentResponse> search(EquipmentSearchCriteria criteria, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTABLE, "createdAt,desc", "id");
        return equipmentService.search(criteria, pageable);
    }

    private static EquipmentSearchCriteria location(String location) {
        return new EquipmentSearchCriteria(null, location, null, null);
    }

    @Test
    void location_isCaseInsensitiveContains() {
        assertEquals(List.of("Excavator", "Tractor A", "Tractor B"),
                names(new EquipmentSearchCriteria(null, " nakuru ", null, null), "name"));
    }

    @Test
    void location_wildcardCharactersMatchLiterally() {
        assertEquals(List.of("Tent"), names(location("50% off"), "name"));
        assertEquals(List.of(), names(location("5_%"), "name"));
    }

    @Test
    void filtersCombineWithAnd_andRatesAreInclusive() {
        EquipmentSearchCriteria criteria = new EquipmentSearchCriteria(Category.AGRICULTURAL, "zq",
                BigDecimal.valueOf(2000), BigDecimal.valueOf(5000));

        assertEquals(List.of("Plough", "Tractor A"), names(criteria, "dailyRateKes"));
    }

    @Test
    void sortByRateDescending() {
        assertEquals(List.of("Excavator", "Tractor B", "Tractor A", "Tent", "Plough"),
                names(location("zq"), "dailyRateKes,desc"));
    }

    @Test
    void paging_returnsSliceAndTotals() {
        // by name: Excavator, Plough | Tent, Tractor A | Tractor B
        PageResponse<EquipmentResponse> second = search(location("zq"), 1, 2, "name");

        assertEquals(List.of("Tent", "Tractor A"), second.content().stream().map(EquipmentResponse::name).toList());
        assertEquals(1, second.page());
        assertEquals(2, second.size());
        assertEquals(5, second.totalElements());
        assertEquals(3, second.totalPages());
    }

    @Test
    void pagePastTheEnd_isEmptyWithTotals() {
        PageResponse<EquipmentResponse> page = search(location("zq"), 9, 2, "name");

        assertTrue(page.content().isEmpty());
        assertEquals(5, page.totalElements());
    }

    @Test
    void comparables_sameCategoryAndLocationIgnoringCase() {
        List<String> names = equipmentRepository
                .findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(Category.AGRICULTURAL, "nakuru zq").stream()
                .map(EquipmentEntity::getName).sorted().toList();

        assertEquals(List.of("Tractor A"), names); // "NAKURU town Zq" doesn't contain "nakuru zq"
        assertTrue(equipmentRepository.findTop20ByCategoryOrderByCreatedAtDesc(Category.AGRICULTURAL).size() >= 3);
    }

    @Test
    void description_upTo1000CharactersFits() {
        EquipmentEntity e = equipmentRepository.findAll().stream().filter(x -> "Plough".equals(x.getName())).findFirst().orElseThrow();
        e.setDescription("x".repeat(1000));

        equipmentRepository.saveAndFlush(e);

        assertEquals(1000, equipmentRepository.findById(e.getId()).orElseThrow().getDescription().length());
    }
}
