package com.example.hireme.equipment.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EquipmentNotFoundException extends BaseException {
    public EquipmentNotFoundException() {
        super("Equipment not found", HttpStatus.NOT_FOUND);
    }
}
