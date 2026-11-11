package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.AssistantChatResponse.Quote;
import com.example.hireme.equipment.EquipmentBookingChecker;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.equipment.internal.EquipmentAiSearchServiceImpl.ModelFilters;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the hire assistant may do with HireMe's data, for one chat request. Everything is read-only, and
 * every listing the tools return is remembered, so the reply can only show cards for listings that really
 * came from the database in this request.
 */
class AssistantTools {

    static final int MAX_RESULTS = 6;
    private static final int DESCRIPTION_LENGTH = 200;

    // What the model sees about a listing.
    record ListingInfo(Long id, String name, String category, String location, BigDecimal dailyRateKes,
                       boolean takingBookings, String description) {
    }

    record SearchResult(String note, List<ListingInfo> listings) {
    }

    record DatesResult(String note, Long listingId, String name, LocalDate from, LocalDate to, Long days,
                       BigDecimal totalKes, Boolean takingBookings, Boolean free) {
    }

    private final EquipmentService equipmentService;
    private final EquipmentBookingChecker bookingChecker;
    private final EquipmentAiSearchServiceImpl filters;
    private final LocalDate today;

    private final Map<Long, EquipmentResponse> seen = new LinkedHashMap<>();
    private final Map<Long, Quote> quotes = new LinkedHashMap<>();
    private EquipmentAssistantServiceImpl.ModelReply reply;

    AssistantTools(EquipmentService equipmentService, EquipmentBookingChecker bookingChecker,
                   EquipmentAiSearchServiceImpl filters, LocalDate today) {
        this.equipmentService = equipmentService;
        this.bookingChecker = bookingChecker;
        this.filters = filters;
        this.today = today;
    }

    @Tool(description = "Search HireMe's listings. All filters are optional; leave out what the user didn't say. "
            + "Results are cheapest first, at most " + MAX_RESULTS + ".")
    public SearchResult searchListings(
            @ToolParam(required = false, description = "HEAVY_MACHINERY, AGRICULTURAL or EVENT_SETUP") String category,
            @ToolParam(required = false, description = "Town or area, e.g. Nakuru") String location,
            @ToolParam(required = false, description = "Lowest daily rate in KES") Integer minRate,
            @ToolParam(required = false, description = "Highest daily rate in KES") Integer maxRate,
            @ToolParam(required = false, description = "A word or two for the kind of equipment, e.g. tractor") String keywords,
            @ToolParam(required = false, description = "First day needed, YYYY-MM-DD: only listings free on every day") String availableFrom,
            @ToolParam(required = false, description = "Last day needed, YYYY-MM-DD") String availableTo) {
        EquipmentSearchCriteria cleaned = filters.toCriteria(
                new ModelFilters(category, location, availableFrom, availableTo, minRate, maxRate, keywords), today);
        String words = clip(keywords);
        // Unlike the one-shot search, keep the words with a category: the model reads the results and judges them.
        EquipmentSearchCriteria withWords = new EquipmentSearchCriteria(cleaned.category(), cleaned.location(),
                cleaned.minRate(), cleaned.maxRate(), words != null ? words : cleaned.keywords(),
                cleaned.availableFrom(), cleaned.availableTo());
        List<EquipmentResponse> found = search(withWords);
        String note = null;
        if (found.isEmpty() && withWords.keywords() != null && withWords.category() != null) {
            found = search(new EquipmentSearchCriteria(withWords.category(), withWords.location(), withWords.minRate(),
                    withWords.maxRate(), null, withWords.availableFrom(), withWords.availableTo()));
            note = "No listing mentions \"" + withWords.keywords() + "\"; these are all matching listings in the "
                    + "category. Read the descriptions and leave out the ones that don't fit.";
        }
        if (found.isEmpty()) {
            note = "Nothing matches these filters.";
        }
        return new SearchResult(note, found.stream().map(this::remember).toList());
    }

    @Tool(description = "Full details of one listing by its id.")
    public SearchResult getListing(@ToolParam(description = "The listing id") Long listingId) {
        try {
            return new SearchResult(null, List.of(remember(equipmentService.getById(listingId))));
        } catch (RuntimeException e) {
            return new SearchResult("There is no listing " + listingId + ".", List.of());
        }
    }

