package com.example.bigdata.powietrze.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Zdarzenie strumieniowe: odczyt stacji jakości powietrza.
 *
 * Agregat L1: minutowe statystyki per stacja i sektor wiatru
 *   – avg PM2.5, avg NO2, max PM10, avg boundaryLayerHeightM
 * Agregat L2: dzienny raport per typ stacji i sektor wiatru
 * Anomalia: PM2.5 > 55 µg/m³ (próg "Niezdrowy" wg aqi_norms.json),
 *           natychmiastowy: PM2.5 > 150 µg/m³ (próg "BardzoNiezdrowy")
 *
 * Pola aqiValue, aqiLevel oraz alertActive wyznaczane są post-enrichment
 * z progów zdefiniowanych w aqi_norms.json — nie są częścią strumienia.
 */
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
