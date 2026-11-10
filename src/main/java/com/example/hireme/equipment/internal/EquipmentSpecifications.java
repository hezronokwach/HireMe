package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Builds the WHERE clause for the equipment list from the filters that were given.
final class EquipmentSpecifications {

    static final int MAX_KEYWORDS = 5;

    // Common words in search sentences that would match almost anything.
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "near", "from", "this", "next", "that", "need", "want", "looking",
            "hire", "rent", "rental", "day", "days", "week", "weekend", "month", "under", "below", "less",
            "than", "cheap", "kes", "ksh", "shillings", "per", "some", "any", "can", "please", "today", "tomorrow");

    private EquipmentSpecifications() {
    }

    /**
     * @param bookedEquipmentIds equipment already booked during the criteria's dates (empty without dates);
     *                           those listings are left out
     */
    static Specification<EquipmentEntity> matching(EquipmentSearchCriteria criteria, Collection<Long> bookedEquipmentIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.category() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.category()));
            }
            if (criteria.location() != null && !criteria.location().isBlank()) {
                predicates.add(contains(root, cb, "location", criteria.location()));
            }
            if (criteria.minRate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dailyRateKes"), criteria.minRate()));
            }
            if (criteria.maxRate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dailyRateKes"), criteria.maxRate()));
            }
            List<String> words = keywords(criteria.keywords());
            if (!words.isEmpty()) {
                predicates.add(cb.or(words.stream()
                        .map(word -> cb.or(contains(root, cb, "name", word), contains(root, cb, "location", word),
                                contains(root, cb, "description", word)))
                        .toArray(Predicate[]::new)));
            }
            if (criteria.hasDates()) {
                predicates.add(cb.equal(root.get("status"), EquipmentStatus.AVAILABLE));
                if (!bookedEquipmentIds.isEmpty()) {
                    predicates.add(cb.not(root.get("id").in(bookedEquipmentIds)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Distinct lower-case words of 3+ letters, without stop words, at most MAX_KEYWORDS.
    static List<String> keywords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 3 && !STOP_WORDS.contains(word) && !word.chars().allMatch(Character::isDigit))
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
    }

    private static Predicate contains(Root<EquipmentEntity> root, CriteriaBuilder cb, String field, String value) {
        String pattern = "%" + escapeLike(value.trim().toLowerCase(Locale.ROOT)) + "%";
        return cb.like(cb.lower(root.get(field)), pattern, '\\');
    }

    // So "50%" or "a_b" typed by a user match literally instead of acting as wildcards.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
