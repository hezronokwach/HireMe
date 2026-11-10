package com.example.hireme.equipment;

import com.example.hireme.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentAiControllerTest {

    @Mock
    private EquipmentDescriptionService descriptionService;
    @Mock
    private UserService userService;

    @InjectMocks
    private EquipmentAiController controller;

    @Test
    void describe_passesTheRequestAndCallerToTheService() {
        DescribeEquipmentRequest request = new DescribeEquipmentRequest("Tent", Category.EVENT_SETUP, "Nairobi", "seats 200");
        EquipmentDescriptionResponse draft = new EquipmentDescriptionResponse("Tent for 200", "A large tent.", BigDecimal.valueOf(3000), "Similar tents.", 2);
        when(userService.getUserIdByEmail("owner@test.com")).thenReturn(Optional.of(5L));
        when(descriptionService.describe(request, 5L)).thenReturn(draft);

        ResponseEntity<EquipmentDescriptionResponse> response =
                controller.describe(request, new User("owner@test.com", "x", List.of()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(draft, response.getBody());
    }
}
