export * from './ai.service';
import { AIService } from './ai.service';
export * from './auth.service';
import { AuthService } from './auth.service';
export * from './bookings.service';
import { BookingsService } from './bookings.service';
export * from './equipment.service';
import { EquipmentService } from './equipment.service';
export const APIS = [AIService, AuthService, BookingsService, EquipmentService];
