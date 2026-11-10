package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    List<BookingEntity> findByClientId(Long clientId);
    List<BookingEntity> findByEquipmentIdIn(Collection<Long> equipmentIds);
    List<BookingEntity> findByEquipmentIdInAndStatus(Collection<Long> equipmentIds, BookingStatus status);
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
}
