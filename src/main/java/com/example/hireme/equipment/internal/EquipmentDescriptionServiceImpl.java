package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiPromptTemplate;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.equipment.DescribeEquipmentRequest;
import com.example.hireme.equipment.EquipmentDescriptionResponse;
import com.example.hireme.equipment.EquipmentDescriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Drafts a title and description for a listing, and suggests a daily rate from similar listings.
 * The model's answer is checked before it reaches the owner: blank text is rejected, overly long text
 * is cut to fit the listing, and a rate far from the comparable listings is dropped.
 */
@Service
@Transactional(readOnly = true)
public class EquipmentDescriptionServiceImpl implements EquipmentDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentDescriptionServiceImpl.class);

    static final int TITLE_MAX_LENGTH = 80;
    static final int DESCRIPTION_MAX_LENGTH = 600; // the column allows 1000; keep drafts readable
    static final int MIN_LOCAL_COMPARABLES = 3;     // below this, widen to the whole category
    static final int MAX_COMPARABLES = 20;

    private static final AiPromptTemplate SYSTEM_PROMPT = AiPromptTemplate.fromClasspath("prompts/describe-equipment.st");
    private static final AiPromptTemplate REQUEST_PROMPT = AiPromptTemplate.fromClasspath("prompts/describe-equipment-request.st");

    // The JSON the model is asked for (Spring AI adds its schema to the prompt).
    public record ModelDraft(String title, String description, Integer suggestedDailyRateKes, String rateReason) {
    }

    private final AiAssistant aiAssistant;
    private final EquipmentRepository equipmentRepository;

    public EquipmentDescriptionServiceImpl(AiAssistant aiAssistant, EquipmentRepository equipmentRepository) {
        this.aiAssistant = aiAssistant;
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public EquipmentDescriptionResponse describe(DescribeEquipmentRequest request, Long currentUserId) {
        List<EquipmentEntity> comparables = findComparables(request);
        String systemPrompt = SYSTEM_PROMPT.render(Map.of(
                "titleMaxLength", TITLE_MAX_LENGTH,
                "descriptionMaxLength", DESCRIPTION_MAX_LENGTH));
        String userPrompt = REQUEST_PROMPT.render(Map.of(
                "name", request.name().trim(),
                "category", request.category().name(),
                "location", orNotGiven(request.location()),
                "notes", orNotGiven(request.notes()),
                "comparables", describeComparables(comparables)));

        ModelDraft draft = aiAssistant.generate(currentUserId, systemPrompt, userPrompt, ModelDraft.class);
        return toResponse(draft, comparables);
    }

    // Same category in (or near) the same location first; the whole category when there are too few.
    List<EquipmentEntity> findComparables(DescribeEquipmentRequest request) {
        Map<Long, EquipmentEntity> byId = new LinkedHashMap<>();
        String place = firstWord(request.location());
        if (place != null) {
            equipmentRepository.findTop20ByCategoryAndLocationContainingIgnoreCaseOrderByCreatedAtDesc(request.category(), place)
                    .forEach(e -> byId.put(e.getId(), e));
        }
        if (byId.size() < MIN_LOCAL_COMPARABLES) {
            equipmentRepository.findTop20ByCategoryOrderByCreatedAtDesc(request.category())
                    .forEach(e -> byId.putIfAbsent(e.getId(), e));
        }
        return byId.values().stream()
                .filter(e -> e.getDailyRateKes() != null && e.getDailyRateKes().signum() > 0)
                .limit(MAX_COMPARABLES)
                .toList();
    }

    EquipmentDescriptionResponse toResponse(ModelDraft draft, List<EquipmentEntity> comparables) {
        if (draft == null || isBlank(draft.title()) || isBlank(draft.description())) {
            log.warn("AI draft was missing a title or description");
            throw new AiUnavailableException();
        }
        String title = shorten(draft.title(), TITLE_MAX_LENGTH);
        String description = shorten(draft.description(), DESCRIPTION_MAX_LENGTH);

        BigDecimal rate = draft.suggestedDailyRateKes() == null ? null : BigDecimal.valueOf(draft.suggestedDailyRateKes());
        if (!isInLineWith(rate, comparables)) {
            if (rate != null) {
                log.info("Dropped AI rate suggestion {} as out of line with {} comparable listings", rate, comparables.size());
            }
            return new EquipmentDescriptionResponse(title, description, null, null, comparables.size());
        }
        String reason = isBlank(draft.rateReason()) ? null : shorten(draft.rateReason(), 200);
        return new EquipmentDescriptionResponse(title, description, rate, reason, comparables.size());
    }

    // A suggestion needs comparable listings, and must lie between half the cheapest and twice the dearest.
    private static boolean isInLineWith(BigDecimal rate, List<EquipmentEntity> comparables) {
        if (rate == null || rate.signum() <= 0 || comparables.isEmpty()) {
            return false;
        }
        BigDecimal cheapest = comparables.stream().map(EquipmentEntity::getDailyRateKes).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal dearest = comparables.stream().map(EquipmentEntity::getDailyRateKes).max(Comparator.naturalOrder()).orElseThrow();
        return rate.compareTo(cheapest.divide(BigDecimal.TWO)) >= 0 && rate.compareTo(dearest.multiply(BigDecimal.TWO)) <= 0;
    }

    private static String describeComparables(List<EquipmentEntity> comparables) {
        if (comparables.isEmpty()) {
            return "none";
        }
        return comparables.stream()
                .map(e -> "- " + e.getName() + " in " + e.getLocation() + ": KES " + e.getDailyRateKes().stripTrailingZeros().toPlainString() + " per day")
                .collect(Collectors.joining("\n"));
    }

    // Cuts at the last space before the limit, so words aren't split.
    static String shorten(String text, int maxLength) {
        String clean = text.strip().replaceAll("\\s+", " ");
        if (clean.length() <= maxLength) {
            return clean;
        }
        int cut = clean.lastIndexOf(' ', maxLength - 1);
        return (cut > maxLength / 2 ? clean.substring(0, cut) : clean.substring(0, maxLength - 1)).strip() + "…";
    }

    private static String firstWord(String location) {
        if (isBlank(location)) {
            return null;
        }
        List<String> words = new ArrayList<>(List.of(location.strip().split("[\\s,]+")));
        return words.isEmpty() || words.getFirst().length() < 3 ? null : words.getFirst();
    }

    private static String orNotGiven(String value) {
        return isBlank(value) ? "not given" : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
