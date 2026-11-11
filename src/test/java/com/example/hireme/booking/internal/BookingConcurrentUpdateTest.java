package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingService;
import com.example.hireme.booking.BookingStatus;
import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.equipment.internal.EquipmentEntity;
import com.example.hireme.equipment.internal.EquipmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two changes to the same booking that overlap in time, played out step by step in one thread:
 * transaction A reads the booking, transaction B (REQUIRES_NEW, so A is suspended) changes it and
 * commits, then A changes its stale copy. With @Version, A's commit fails instead of overwriting B.
 */
@SpringBootTest
class BookingConcurrentUpdateTest {

    private static final long OWNER_ID = 7001L;
    private static final long CLIENT_ID = 7002L;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingActivator bookingActivator;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate outer;
    private TransactionTemplate inner;
    private Long equipmentId;

    @BeforeEach
    void setUp() {
        outer = new TransactionTemplate(transactionManager);
        inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        EquipmentEntity equipment = new EquipmentEntity();
        equipment.setOwnerId(OWNER_ID);
        equipment.setName("Concurrency test excavator");
        equipment.setCategory(Category.HEAVY_MACHINERY);
        equipment.setDailyRateKes(BigDecimal.valueOf(1000));
        equipment.setLocation("Nairobi");
        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setCreatedAt(Instant.now());
        equipmentId = equipmentRepository.save(equipment).getId();
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll(bookingRepository.findByEquipmentIdIn(List.of(equipmentId), Pageable.unpaged()).getContent());
        equipmentRepository.deleteById(equipmentId);
    }

    private Long saveBooking(BookingStatus status, LocalDate start) {
        BookingEntity booking = new BookingEntity();
        booking.setEquipmentId(equipmentId);
        booking.setClientId(CLIENT_ID);
        booking.setStartDate(start);
        booking.setEndDate(start.plusDays(2));
        booking.setTotalCostKes(BigDecimal.valueOf(3000));
        booking.setStatus(status);
        booking.setCreationDate(Instant.now());
        return bookingRepository.save(booking).getBookingId();
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    @Test
    void ownerConfirmsWhileClientCancels_confirmFailsAndCancellationIsKept() {
        Long bookingId = saveBooking(BookingStatus.PENDING, LocalDate.now().plusDays(30));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> outer.executeWithoutResult(a -> {
            bookingRepository.findById(bookingId).orElseThrow();                    // A reads PENDING
            inner.executeWithoutResult(b -> bookingService.cancel(bookingId, CLIENT_ID)); // B cancels, commits
            bookingService.confirm(bookingId, OWNER_ID);                           // A confirms its stale copy
        }));

        assertEquals(BookingStatus.CANCELLED, statusOf(bookingId));
    }

    @Test
    void clientCancelsWhileOwnerCompletes_cancelFailsAndCompletionIsKept() {
        Long bookingId = saveBooking(BookingStatus.CONFIRMED, LocalDate.now().plusDays(30));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> outer.executeWithoutResult(a -> {
            bookingRepository.findById(bookingId).orElseThrow();
            inner.executeWithoutResult(b -> bookingService.complete(bookingId, OWNER_ID));
            bookingService.cancel(bookingId, CLIENT_ID);
        }));

        assertEquals(BookingStatus.COMPLETED, statusOf(bookingId));
    }

    @Test
    void activationBumpsVersion_staleCancelByClientFails() {
        Long bookingId = saveBooking(BookingStatus.CONFIRMED, LocalDate.now().minusDays(1));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> outer.executeWithoutResult(a -> {
            bookingRepository.findById(bookingId).orElseThrow();                // A reads CONFIRMED
            inner.executeWithoutResult(b -> bookingActivator.activateDueBookings()); // job makes it ACTIVE
            bookingService.cancel(bookingId, CLIENT_ID);                       // client may cancel CONFIRMED, not ACTIVE
        }));

        assertEquals(BookingStatus.ACTIVE, statusOf(bookingId));
    }

    @Test
    void changesOneAfterTheOther_bothSucceed() {
        Long bookingId = saveBooking(BookingStatus.PENDING, LocalDate.now().plusDays(30));

        bookingService.confirm(bookingId, OWNER_ID);
        bookingService.cancel(bookingId, CLIENT_ID);

        BookingEntity booking = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(2L, booking.getVersion());
    }
}
