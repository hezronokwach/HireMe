package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.EquipmentSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Builds the WHERE clause for the equipment list from the filters that were given.
final class EquipmentSpecifications {

    private EquipmentSpecifications() {
    }

    static Specification<EquipmentEntity> matching(EquipmentSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.category() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.category()));
            }
            if (criteria.location() != null && !criteria.location().isBlank()) {
                String pattern = "%" + escapeLike(criteria.location().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("location")), pattern, '\\'));
            }
            if (criteria.minRate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dailyRateKes"), criteria.minRate()));
            }
            if (criteria.maxRate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dailyRateKes"), criteria.maxRate()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // So "50%" or "a_b" typed by a user match literally instead of acting as wildcards.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
