package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    Page<BookingEntity> findByClientId(Long clientId, Pageable pageable);
    Page<BookingEntity> findByEquipmentIdIn(Collection<Long> equipmentIds, Pageable pageable);
    Page<BookingEntity> findByEquipmentIdInAndStatus(Collection<Long> equipmentIds, BookingStatus status, Pageable pageable);
    boolean existsByEquipmentIdAndStatusInAndEndDateGreaterThanEqual(Long equipmentId, Collection<BookingStatus> statuses, LocalDate date);

    // Both start and end days are part of a booking, so [1st, 3rd] and [3rd, 5th] overlap but [1st, 3rd] and [4th, 5th] don't.
    @Query("""
            select b from BookingEntity b
            where b.equipmentId = :equipmentId
              and b.status in :statuses
              and b.startDate <= :endDate
              and b.endDate >= :startDate
            order by b.startDate
            """)
    List<BookingEntity> findOverlapping(@Param("equipmentId") Long equipmentId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("statuses") Collection<BookingStatus> statuses);

    // Same overlap rule as findOverlapping (both start and end days count), across all equipment.
    @Query("""
            select distinct b.equipmentId from BookingEntity b
            where b.status in :statuses
              and b.startDate <= :to
              and b.endDate >= :from
            """)
    List<Long> findEquipmentIdsBookedBetween(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("statuses") Collection<BookingStatus> statuses);

    List<BookingEntity> findByStatusAndStartDateLessThanEqual(BookingStatus status, LocalDate date);

    // Changes the status only if it is still `from`; returns 1 when it did, 0 when the booking had moved on.
    // Bulk updates skip @Version, so bump it here: anyone holding the old version then gets a conflict.
    @Modifying
    @Query("update BookingEntity b set b.status = :to, b.version = b.version + 1 where b.bookingId = :bookingId and b.status = :from")
    int updateStatus(@Param("bookingId") Long bookingId,
                     @Param("from") BookingStatus from,
                     @Param("to") BookingStatus to);
}
