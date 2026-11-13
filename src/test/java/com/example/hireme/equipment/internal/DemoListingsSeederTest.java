package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.CreateEquipmentRequest;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.shared.web.PageResponse;
import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserResponse;
import com.example.hireme.user.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DemoListingsSeederTest {

    private final UserService userService = mock(UserService.class);
    private final EquipmentService equipmentService = mock(EquipmentService.class);

    {
        // A new deployment: the demo owner has no listings yet.
        when(equipmentService.getByOwner(any(), any())).thenReturn(page(List.of()));
    }

    @Test
    void newDeployment_createsTheDemoOwnerAndAllListings() {
        when(userService.getUserIdByEmail(DemoListingsSeeder.DEMO_OWNER_EMAIL)).thenReturn(Optional.empty());
        when(userService.register(any())).thenReturn(new UserResponse(42L, "HireMe Demo Rentals",
                DemoListingsSeeder.DEMO_OWNER_EMAIL, "+254700000000", Role.OWNER, Instant.now()));

        new DemoListingsSeeder(userService, equipmentService, "demo-pass-123").seed();

        ArgumentCaptor<RegisterRequest> owner = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userService).register(owner.capture());
        assertEquals(Role.OWNER, owner.getValue().role());
        assertEquals("demo-pass-123", owner.getValue().password());
        verify(equipmentService, times(DemoListingsSeeder.LISTINGS.size())).create(any(), eq(42L));
    }

    @Test
    void demoOwnerWithAllListings_addsNothing() {
        when(userService.getUserIdByEmail(DemoListingsSeeder.DEMO_OWNER_EMAIL)).thenReturn(Optional.of(42L));
        when(equipmentService.getByOwner(eq(42L), any())).thenReturn(page(DemoListingsSeeder.LISTINGS.stream()
                .map(CreateEquipmentRequest::name).toList()));

        new DemoListingsSeeder(userService, equipmentService, "").seed();

        verify(userService, never()).register(any());
        verify(equipmentService, never()).create(any(), anyLong());
    }

    @Test
    void listingsAVisitorDeleted_areAddedBack_andTheRestLeftAlone() {
        when(userService.getUserIdByEmail(DemoListingsSeeder.DEMO_OWNER_EMAIL)).thenReturn(Optional.of(42L));
        List<String> allButTwo = DemoListingsSeeder.LISTINGS.stream().map(CreateEquipmentRequest::name).skip(2).toList();
        when(equipmentService.getByOwner(eq(42L), any())).thenReturn(page(allButTwo));

        new DemoListingsSeeder(userService, equipmentService, "").seed();

        verify(equipmentService).create(DemoListingsSeeder.LISTINGS.get(0), 42L);
        verify(equipmentService).create(DemoListingsSeeder.LISTINGS.get(1), 42L);
        verify(equipmentService, times(2)).create(any(), anyLong());
    }

    private static PageResponse<EquipmentResponse> page(List<String> names) {
        List<EquipmentResponse> content = names.stream()
                .map(name -> new EquipmentResponse(null, 42L, name, null, null, null, null, null, null, null))
                .toList();
        return new PageResponse<>(content, 0, 100, content.size(), 1);
    }

    @Test
    void noOrShortPassword_givesTheOwnerARandomOne() {
        when(userService.getUserIdByEmail(any())).thenReturn(Optional.empty());
        when(userService.register(any())).thenReturn(new UserResponse(1L, null, null, null, Role.OWNER, null));

        new DemoListingsSeeder(userService, equipmentService, "short").seed();

        ArgumentCaptor<RegisterRequest> owner = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userService).register(owner.capture());
        assertNotEquals("short", owner.getValue().password());
        assertTrue(owner.getValue().password().length() >= 8);
    }

    @Test
    void listings_areValidAndCoverEveryCategory() {
        for (CreateEquipmentRequest listing : DemoListingsSeeder.LISTINGS) {
            assertTrue(listing.name().length() <= 255, listing.name());
            assertTrue(listing.description().length() <= 1000, listing.name());
            assertTrue(listing.dailyRateKes().signum() > 0, listing.name());
        }
        Set<?> categories = DemoListingsSeeder.LISTINGS.stream().map(CreateEquipmentRequest::category)
                .collect(Collectors.toSet());
        assertEquals(3, categories.size());
    }
}
