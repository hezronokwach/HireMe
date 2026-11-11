package com.example.hireme.equipment.internal;

import com.example.hireme.booking.BookingStatus;
import com.example.hireme.booking.internal.BookingEntity;
import com.example.hireme.booking.internal.BookingRepository;
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
import java.time.LocalDate;
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

    @Test
    void getByOwner_onlyThatOwnersListings_anyStatus_pagedAndSorted() {
        EquipmentEntity other = byName("Tent");
        other.setOwnerId(2L);
        EquipmentEntity plough = byName("Plough");
        plough.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        equipmentRepository.saveAndFlush(other);
        equipmentRepository.saveAndFlush(plough);

        Pageable byName = PageRequests.of(0, 3, "name", SORTABLE, "createdAt,desc", "id");
        PageResponse<EquipmentResponse> first = equipmentService.getByOwner(1L, byName);

        assertEquals(List.of("Excavator", "Plough", "Tractor A"), first.content().stream().map(EquipmentResponse::name).toList());
        assertEquals(4, first.totalElements());
        assertEquals(List.of("Tent"), equipmentService.getByOwner(2L, byName).content().stream().map(EquipmentResponse::name).toList());
        assertEquals(0, equipmentService.getByOwner(99L, byName).totalElements());
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

    @Test
    void keywords_matchNameLocationOrDescription_anyWord() {
        assertEquals(List.of("Excavator", "Tractor A", "Tractor B"),
                names(new EquipmentSearchCriteria(null, "zq", null, null, "tractor excavator", null, null), "name"));
        assertEquals(List.of("Plough"), names(new EquipmentSearchCriteria(null, "zq", null, null, "eldoret", null, null), "name"));
    }

    @Test
    void availability_leavesOutBookedAndUnderMaintenanceListings() {
        EquipmentEntity plough = byName("Plough");
        plough.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        equipmentRepository.saveAndFlush(plough);
        booking(byName("Tractor A").getId(), LocalDate.of(2030, 3, 10), LocalDate.of(2030, 3, 12), BookingStatus.CONFIRMED);
        booking(byName("Tractor B").getId(), LocalDate.of(2030, 3, 10), LocalDate.of(2030, 3, 12), BookingStatus.PENDING);

        EquipmentSearchCriteria onTheBookedDays = new EquipmentSearchCriteria(Category.AGRICULTURAL, "zq", null, null, null,
                LocalDate.of(2030, 3, 12), LocalDate.of(2030, 3, 14));
        EquipmentSearchCriteria dayAfter = new EquipmentSearchCriteria(Category.AGRICULTURAL, "zq", null, null, null,
                LocalDate.of(2030, 3, 13), null);

        assertEquals(List.of("Tractor B"), names(onTheBookedDays, "name"), "a pending booking doesn't block dates");
        assertEquals(List.of("Tractor A", "Tractor B"), names(dayAfter, "name"));
        assertEquals(List.of("Plough", "Tractor A", "Tractor B"),
                names(new EquipmentSearchCriteria(Category.AGRICULTURAL, "zq", null, null), "name"), "without dates, status doesn't matter");
    }

    @Autowired
    private BookingRepository bookingRepository;

    private EquipmentEntity byName(String name) {
        return equipmentRepository.findAll().stream().filter(e -> name.equals(e.getName())).findFirst().orElseThrow();
    }

    private void booking(Long equipmentId, LocalDate start, LocalDate end, BookingStatus status) {
        BookingEntity b = new BookingEntity();
        b.setEquipmentId(equipmentId);
        b.setClientId(1L);
        b.setStartDate(start);
        b.setEndDate(end);
        b.setTotalCostKes(BigDecimal.valueOf(1000));
        b.setStatus(status);
        b.setCreationDate(Instant.now());
        bookingRepository.saveAndFlush(b);
    }
}
