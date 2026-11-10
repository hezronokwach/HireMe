package com.example.hireme.equipment.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EquipmentInUseException extends BaseException {
    public EquipmentInUseException() {
        super("Equipment has pending or upcoming bookings", HttpStatus.CONFLICT);
    }
}
