package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.AssistantChatResponse.Quote;
import com.example.hireme.equipment.EquipmentBookingChecker;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentSearchCriteria;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.equipment.internal.EquipmentAiSearchServiceImpl.ModelFilters;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the hire assistant may do with HireMe's data, for one chat request. Everything is read-only, and
 * every listing the tools return is remembered, so the reply can only show cards for listings that really
 * came from the database in this request.
 * <p>
 * Groq's free tier allows few tokens per minute, and every tool round trip sends the whole prompt again,
 * so the tools are built to need few calls: a search with days already gives the price for those days,
 * and checkDates takes several listings at once.
 */
class AssistantTools {

    static final int MAX_RESULTS = 6;
    private static final int DESCRIPTION_LENGTH = 200;

    // What the model sees about a listing. totalKes: the price for the days searched (all results are free then).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ListingInfo(Long id, String name, String category, String location, BigDecimal dailyRateKes,
                       boolean takingBookings, String description, BigDecimal totalKes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SearchResult(String note, List<ListingInfo> listings) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DatesResult(String note, LocalDate from, LocalDate to, Long days, List<DatePrice> listings) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DatePrice(Long id, String name, BigDecimal totalKes, boolean free, String note) {
    }

    private final EquipmentService equipmentService;
    private final EquipmentBookingChecker bookingChecker;
    private final EquipmentAiSearchServiceImpl filters;
    private final LocalDate today;

    private final Map<Long, EquipmentResponse> seen = new LinkedHashMap<>();
    private final Map<Long, Quote> quotes = new LinkedHashMap<>();
    private EquipmentAssistantServiceImpl.ModelReply reply;
    private LocalDate[] lastDays; // the days the last search or date check used

    AssistantTools(EquipmentService equipmentService, EquipmentBookingChecker bookingChecker,
                   EquipmentAiSearchServiceImpl filters, LocalDate today) {
        this.equipmentService = equipmentService;
        this.bookingChecker = bookingChecker;
        this.filters = filters;
        this.today = today;
    }

    @Tool(description = "Search listings, cheapest first. Filters are optional. With days, only listings free on all of "
            + "them come back, each with totalKes for those days.")
    public SearchResult searchListings(
            @ToolParam(required = false, description = "HEAVY_MACHINERY, AGRICULTURAL or EVENT_SETUP") String category,
            @ToolParam(required = false, description = "Town or area, e.g. Nakuru") String location,
            @ToolParam(required = false, description = "Lowest daily rate in KES") Integer minRate,
            @ToolParam(required = false, description = "Highest daily rate in KES") Integer maxRate,
            @ToolParam(required = false, description = "A word or two for the kind of equipment, e.g. tractor") String keywords,
            @ToolParam(required = false, description = "First day needed, YYYY-MM-DD") String availableFrom,
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
        LocalDate from = withWords.availableFrom();
        LocalDate to = withWords.availableTo();
        if (from != null) {
            lastDays = new LocalDate[]{from, to};
        }
        return new SearchResult(note, found.stream()
                .map(e -> from == null ? remember(e) : withPrice(remember(e), quoteAndKeep(e, from, to)))
                .toList());
    }

    @Tool(description = "Details of listings by id, including what their descriptions say is included.")
    public SearchResult getListings(@ToolParam(description = "Listing ids") List<Long> listingIds) {
        List<ListingInfo> found = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        List<Long> ids = listingIds == null ? List.of() : listingIds.stream().distinct().limit(MAX_RESULTS).toList();
        for (Long id : ids) {
            try {
                found.add(remember(equipmentService.getById(id)));
            } catch (RuntimeException e) {
                missing.add(id);
            }
        }
        return new SearchResult(missing.isEmpty() ? null : "No listing with id " + missing + ".", found);
    }

    @Tool(description = "Whether listings are free on the given days, and the total for each "
            + "(daily rate × days, both the first and last day counted).")
    public DatesResult checkDates(
            @ToolParam(description = "Listing ids") List<Long> listingIds,
            @ToolParam(description = "First day, YYYY-MM-DD") String from,
            @ToolParam(description = "Last day, YYYY-MM-DD; the same as from for one day") String to) {
        LocalDate start = parse(from);
        LocalDate end = parse(to);
        if (start == null || end == null) {
            return problem("Give both days as YYYY-MM-DD.");
        }
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (start.isBefore(today)) {
            return problem("Those days are in the past; today is " + today + ".");
        }
        if (ChronoUnit.DAYS.between(start, end) >= EquipmentServiceImpl.MAX_AVAILABILITY_RANGE_DAYS) {
            return problem("That is more than a year; ask for a shorter period.");
        }
        lastDays = new LocalDate[]{start, end};
        List<DatePrice> prices = new ArrayList<>();
        List<Long> ids = listingIds == null ? List.of() : listingIds.stream().distinct().limit(MAX_RESULTS).toList();
        for (Long id : ids) {
            EquipmentResponse listing;
            try {
                listing = equipmentService.getById(id);
            } catch (RuntimeException e) {
                prices.add(new DatePrice(id, null, null, false, "There is no listing " + id + "."));
                continue;
            }
            remember(listing);
            Quote quote = quoteAndKeep(listing, start, end);
            prices.add(new DatePrice(listing.id(), listing.name(), quote.totalKes(), quote.free(),
                    listing.equipmentStatus() == EquipmentStatus.AVAILABLE ? null : "Not taking bookings right now."));
        }
        return new DatesResult(null, start, end, ChronoUnit.DAYS.between(start, end) + 1, prices);
    }

    @Tool(name = "reply", returnDirect = true,
            description = "Send your answer to the user. Always finish by calling this exactly once.")
    public String reply(
            @ToolParam(description = "Your message: two to five friendly sentences of plain text") String message,
            @ToolParam(required = false, description = "Ids of the listings you recommend, best first") List<Long> listingIds,
            @ToolParam(required = false, description = "First day being discussed, YYYY-MM-DD") String from,
            @ToolParam(required = false, description = "Last day being discussed, YYYY-MM-DD") String to,
            @ToolParam(required = false, description = "Up to 3 short next messages the user might send") List<String> suggestions) {
        this.reply = new EquipmentAssistantServiceImpl.ModelReply(message, listingIds, from, to, suggestions);
        return message == null ? "" : message;
    }

    /** The answer the model gave through the reply tool, or null if it never called it. */
    EquipmentAssistantServiceImpl.ModelReply finalReply() {
        return reply;
    }

    /** The days the last search or date check in this request used, or null. */
    LocalDate[] lastDays() {
        return lastDays;
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

    private Quote quoteAndKeep(EquipmentResponse listing, LocalDate from, LocalDate to) {
        Quote quote = quote(listing, from, to);
        quotes.put(listing.id(), quote);
        return quote;
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
                e.equipmentStatus() == EquipmentStatus.AVAILABLE, description, null);
    }

    private static ListingInfo withPrice(ListingInfo info, Quote quote) {
        return new ListingInfo(info.id(), info.name(), info.category(), info.location(), info.dailyRateKes(),
                info.takingBookings(), info.description(), quote.totalKes());
    }

    private static DatesResult problem(String note) {
        return new DatesResult(note, null, null, null, List.of());
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
