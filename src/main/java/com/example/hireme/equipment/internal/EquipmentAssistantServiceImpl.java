package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiPromptTemplate;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.ai.ChatTurn;
import com.example.hireme.equipment.AssistantChatRequest;
import com.example.hireme.equipment.AssistantChatRequest.Message;
import com.example.hireme.equipment.AssistantChatRequest.Role;
import com.example.hireme.equipment.AssistantChatResponse;
import com.example.hireme.equipment.AssistantChatResponse.ListingCard;
import com.example.hireme.equipment.AssistantChatResponse.Quote;
import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentAssistantService;
import com.example.hireme.equipment.EquipmentBookingChecker;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.shared.exception.InvalidRequestException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The conversational hire assistant. The model gets the conversation and read-only tools over the real
 * listings (search, details, dates and price), and answers in its own words. What the client is shown as
 * listings is never the model's text: the cards are built here from the database, only for listings the
 * tools returned (in this request, or shown earlier in the conversation), with prices worked out here.
 */
@Service
public class EquipmentAssistantServiceImpl implements EquipmentAssistantService {

    static final int MAX_TURNS = 12;     // only the latest turns go to the model
    static final int MAX_CARDS = 3;
    static final int MAX_SUGGESTIONS = 3;
    static final int MAX_REPLY_LENGTH = 2000;

    private static final AiPromptTemplate SYSTEM_PROMPT = AiPromptTemplate.fromClasspath("prompts/hire-assistant.st");
    private static final String CATEGORIES = Arrays.stream(Category.values()).map(Enum::name).collect(Collectors.joining(", "));

    // The answer the model gives through the reply tool. Dates are strings so a bad value is dropped instead of failing.
    public record ModelReply(String reply, List<Long> listingIds, String from, String to, List<String> suggestions) {
    }

    private final AiAssistant aiAssistant;
    private final EquipmentService equipmentService;
    private final EquipmentBookingChecker bookingChecker;
    private final EquipmentAiSearchServiceImpl filters;
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public EquipmentAssistantServiceImpl(AiAssistant aiAssistant, EquipmentService equipmentService,
                                         EquipmentBookingChecker bookingChecker, EquipmentAiSearchServiceImpl filters,
                                         Clock clock, JsonMapper jsonMapper) {
        this.aiAssistant = aiAssistant;
        this.equipmentService = equipmentService;
        this.bookingChecker = bookingChecker;
        this.filters = filters;
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public AssistantChatResponse chat(AssistantChatRequest request, Long currentUserId) {
        List<Message> messages = request.messages();
        if (messages.getLast().role() != Role.USER) {
            throw new InvalidRequestException("The last message must be the user's");
        }
        if (!aiAssistant.isEnabled()) {
            throw new AiUnavailableException();
        }
        LocalDate today = LocalDate.now(clock);
        List<Message> recent = messages.subList(Math.max(0, messages.size() - MAX_TURNS), messages.size());

        // Listings shown earlier in the conversation, looked up again so the model and the cards see real data.
        Map<Long, EquipmentResponse> shownBefore = new LinkedHashMap<>();
        List<ChatTurn> turns = new ArrayList<>();
        for (Message message : recent) {
            if (message.role() == Role.USER) {
                turns.add(ChatTurn.user(message.text().strip()));
            } else {
                turns.add(ChatTurn.assistant(message.text().strip()
                        + shownLine(message.listingIds(), message.from(), message.to(), shownBefore)));
            }
        }

        AssistantTools tools = new AssistantTools(equipmentService, bookingChecker, filters, today);
        String systemPrompt = SYSTEM_PROMPT.render(Map.of(
                "today", today.toString(),
                "dayOfWeek", today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                "calendar", calendar(today),
                "categories", CATEGORIES));
        String text = aiAssistant.converse(currentUserId, systemPrompt, turns, tools);
        ModelReply answer = tools.finalReply();
        if (answer == null || answer.reply() == null || answer.reply().isBlank()) {
            // It answered in plain text instead of calling the reply tool: still usable.
            answer = fromText(text);
            if (answer.listingIds().isEmpty()) {
                Map<Long, EquipmentResponse> known = new LinkedHashMap<>(shownBefore);
                known.putAll(tools.seen());
                answer = new ModelReply(answer.reply(), mentioned(answer.reply(), known), answer.from(), answer.to(),
                        answer.suggestions());
            }
        }
        if (answer.from() == null && answer.to() == null && tools.lastDays() != null) {
            // The reply left the days out, but the search or check behind it used some: those are the days.
            answer = new ModelReply(answer.reply(), answer.listingIds(), tools.lastDays()[0].toString(),
                    tools.lastDays()[1].toString(), answer.suggestions());
        }
        if (answer.from() == null && answer.to() == null) {
            // No days in this answer: keep the ones the conversation was about (e.g. "does it come with a driver?").
            Message earlier = recent.reversed().stream()
                    .filter(m -> m.role() == Role.ASSISTANT && m.from() != null).findFirst().orElse(null);
            if (earlier != null) {
                answer = new ModelReply(answer.reply(), answer.listingIds(), earlier.from().toString(),
                        (earlier.to() == null ? earlier.from() : earlier.to()).toString(), answer.suggestions());
            }
        }
        return toResponse(answer, tools, shownBefore, today);
    }

    AssistantChatResponse toResponse(ModelReply answer, AssistantTools tools, Map<Long, EquipmentResponse> shownBefore,
                                     LocalDate today) {
        LocalDate[] dates = dates(answer.from(), answer.to(), today);
        Map<Long, EquipmentResponse> known = new LinkedHashMap<>(shownBefore);
        known.putAll(tools.seen());

        List<ListingCard> cards = (answer.listingIds() == null ? List.<Long>of() : answer.listingIds()).stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(known::get)          // ids the tools never returned are dropped: no invented listings
                .filter(Objects::nonNull)
                .limit(MAX_CARDS)
                .map(listing -> card(listing, dates == null ? null : tools.quote(listing, dates[0], dates[1])))
                .toList();

        List<String> suggestions = (answer.suggestions() == null ? List.<String>of() : answer.suggestions()).stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> clip(s.strip(), 80))
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();

        return new AssistantChatResponse(clip(plain(answer.reply()), MAX_REPLY_LENGTH), cards,
                dates == null ? null : dates[0], dates == null ? null : dates[1], suggestions);
    }

