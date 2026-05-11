package com.example.bigdata.transport.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VehiclePositionEvent {

    private String vehicleId;
    private String lineId;         // klucz do słownika tras

    private String currentStop;
    private String nextStop;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private double latitude;
    private double longitude;
    private double speedKmh;
    private double headingDeg;

    private int passengerCount;

    /** Opóźnienie względem rozkładu w sekundach (ujemne = przed czasem) */
    private int delaySeconds;

    private boolean doorsOpen;
    private boolean airConditioningOn;
    private double  engineTempC;
    private double  fuelLevelPercent;   // lub naładowanie baterii dla pojazdów elektrycznych
}
