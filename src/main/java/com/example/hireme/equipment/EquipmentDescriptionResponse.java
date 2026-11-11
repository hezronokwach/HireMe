package com.example.hireme.equipment;

import java.math.BigDecimal;

/**
 * An AI draft for the owner to accept or edit; nothing is saved. The rate is only suggested when there
 * are similar listings to base it on, and only when it is in line with them.
 */
public record EquipmentDescriptionResponse(
        String title,
        String description,
        BigDecimal suggestedDailyRateKes, // null: no suggestion
        String rateReason,                // null when there is no suggestion
        int comparableListings            // how many similar listings the suggestion was based on
) {
}
