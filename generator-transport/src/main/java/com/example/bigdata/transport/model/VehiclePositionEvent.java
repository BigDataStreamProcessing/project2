package com.example.bigdata.transport.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Zdarzenie strumieniowe: pozycja GPS pojazdu komunikacji miejskiej.
 *
 * Agregat L1: minutowe statystyki per linia i pojazd
 *   – avg delaySeconds, avg speedKmh, avg passengerCount,
 *     liczba odczytów z speed=0 i delay>300s
 * Agregat L2: dzienny raport per typ środka transportu (BUS/TRAM/METRO)
 * Anomalia:   speed=0 i delay>300s przez 5 min (licznikowy),
 *             delay>600s i occupancyPercent>80% (natychmiastowy, post-enrichment)
 *
 * Pole occupancyPercent wyznaczane jest post-enrichment — wymaga nominalnej
 * pojemności pojazdu zależnej od type w słowniku routes.json.
 * Pole district usunięte — w generatorze losowane bez związku z GPS.
 */
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
