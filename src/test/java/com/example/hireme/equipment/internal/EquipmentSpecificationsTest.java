package com.example.hireme.equipment.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentSpecificationsTest {

    @Test
    void keywords_dropShortStopAndNumberWords() {
        assertEquals(List.of("tractor", "nakuru"),
                EquipmentSpecifications.keywords("I need a tractor in Nakuru next weekend under 8000 a day"));
    }

    @Test
    void keywords_areLowerCaseDistinctAndCapped() {
        assertEquals(List.of("tent", "chairs", "stage", "sound", "lights"),
                EquipmentSpecifications.keywords("Tent, TENT, chairs; stage + sound / lights & generator"));
    }

    @Test
    void keywords_emptyForBlankText() {
        assertEquals(List.of(), EquipmentSpecifications.keywords("  "));
        assertEquals(List.of(), EquipmentSpecifications.keywords(null));
    }
}
