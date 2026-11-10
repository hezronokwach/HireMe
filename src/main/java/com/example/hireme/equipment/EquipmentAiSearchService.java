package com.example.hireme.equipment;

import org.springframework.data.domain.Pageable;

public interface EquipmentAiSearchService {

    // Never fails because of AI: without it, falls back to a keyword search.
    AiSearchResponse search(AiSearchRequest request, Pageable pageable, Long currentUserId);
}
