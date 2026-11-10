package com.example.hireme.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// What the owner tells us before the AI drafts a listing.
public record DescribeEquipmentRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Category is required")
        Category category,

        @Size(max = 100, message = "Location must be at most 100 characters")
        String location,

        // free text, e.g. "2019, 5 tonnes, comes with operator"
        @Size(max = 500, message = "Notes must be at most 500 characters")
        String notes
) {
}
