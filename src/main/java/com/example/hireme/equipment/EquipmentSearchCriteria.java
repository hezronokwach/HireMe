package com.example.hireme.equipment;

import java.math.BigDecimal;
import java.time.LocalDate;

// Filters for the equipment list; every field is optional and they combine with AND.
public record EquipmentSearchCriteria(
        Category category,
        String location,          // case-insensitive "contains"
        BigDecimal minRate,       // dailyRateKes >= minRate
        BigDecimal maxRate,       // dailyRateKes <= maxRate
        String keywords,          // words matched against name, location and description (any word)
        LocalDate availableFrom,  // with availableTo: only listings that take bookings and are free on every day
        LocalDate availableTo
) {
    public EquipmentSearchCriteria(Category category, String location, BigDecimal minRate, BigDecimal maxRate) {
        this(category, location, minRate, maxRate, null, null, null);
    }

    public static EquipmentSearchCriteria none() {
        return new EquipmentSearchCriteria(null, null, null, null);
    }

    public static EquipmentSearchCriteria keywordsOnly(String keywords) {
        return new EquipmentSearchCriteria(null, null, null, null, keywords, null, null);
    }

    public boolean hasDates() {
        return availableFrom != null || availableTo != null;
    }
}
