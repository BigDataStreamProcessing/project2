package com.example.bigdata.wodociagi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Zdarzenie strumieniowe: odczyt czujnika w węźle sieci wodociągowej.
 *
 * Agregat L1: minutowe parametry per węzeł i pasmo zapotrzebowania
 *   (PEAK: 6-9/18-21, NIGHT: 1-5, NORMAL: pozostałe)
 *   – avg flowM3h, avg pressureBar, avg chlorineMgL, max turbidityNTU
 * Agregat L2: dzienny raport per strefa (A/B/C) i pasmo zapotrzebowania
 * Anomalia:   ciśnienie < 70% nominału (wyciek, post-enrichment),
 *             natychmiastowy: ciśnienie < 55% nominału i turbidityNTU > 2.0
 *
 * Pole totalFlowM3 usunięte — licznik narastający wymagający własnego agregatora;
 * flowM3h jako natężenie chwilowe jest wystarczające dla agregacji minutowej.
 * Pole leakAlarmActive usunięte — wyznaczane post-enrichment z nominalPressureBar
 * ze słownika network_nodes.json.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WaterNetworkEvent {

    private String nodeId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private double flowM3h;             // przepływ chwilowy [m³/h]
    private double pressureBar;         // ciśnienie [bar]
    private double pressureDropBarH;    // tendencja ciśnieniowa [bar/h]

    private double waterTempC;
    private double chlorineMgL;         // stężenie chloru [mg/L]
    private double turbidityNTU;        // mętność wody [NTU]
    private double phLevel;

    private double pumpSpeedRpm;        // obroty pompy [obr/min]; 0 dla węzłów innych niż PUMP_STATION
    private double motorCurrentA;       // prąd silnika [A]; 0 dla węzłów innych niż PUMP_STATION
    private double vibrationMms;        // wibracje pompy [mm/s]; 0 dla węzłów innych niż PUMP_STATION

    private boolean valveOpen;
}
