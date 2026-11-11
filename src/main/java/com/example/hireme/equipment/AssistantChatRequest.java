package com.example.hireme.equipment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The conversation with the hire assistant so far, oldest first; the last message is the user's new one.
 * The client keeps the conversation and sends it with every message (the server keeps nothing).
 */
public record AssistantChatRequest(
        @NotEmpty(message = "Messages are required")
        @Size(max = 20, message = "At most 20 messages; start a new conversation")
        List<@Valid @NotNull Message> messages
) {

    public enum Role { USER, ASSISTANT }

    public record Message(
            @NotNull(message = "Role is required")
            Role role,
            @NotNull(message = "Text is required")
            @Size(min = 1, max = 1000, message = "A message must be 1 to 1000 characters")
            String text,
            // For assistant messages: the listings it showed, so "the second one" still makes sense later.
            @Size(max = 10, message = "At most 10 listings per message")
            List<Long> listingIds
    ) {
    }
}
