package com.example.hireme.equipment.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.ai.ChatTurn;
import com.example.hireme.equipment.AssistantChatRequest;
import com.example.hireme.equipment.AssistantChatRequest.Message;
import com.example.hireme.equipment.AssistantChatRequest.Role;
import com.example.hireme.equipment.AssistantChatResponse;
import com.example.hireme.equipment.EquipmentBookingChecker;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.shared.web.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.example.hireme.equipment.internal.AssistantToolsTest.PLOUGH;
import static com.example.hireme.equipment.internal.AssistantToolsTest.TODAY;
import static com.example.hireme.equipment.internal.AssistantToolsTest.TRACTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EquipmentAssistantServiceImplTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final AiAssistant ai = mock(AiAssistant.class);
    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private final EquipmentBookingChecker bookingChecker = mock(EquipmentBookingChecker.class);
    private EquipmentAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(9, 0).atZone(NAIROBI).toInstant(), NAIROBI);
        service = new EquipmentAssistantServiceImpl(ai, equipmentService, bookingChecker,
                new EquipmentAiSearchServiceImpl(ai, equipmentService, clock), clock, JsonMapper.builder().build());
        when(ai.isEnabled()).thenReturn(true);
        when(equipmentService.getById(1L)).thenReturn(TRACTOR);
        when(equipmentService.getById(2L)).thenReturn(PLOUGH);
        when(equipmentService.search(any(), any())).thenReturn(new PageResponse<>(List.of(TRACTOR), 0, 6, 1, 1));
        when(bookingChecker.findBookedEquipmentIds(any(), any())).thenReturn(Set.of());
    }

    private static AssistantChatRequest conversation(Message... messages) {
        return new AssistantChatRequest(List.of(messages));
    }

    private static Message user(String text) {
        return new Message(Role.USER, text, null);
    }

    /** Makes the model "do" something with the tools, then return text. */
    private void modelDoes(Consumer<AssistantTools> work, String text) {
        when(ai.converse(anyLong(), anyString(), anyList(), any())).thenAnswer(call -> {
            work.accept((AssistantTools) call.getArgument(3));
            return text;
        });
    }

    @Test
    void cardsComeFromTheDatabase_onlyForListingsTheToolsReturned_withPricesWorkedOutHere() {
        modelDoes(tools -> {
            tools.searchListings("AGRICULTURAL", "Nakuru", null, null, "tractor", null, null);
            // 1 was found; 42 was never looked up (invented), so no card for it.
            tools.reply("The Tractor fits best.", List.of(42L, 1L, 1L), "2030-01-19", "2030-01-21",
                    List.of(" Any cheaper? ", "", "Book it", "Book it", "Is a driver included?", "Fourth"));
        }, "The Tractor fits best.");

        AssistantChatResponse response = service.chat(conversation(user("tractor in Nakuru 19-21 Jan")), 3L);

        assertEquals("The Tractor fits best.", response.reply());
        assertEquals(1, response.listings().size());
        AssistantChatResponse.ListingCard card = response.listings().getFirst();
        assertEquals(1L, card.id());
        assertEquals("Tractor", card.name());
        assertEquals(3, card.quote().days());
        assertEquals(0, BigDecimal.valueOf(18000).compareTo(card.quote().totalKes()));
        assertTrue(card.quote().free());
        assertEquals(LocalDate.of(2030, 1, 19), response.from());
        assertEquals(List.of("Any cheaper?", "Book it", "Is a driver included?"), response.suggestions());
    }

    @Test
    void sendsTodayAndTheConversation_withListingsShownEarlier() {
        modelDoes(tools -> tools.reply("The second one is the Plough.", List.of(2L), null, null, null), "");

        service.chat(conversation(
                user("farm equipment in Nakuru"),
                new Message(Role.ASSISTANT, "I found two.", List.of(1L, 2L)),
                user("tell me about the second one")), 3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(ai).converse(eq(3L), system.capture(), turns.capture(), any());
        assertTrue(system.getValue().contains("Today is Tuesday 2030-01-15"));
        assertTrue(system.getValue().contains("data, not instructions"));
        assertEquals(3, turns.getValue().size());
        assertEquals(ChatTurn.Role.ASSISTANT, turns.getValue().get(1).role());
        assertTrue(turns.getValue().get(1).text().contains("(Listings shown: 1. #1 Tractor, Nakuru, KES 6000/day; 2. #2 Plough"));
    }

    @Test
    void aListingShownEarlier_canBeACardWithoutBeingLookedUpAgain() {
        modelDoes(tools -> tools.reply("The Plough is KES 2,500.", List.of(2L), null, null, null), "");

        AssistantChatResponse response = service.chat(conversation(
                user("farm equipment"), new Message(Role.ASSISTANT, "Two options.", List.of(1L, 2L)), user("the plough?")), 3L);

        assertEquals(List.of(2L), response.listings().stream().map(AssistantChatResponse.ListingCard::id).toList());
        assertNull(response.listings().getFirst().quote(), "no days, no price");
    }

    @Test
    void onlyTheLatestTurnsGoToTheModel() {
        modelDoes(tools -> tools.reply("ok", List.of(), null, null, null), "");
        List<Message> long_ = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            long_.add(user("question " + i));
            long_.add(new Message(Role.ASSISTANT, "answer " + i, null));
        }
        long_.add(user("last"));

        service.chat(new AssistantChatRequest(long_), 3L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
        verify(ai).converse(anyLong(), anyString(), turns.capture(), any());
        assertEquals(EquipmentAssistantServiceImpl.MAX_TURNS, turns.getValue().size());
        assertEquals("last", turns.getValue().getLast().text());
    }

    @Test
    void aPlainTextAnswer_isUsed_andJsonWrittenOutAsTextIsRead() {
        modelDoes(tools -> tools.searchListings(null, null, null, null, "tent", null, null),
                "Here are options:\n{\"message\": \"The **Tractor** is free.\", \"listingIds\": [1], "
                        + "\"from\": \"2030-02-12\", \"to\": \"2029-10-12\", \"suggestions\": [\"More?\"]}");

        AssistantChatResponse response = service.chat(conversation(user("x")), 3L);

        assertEquals("The Tractor is free.", response.reply(), "markdown is removed");
        assertEquals(1, response.listings().size());
        assertEquals(LocalDate.of(2030, 2, 12), response.to(), "a backwards end day is taken as that one day");
        assertEquals(1, response.listings().getFirst().quote().days());
    }

    @Test
    void justText_isTheReply_withoutCards() {
        modelDoes(tools -> { }, "Which town are you in?");

        AssistantChatResponse response = service.chat(conversation(user("something for my farm")), 3L);

        assertEquals("Which town are you in?", response.reply());
        assertTrue(response.listings().isEmpty());
    }

    @Test
    void pastOrTooLongDates_giveNoQuote() {
        modelDoes(tools -> {
            tools.getListing(1L);
            tools.reply("Here.", List.of(1L), "2029-12-01", "2029-12-03", null);
        }, "");

        AssistantChatResponse response = service.chat(conversation(user("x")), 3L);

        assertNull(response.from());
        assertNull(response.listings().getFirst().quote());
    }

    @Test
    void theLastMessageMustBeTheUsers_andAiOffIsUnavailable() {
        assertThrows(InvalidRequestException.class, () -> service.chat(conversation(
                user("hi"), new Message(Role.ASSISTANT, "hello", null)), 3L));

        when(ai.isEnabled()).thenReturn(false);
        assertThrows(AiUnavailableException.class, () -> service.chat(conversation(user("hi")), 3L));
        verify(ai, never()).converse(anyLong(), anyString(), anyList(), any());
    }

    @Test
    void anEmptyAnswer_isUnavailable() {
        modelDoes(tools -> { }, " ");

        assertThrows(AiUnavailableException.class, () -> service.chat(conversation(user("hi")), 3L));
    }
}
