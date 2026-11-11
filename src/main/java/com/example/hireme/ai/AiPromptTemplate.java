package com.example.hireme.ai;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

/**
 * A prompt kept in a resource file (e.g. {@code prompts/describe-equipment.st}) instead of in code.
 * Placeholders are written {@code {name}}, so the template text itself can't contain literal braces.
 */
public final class AiPromptTemplate {

    private final PromptTemplate template;

    private AiPromptTemplate(PromptTemplate template) {
        this.template = template;
    }

    public static AiPromptTemplate fromClasspath(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt template not found on the classpath: " + path);
        }
        return new AiPromptTemplate(new PromptTemplate(resource));
    }

    // Every placeholder in the template needs a value.
    public String render(Map<String, Object> values) {
        return template.render(values);
    }
}
