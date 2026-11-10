package com.example.hireme.equipment;

import java.math.BigDecimal;

// Filters for the equipment list; every field is optional and they combine with AND.
public record EquipmentSearchCriteria(
        Category category,
        String location,       // case-insensitive "contains"
        BigDecimal minRate,    // dailyRateKes >= minRate
        BigDecimal maxRate     // dailyRateKes <= maxRate
) {
    public static EquipmentSearchCriteria none() {
        return new EquipmentSearchCriteria(null, null, null, null);
    }
}
