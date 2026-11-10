package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test against the real Groq API, using the same defaults as application.properties. Runs only
 * when GROQ_API_KEY is set (skipped in CI and on machines without a key), and makes two small calls:
 * <pre>
 * set -a; . ./.env; set +a; ./mvnw test -Dtest=GroqLiveTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class GroqLiveTest {

    record Listing(String title, String description) {
    }

    private AiAssistant assistant;

    @BeforeEach
    void setUp() {
        // HIREME_AI_MODEL picks another model to try; otherwise the default in AiProperties
        AiProperties properties = new AiProperties(true, System.getenv("GROQ_API_KEY"), null, System.getenv("HIREME_AI_MODEL"),
                Duration.ofSeconds(30), 1, 1024, 10);
        assistant = new AiConfig().aiAssistant(properties, Clock.systemUTC());
    }

    @Test
    void generatesText() {
        String answer = assistant.generate(1L, "Answer in one short sentence.",
                "What is a tractor used for on a farm?");

        assertFalse(answer.isBlank());
        System.out.println("Groq text answer: " + answer);
    }

    @Test
    void generatesStructuredJson() {
        Listing listing = assistant.generate(1L,
                "You write short equipment-hire listings for Kenyan farmers.",
                "Write a listing for a 2019 Massey Ferguson tractor in Nakuru, hired out with an operator.",
                Listing.class);

        assertFalse(listing.title().isBlank());
        assertFalse(listing.description().isBlank());
        System.out.println("Groq structured answer: " + listing);
    }
}
