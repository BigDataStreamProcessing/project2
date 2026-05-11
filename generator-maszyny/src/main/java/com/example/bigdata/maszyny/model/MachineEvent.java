package com.example.bigdata.maszyny.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Zdarzenie strumieniowe: odczyt telemetrii maszyny produkcyjnej.
 *
 * Agregat L1: OEE (Overall Equipment Effectiveness) per maszyna per zmiana
 *   – Availability, Performance, Quality
 * Agregat L2: OEE per linia produkcyjna per dobę + alert Predictive Maintenance
 * Anomalia:   wibracje > progu alarmowego, temperatura łożysk > max,
 *             nagły wzrost prądu (> 30% nominału) → możliwe zacięcie narzędzia
 *
 * Uwaga: flagi alarmowe (vibrationWarn/Alarm, tempWarn/Alarm) są wyliczane
 * podczas przetwarzania strumienia po połączeniu z MachineCatalog —
 * nie są częścią surowego odczytu telemetrycznego.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MachineEvent {

    private String machineId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String machineState;       // RUNNING / IDLE / SETUP / FAULT / MAINTENANCE

    // Parametry mechaniczne
    private double spindleRpm;
    private double spindleLoadPercent;
    private double feedRateMmMin;

    // Parametry elektryczne
    private double motorCurrentA;
    private double motorVoltageV;
    private double powerKw;
    private double energyKwhDelta;    // zużycie energii w tym odczycie [kWh]

    // Stan termiczny
    private double bearingTempC;
    private double motorTempC;
    private double coolantTempC;

    // Wibracje (3 osie)
    private double vibrationXMms;
    private double vibrationYMms;
    private double vibrationZMms;
    private double vibrationRmsMms;   // wartość skuteczna

    // Produktywność
    private int    partsProducedDelta;  // sztuki wyprodukowane w tym odczycie
    private int    defectPartsDelta;    // sztuki wadliwe w tym odczycie
    private double cycleTimeS;          // rzeczywisty czas cyklu [s]; 0 gdy maszyna nie produkuje

    private String shift;              // A / B / C
}
