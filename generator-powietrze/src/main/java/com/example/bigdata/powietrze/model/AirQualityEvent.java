package com.example.bigdata.powietrze.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AirQualityEvent {

    private String stationId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    /** PM2.5 [µg/m³] */
    private double pm25;
    /** PM10 [µg/m³] */
    private double pm10;
    /** NO₂ [µg/m³] */
    private double no2;
    /** O₃ [µg/m³] */
    private double o3;
    /** CO [mg/m³] */
    private double co;
    /** SO₂ [µg/m³] */
    private double so2;
    /** Benzen [µg/m³] */
    private double benzene;

    private double temperatureC;
    private double humidityPercent;
    private double windSpeedMs;
    private double windDirectionDeg;
    /** Wysokość warstwy granicznej atmosfery [m] */
    private double boundaryLayerHeightM;
}
