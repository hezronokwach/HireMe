package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.CreateEquipmentRequest;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Demo listings, so a new deployment isn't empty: a client who signs up straight away has equipment
 * to browse, book and ask the assistant about. Off unless {@code hireme.demo-listings.enabled=true}.
 * <p>
 * The listings belong to a demo owner account ({@link #DEMO_OWNER_EMAIL}). It is created once; when it
 * already exists nothing is added, so restarts and redeploys never add duplicates. Its password comes from
 * {@code hireme.demo-listings.owner-password}; without one it gets a random password nobody knows, and the
 * listings are just there to browse and book.
 */
@Component
@ConditionalOnProperty(name = "hireme.demo-listings.enabled", havingValue = "true")
class DemoListingsSeeder {

    static final String DEMO_OWNER_EMAIL = "demo-owner@hireme.example";

    private static final Logger log = LoggerFactory.getLogger(DemoListingsSeeder.class);

    private final UserService userService;
    private final EquipmentService equipmentService;
    private final String ownerPassword;

    DemoListingsSeeder(UserService userService, EquipmentService equipmentService,
                       @Value("${hireme.demo-listings.owner-password:}") String ownerPassword) {
        this.userService = userService;
        this.equipmentService = equipmentService;
        this.ownerPassword = ownerPassword == null ? "" : ownerPassword.strip();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (userService.getUserIdByEmail(DEMO_OWNER_EMAIL).isPresent()) {
            log.info("Demo listings: the demo owner already exists, nothing to add");
            return;
        }
        String password = ownerPassword.length() >= 8 ? ownerPassword : UUID.randomUUID().toString();
        Long ownerId = userService.register(new RegisterRequest("HireMe Demo Rentals", DEMO_OWNER_EMAIL,
                "+254700000000", password, Role.OWNER)).id();
        LISTINGS.forEach(listing -> equipmentService.create(listing, ownerId));
        log.info("Demo listings: added {} listings owned by {}", LISTINGS.size(), DEMO_OWNER_EMAIL);
    }

    static final List<CreateEquipmentRequest> LISTINGS = List.of(
            listing("Massey Ferguson 375 Tractor", Category.AGRICULTURAL, "Nakuru", 6500,
                    "75 hp, 2WD tractor in good working order, ideal for ploughing and harrowing. Disc plough "
                            + "available for KES 1,000 more per day. Driver on request."),
            listing("John Deere 5055E Tractor with Driver", Category.AGRICULTURAL, "Eldoret", 8500,
                    "55 hp, 4WD tractor. The rate includes an experienced driver and fuel for up to 8 hours a day. "
                            + "Good for wheat and maize farms."),
            listing("Combine Harvester (New Holland TC5.70)", Category.AGRICULTURAL, "Eldoret", 35000,
                    "Wheat and maize harvesting. Operator included. Booked out quickly at harvest time, so ask early."),
            listing("Knapsack Sprayer Set (5 units)", Category.AGRICULTURAL, "Kisumu", 1500,
                    "Five 16-litre knapsack sprayers with spare nozzles. Protective gear included. "
                            + "Pick-up from Kisumu town."),
            listing("Water Pump, 3-inch Petrol", Category.AGRICULTURAL, "Nakuru", 1800,
                    "Honda engine, 3-inch inlet and outlet, 30 m of hose. Good for irrigation and draining."),
            listing("CAT 320 Excavator", Category.HEAVY_MACHINERY, "Nairobi", 45000,
                    "20-tonne excavator for foundations, trenching and site clearing. Operator included. "
                            + "Transport within Nairobi at extra cost."),
            listing("JCB 3CX Backhoe Loader", Category.HEAVY_MACHINERY, "Nairobi", 28000,
                    "Backhoe loader for digging, loading and small demolition jobs. Comes with an operator. "
                            + "Half-day hire possible on request."),
            listing("Bomag Road Roller, 10 t", Category.HEAVY_MACHINERY, "Mombasa", 22000,
                    "Single-drum vibratory roller for road and yard compaction. Operator included."),
            listing("Concrete Mixer, 1 Bag Diesel", Category.HEAVY_MACHINERY, "Kisumu", 3500,
                    "One-bag diesel concrete mixer on wheels. Delivered within Kisumu for KES 1,500."),
            listing("Tipper Truck, 10 m³", Category.HEAVY_MACHINERY, "Nakuru", 18000,
                    "Isuzu FVZ tipper with driver, for ballast, sand and murram. Fuel not included."),
            listing("Wedding Tent for 200 Guests", Category.EVENT_SETUP, "Mombasa", 15000,
                    "White A-frame tent with side walls, set up and taken down by our crew. Lighting available."),
            listing("Event Chairs and Tables (100 guests)", Category.EVENT_SETUP, "Nairobi", 6000,
                    "100 chairs with white covers and 10 round tables with cloths. Delivery and pick-up in Nairobi "
                            + "included."),
            listing("PA Sound System with Microphones", Category.EVENT_SETUP, "Nairobi", 9000,
                    "Two 1,000 W speakers, a mixer and 4 wireless microphones, for up to 300 people. "
                            + "A technician stays for the event."),
            listing("Generator, 20 kVA Silent", Category.EVENT_SETUP, "Mombasa", 8000,
                    "Silent diesel generator for events and sites. Fuel billed separately. Delivery in Mombasa included."),
            listing("Stage Platform 6 m × 4 m", Category.EVENT_SETUP, "Kisumu", 12000,
                    "Modular stage with skirting and steps, set up by our crew. Good for weddings, concerts and launches.")
    );

    private static CreateEquipmentRequest listing(String name, Category category, String location, int rate,
                                                  String description) {
        return new CreateEquipmentRequest(name, category, description, BigDecimal.valueOf(rate), location);
    }
}
