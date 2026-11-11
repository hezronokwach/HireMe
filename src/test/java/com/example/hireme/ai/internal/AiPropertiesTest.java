package com.example.hireme.ai.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiPropertiesTest {

    @Test
    void keyAndModelPastedWithSurroundingWhitespace_areTrimmed() {
        AiProperties properties = new AiProperties(true, "  test-key\n", null, "openai/gpt-oss-20b\r\n", null, 1, 0, 0);

        assertEquals("test-key", properties.apiKey());
        assertEquals("openai/gpt-oss-20b", properties.model());
    }

    @Test
    void missingKey_staysMissing_andBlankModelGetsTheDefault() {
        AiProperties properties = new AiProperties(false, null, null, " ", null, 1, 0, 0);

        assertNull(properties.apiKey());
        assertEquals("openai/gpt-oss-120b", properties.model());
    }
}
