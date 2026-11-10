package com.example.hireme.equipment;

public interface EquipmentDescriptionService {

    // Throws AiUnavailableException (503) or AiRateLimitExceededException (429) from the ai module.
    EquipmentDescriptionResponse describe(DescribeEquipmentRequest request, Long currentUserId);
}
