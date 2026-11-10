package com.example.hireme.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiPromptTemplateTest {

    @Test
    void rendersPlaceholders() {
        String prompt = AiPromptTemplate.fromClasspath("prompts/describe-equipment-request.st").render(Map.of(
                "name", "Tractor", "category", "AGRICULTURAL", "location", "Nakuru", "notes", "2019", "comparables", "none"));

        assertTrue(prompt.contains("Equipment: Tractor"));
        assertTrue(prompt.contains("Location: Nakuru"));
        assertFalse(prompt.contains("{"), "every placeholder is filled");
    }

    @Test
    void missingTemplate_failsClearly() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AiPromptTemplate.fromClasspath("prompts/nope.st"));
        assertTrue(ex.getMessage().contains("prompts/nope.st"));
    }
}
