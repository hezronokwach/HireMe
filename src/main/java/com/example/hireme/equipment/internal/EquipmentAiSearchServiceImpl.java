package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiPromptTemplate;
import com.example.hireme.ai.AiRateLimitExceededException;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.equipment.AiSearchRequest;
import com.example.hireme.equipment.AiSearchResponse;
import com.example.hireme.equipment.AiSearchResponse.Interpretation;
import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentAiSearchService;
import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a sentence into equipment filters with the model, then runs the normal search with them.
 * The model only fills in filters, never queries the database, and everything it returns is checked:
 * unknown categories, unparsable or past dates, backwards ranges and negative rates are dropped or
 * corrected, so a bad answer can't cause an error. Without AI, the query's words are searched instead.
 */
@Service
public class EquipmentAiSearchServiceImpl implements EquipmentAiSearchService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentAiSearchServiceImpl.class);

    static final String UNAVAILABLE_NOTICE = "AI search is unavailable right now, so these results match the words in your search.";
    static final String RATE_LIMITED_NOTICE = "You've made a lot of AI searches in the last minute, so these results match the words in your search.";

    private static final AiPromptTemplate SYSTEM_PROMPT = AiPromptTemplate.fromClasspath("prompts/search-equipment.st");
    private static final String CATEGORIES = Arrays.stream(Category.values()).map(Enum::name).collect(Collectors.joining(", "));

    // The JSON the model is asked for. Dates and category are strings so a bad value can be dropped instead of failing.
    public record ModelFilters(String category, String location, String availableFrom, String availableTo,
                               Integer minRate, Integer maxRate, String keywords) {
    }

    private final AiAssistant aiAssistant;
    private final EquipmentService equipmentService;
    private final Clock clock;

    public EquipmentAiSearchServiceImpl(AiAssistant aiAssistant, EquipmentService equipmentService, Clock clock) {
        this.aiAssistant = aiAssistant;
        this.equipmentService = equipmentService;
        this.clock = clock;
    }

    @Override
    public AiSearchResponse search(AiSearchRequest request, Pageable pageable, Long currentUserId) {
        String query = request.query().strip();
        LocalDate today = LocalDate.now(clock);
        EquipmentSearchCriteria filters;
        Interpretation interpretedBy = Interpretation.AI;
        String notice = null;
        try {
            String systemPrompt = SYSTEM_PROMPT.render(Map.of(
                    "today", today.toString(),
                    "dayOfWeek", today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    "categories", CATEGORIES));
            filters = toCriteria(aiAssistant.generate(currentUserId, systemPrompt, query, ModelFilters.class), today);
        } catch (AiUnavailableException | AiRateLimitExceededException e) {
            log.info("AI search fell back to keywords: {}", e.getMessage());
            filters = EquipmentSearchCriteria.keywordsOnly(query);
            interpretedBy = Interpretation.KEYWORDS;
            notice = e instanceof AiRateLimitExceededException ? RATE_LIMITED_NOTICE : UNAVAILABLE_NOTICE;
        }
        return new AiSearchResponse(query, filters, interpretedBy, notice, equipmentService.search(filters, pageable));
    }

    EquipmentSearchCriteria toCriteria(ModelFilters model, LocalDate today) {
        if (model == null) {
            return EquipmentSearchCriteria.none();
        }
        Category category = parseCategory(model.category());

        LocalDate from = parseDate(model.availableFrom());
        LocalDate to = parseDate(model.availableTo());
        if (from == null) {
            from = to;
        }
        if (to == null) {
            to = from;
        }
        if (from != null) {
            if (to.isBefore(from)) {
                LocalDate swap = from;
                from = to;
                to = swap;
            }
            if (from.isBefore(today)) {
                from = today; // only the days still to come can be booked
            }
            if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= EquipmentServiceImpl.MAX_AVAILABILITY_RANGE_DAYS) {
                from = null;
                to = null;
            }
        }

        BigDecimal minRate = positive(model.minRate());
        BigDecimal maxRate = positive(model.maxRate());
        if (minRate != null && maxRate != null && minRate.compareTo(maxRate) > 0) {
            minRate = null;
            maxRate = null;
        }

        // With a category the keywords would only narrow it further (a listing named "MF 375" never says
        // "tractor"), so they're used only when the category is unknown.
        String keywords = category == null ? clip(model.keywords(), 50) : null;
        return new EquipmentSearchCriteria(category, clip(model.location(), 100), minRate, maxRate, keywords, from, to);
    }

    private static Category parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Category.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal positive(Integer value) {
        return value == null || value <= 0 ? null : BigDecimal.valueOf(value);
    }

    private static String clip(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength).strip();
    }
}
