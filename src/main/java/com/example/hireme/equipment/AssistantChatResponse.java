package com.example.hireme.equipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The assistant's answer. `reply` is the model's text; `listings` are cards built by the server from the
 * database (only listings the assistant actually looked up), so names, rates and availability are real.
 */
public record AssistantChatResponse(
        String reply,
        List<ListingCard> listings,
        LocalDate from,     // the dates being discussed, or null
        LocalDate to,
        List<String> suggestions // short follow-ups the user might send next
) {

    public record ListingCard(
            Long id,
            String name,
            Category category,
            String location,
            BigDecimal dailyRateKes,
            boolean takingBookings,
            Quote quote // null when no dates are being discussed
    ) {
    }

    // Price for the dates being discussed: days × rate, both days counted. free: takes bookings and nothing booked then.
    public record Quote(LocalDate from, LocalDate to, long days, BigDecimal totalKes, boolean free) {
    }
}
