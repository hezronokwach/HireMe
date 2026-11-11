package com.example.hireme.shared.web;

import com.example.hireme.shared.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI description served at /v3/api-docs, with Swagger UI at /swagger-ui.html.
 * Every operation needs the JWT from POST /api/v1/auth/login unless it says otherwise
 * (the auth endpoints opt out with an empty @SecurityRequirements).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI hireMeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HireMe API")
                        .version("v1")
                        .description("Hire out equipment: owners list machinery with a daily rate in KES, "
                                + "clients book it for a date range. Log in with POST /api/v1/auth/login, "
                                + "then click Authorize and paste the token.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The token from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * Documents the error responses every operation can return, all with the {@link ErrorResponse} body:
     * 400 everywhere, 401/403 wherever a token is needed, and any 4xx a controller declares itself
     * (e.g. {@code @ApiResponse(responseCode = "409")}). springdoc would give those the success body,
     * so every 4xx body is replaced with ErrorResponse. Success bodies are labelled application/json.
     */
    @Bean
    public OpenApiCustomizer errorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Content errorBody = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();
                responses.computeIfAbsent("400", code -> new ApiResponse().description("Invalid request or parameters"));
                boolean needsToken = operation.getSecurity() == null || !operation.getSecurity().isEmpty();
                if (needsToken) {
                    responses.computeIfAbsent("401", code -> new ApiResponse().description("Missing, invalid or expired token"));
                    responses.computeIfAbsent("403", code -> new ApiResponse().description("Not allowed for this user"));
                }
                responses.forEach((code, response) -> {
                    if (code.startsWith("4")) {
                        response.setContent(errorBody);
                    } else if (response.getContent() != null && response.getContent().containsKey("*/*")) {
                        // controllers don't declare `produces`, but every body is JSON
                        response.getContent().put("application/json", response.getContent().remove("*/*"));
                    }
                });
            }));
        };
    }
}
