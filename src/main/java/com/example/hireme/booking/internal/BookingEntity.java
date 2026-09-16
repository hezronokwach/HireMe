package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(schema = "app_booking", name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;
    private Long equipmentId;
    private Long clientId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCostKes;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private Instant creationDate;
}
