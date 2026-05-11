package com.example.bigdata.wodociagi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

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