    @Tool(description = "Whether a listing is free to book on the given days, and the total price for them "
            + "(daily rate × days, both the first and last day counted).")
    public DatesResult checkDates(
            @ToolParam(description = "The listing id") Long listingId,
            @ToolParam(description = "First day, YYYY-MM-DD") String from,
            @ToolParam(description = "Last day, YYYY-MM-DD; the same as from for one day") String to) {
        LocalDate start = parse(from);
        LocalDate end = parse(to);
        if (start == null || end == null) {
            return problem(listingId, "Give both days as YYYY-MM-DD.");
        }
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (start.isBefore(today)) {
            return problem(listingId, "Those days are in the past; today is " + today + ".");
        }
        if (ChronoUnit.DAYS.between(start, end) >= EquipmentServiceImpl.MAX_AVAILABILITY_RANGE_DAYS) {
            return problem(listingId, "That is more than a year; ask for a shorter period.");
        }
        EquipmentResponse listing;
        try {
            listing = equipmentService.getById(listingId);
        } catch (RuntimeException e) {
            return problem(listingId, "There is no listing " + listingId + ".");
        }
        remember(listing);
        Quote quote = quote(listing, start, end);
        quotes.put(listing.id(), quote);
        return new DatesResult(null, listing.id(), listing.name(), start, end, quote.days(), quote.totalKes(),
                listing.equipmentStatus() == EquipmentStatus.AVAILABLE, quote.free());
    }

    @Tool(name = "reply", returnDirect = true,
            description = "Send your answer to the user. Always finish by calling this exactly once.")
    public String reply(
            @ToolParam(description = "Your message to the user: two to five friendly sentences of plain text") String message,
            @ToolParam(required = false, description = "Ids of the listings you recommend in this message, best first") List<Long> listingIds,
            @ToolParam(required = false, description = "First day being discussed, YYYY-MM-DD") String from,
            @ToolParam(required = false, description = "Last day being discussed, YYYY-MM-DD") String to,
            @ToolParam(required = false, description = "Up to 3 short follow-ups the user might send next, written as the user") List<String> suggestions) {
        this.reply = new EquipmentAssistantServiceImpl.ModelReply(message, listingIds, from, to, suggestions);
        return message == null ? "" : message;
    }

    /** The answer the model gave through the reply tool, or null if it never called it. */
    EquipmentAssistantServiceImpl.ModelReply finalReply() {
        return reply;
    }

    /** Listings the tools returned in this request, in the order they were first seen. */
    Map<Long, EquipmentResponse> seen() {
        return seen;
    }

    /** The price and availability of a listing for the given days (computed here, never by the model). */
    Quote quote(EquipmentResponse listing, LocalDate from, LocalDate to) {
        Quote known = quotes.get(listing.id());
        if (known != null && known.from().equals(from) && known.to().equals(to)) {
            return known;
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        boolean free = listing.equipmentStatus() == EquipmentStatus.AVAILABLE
                && !bookingChecker.findBookedEquipmentIds(from, to).contains(listing.id());
        return new Quote(from, to, days, listing.dailyRateKes().multiply(BigDecimal.valueOf(days)), free);
    }

    private List<EquipmentResponse> search(EquipmentSearchCriteria criteria) {
        var page = PageRequest.of(0, MAX_RESULTS, Sort.by("dailyRateKes").and(Sort.by("id")));
        return equipmentService.search(criteria, page).content();
    }

    private ListingInfo remember(EquipmentResponse e) {
        seen.putIfAbsent(e.id(), e);
        String description = e.description() == null ? null
                : e.description().length() <= DESCRIPTION_LENGTH ? e.description()
                : e.description().substring(0, DESCRIPTION_LENGTH) + "…";
        return new ListingInfo(e.id(), e.name(), e.category().name(), e.location(), e.dailyRateKes(),
                e.equipmentStatus() == EquipmentStatus.AVAILABLE, description);
    }

    private static DatesResult problem(Long listingId, String note) {
        return new DatesResult(note, listingId, null, null, null, null, null, null, null);
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

    private static String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.strip();
        return clean.length() <= 50 ? clean : clean.substring(0, 50).strip();
    }
}
