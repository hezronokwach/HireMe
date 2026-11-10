package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    List<BookingEntity> findByClientId(Long clientId);
    List<BookingEntity> findByEquipmentIdIn(Collection<Long> equipmentIds);
    List<BookingEntity> findByEquipmentIdInAndStatus(Collection<Long> equipmentIds, BookingStatus status);
}
