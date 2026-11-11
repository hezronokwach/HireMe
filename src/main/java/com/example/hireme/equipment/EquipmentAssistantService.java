package com.example.hireme.equipment;

// The conversational hire assistant: finds, compares and prices listings by talking with the user.
public interface EquipmentAssistantService {

    AssistantChatResponse chat(AssistantChatRequest request, Long currentUserId);
}