    /**
     * The days people mention most, worked out here: models get day arithmetic wrong (one called Thursday
     * and Friday "next weekend").
     */
    static String calendar(LocalDate today) {
        LocalDate saturday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY));
        if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            saturday = today.minusDays(1); // on Sunday, "this weekend" is today
        }
        LocalDate thisWeekendStart = today.isAfter(saturday) ? today : saturday;
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        return "tomorrow is " + day(today.plusDays(1))
                + "; this weekend is " + day(thisWeekendStart) + " to " + day(saturday.plusDays(1))
                + "; next weekend is " + day(saturday.plusDays(7)) + " to " + day(saturday.plusDays(8))
                + "; next week is " + day(monday) + " to " + day(monday.plusDays(6));
    }

    private static String day(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + date;
    }

    /**
     * The model's plain-text answer when it didn't call the reply tool. Sometimes that text is the reply
     * tool's arguments written out as JSON; then they are used, otherwise the text is the reply, without cards.
     */
    ModelReply fromText(String text) {
        if (text == null || text.isBlank()) {
            throw new AiUnavailableException();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                Map<?, ?> json = jsonMapper.readValue(text.substring(start, end + 1), Map.class);
                Object message = json.containsKey("message") ? json.get("message") : json.get("reply");
                if (message instanceof String m && !m.isBlank()) {
                    return new ModelReply(m, longs(json.get("listingIds")), string(json.get("from")),
                            string(json.get("to")), strings(json.get("suggestions")));
                }
            } catch (RuntimeException e) {
                // not JSON after all
            }
            String before = text.substring(0, start).strip();
            if (!before.isBlank()) {
                return fromNote(before);
            }
        }
        return fromNote(text);
    }

    private static final java.util.regex.Pattern LISTING_NOTE =
            java.util.regex.Pattern.compile("\\(\\s*listings?(?: recommended| shown)?\\s*:([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("#?(\\d+)");

    // Plain text that copies our "(Listings shown: ...)" note to name its picks: the ids are used, the note removed.
    static ModelReply fromNote(String text) {
        java.util.regex.Matcher note = LISTING_NOTE.matcher(text);
        if (!note.find()) {
            return new ModelReply(text, List.of(), null, null, List.of());
        }
        List<Long> ids = new ArrayList<>();
        for (String part : note.group(1).split("[;,]")) {
            java.util.regex.Matcher id = ID.matcher(part.strip());
            if (id.lookingAt()) {
                ids.add(Long.parseLong(id.group(1)));
            }
        }
        String reply = (text.substring(0, note.start()) + text.substring(note.end())).strip();
        return new ModelReply(reply, ids, null, null, List.of());
    }

    /**
     * Listings a plain-text answer talks about, in the order it mentions them: by their full name, or as
     * "listing 16", "#16" or "id 16". Only listings looked up in this conversation count.
     */
    static List<Long> mentioned(String text, Map<Long, EquipmentResponse> known) {
        String lower = text.toLowerCase(Locale.ROOT);
        Map<Integer, Long> byPosition = new java.util.TreeMap<>();
        for (EquipmentResponse listing : known.values()) {
            int at = -1;
            for (String form : nameForms(listing.name())) {
                int found = lower.indexOf(form);
                if (found >= 0 && (at < 0 || found < at)) {
                    at = found;
                }
            }
            java.util.regex.Matcher byId = java.util.regex.Pattern
                    .compile("(?:listing|id|#)\\s*#?" + listing.id() + "\\b").matcher(lower);
            if (byId.find() && (at < 0 || byId.start() < at)) {
                at = byId.start();
            }
            if (at >= 0) {
                byPosition.putIfAbsent(at, listing.id());
            }
        }
        return List.copyOf(byPosition.values());
    }

    /**
     * How an answer may refer to a listing: its full name, or a model number with the word before it
     * ("Massey Ferguson 290 Tractor – Nakuru" is also "ferguson 290"), as models shorten names.
     */
    static List<String> nameForms(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        List<String> forms = new ArrayList<>(List.of(lower));
        String[] words = lower.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        for (int i = 1; i < words.length; i++) {
            if (words[i].matches(".*\\d.*") && words[i - 1].length() > 2) {
                forms.add(words[i - 1] + " " + words[i]);
            }
        }
        return forms;
    }

    // Removes markdown and leftovers of tool syntax the model sometimes writes; the reply is shown as plain text.
    static String plain(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("(?m)^\\s*(?:\\(reply\\)|reply:?|```\\w*)\\s*$", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "- ")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static List<Long> longs(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(n -> ((Number) n).longValue()).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static String string(Object value) {
        return value instanceof String s ? s : null;
    }

    private String shownLine(List<Long> ids, LocalDate from, LocalDate to, Map<Long, EquipmentResponse> shownBefore) {
        String days = from == null ? "" : "\n(Days discussed: " + from + " to " + (to == null ? from : to) + ")";
        if (ids == null || ids.isEmpty()) {
            return days;
        }
        List<String> shown = new ArrayList<>();
        int position = 1;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().limit(10).toList()) {
            EquipmentResponse listing = lookUp(id);
            if (listing != null) {
                shownBefore.put(id, listing);
                shown.add(position++ + ". #" + id + " " + listing.name() + ", " + listing.location()
                        + ", KES " + listing.dailyRateKes().stripTrailingZeros().toPlainString() + "/day"
                        + snippet(listing.description()));
            }
        }
        return (shown.isEmpty() ? "" : "\n(Listings shown: " + String.join("; ", shown) + ")") + days;
    }

    // The start of a description, so follow-up questions about it are answered from the listing, not memory.
    private static String snippet(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String clean = description.strip().replaceAll("\\s+", " ");
        return ", \"" + (clean.length() <= 100 ? clean : clean.substring(0, 100) + "…") + "\"";
    }

    private EquipmentResponse lookUp(Long id) {
        try {
            return equipmentService.getById(id);
        } catch (RuntimeException e) {
            return null; // deleted since: just leave it out
        }
    }

    private static ListingCard card(EquipmentResponse e, Quote quote) {
        return new ListingCard(e.id(), e.name(), e.category(), e.location(), e.dailyRateKes(),
                e.equipmentStatus() == EquipmentStatus.AVAILABLE, quote);
    }

    // Both days as a valid, future range of at most a year, or null.
    private static LocalDate[] dates(String from, String to, LocalDate today) {
        LocalDate start = parse(from);
        LocalDate end = parse(to);
        if (start == null) start = end;
        if (end == null) end = start;
        if (start == null) {
            return null;
        }
        if (end.isBefore(start)) {
            end = start; // a slip like "to": "2026-10-12" for a day in December: take it as that one day
        }
        if (start.isBefore(today) || ChronoUnit.DAYS.between(start, end) >= EquipmentServiceImpl.MAX_AVAILABILITY_RANGE_DAYS) {
            return null;
        }
        return new LocalDate[]{start, end};
    }

    private static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1).strip() + "…";
    }
}
