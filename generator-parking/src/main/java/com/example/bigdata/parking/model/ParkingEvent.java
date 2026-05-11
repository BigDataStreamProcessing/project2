package com.example.bigdata.parking.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ParkingEvent {

    private String eventId;
    private String spotId;          // np. "Z-A-042"
    private String zoneId;          // klucz do słownika stref

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String eventType;        // ENTRY / EXIT / STATUS_UPDATE

    /** Zanonimizowany hash tablicy rejestracyjnej */
    private String vehicleHash;
    private String vehicleCategory;  // CAR / TRUCK / MOTORCYCLE / EV

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant entryTime;

    /** Czas postoju w minutach (wypełniony przy EXIT) */
    private Integer durationMinutes;

    private String paymentMethod;    // APP / METER / CARD / SUBSCRIPTION

    private boolean spotOccupied;
    private boolean evChargingPoint;
    private boolean evCharging;
    private double  batteryLevelAtEntry;   // % dla EV, -1 dla innych

    private double  sensorConfidence;      // 0..1 pewność czujnika indukcyjnego

    private double  distanceToCenterM;
}
