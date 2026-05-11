package com.example.bigdata.pogoda.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WeatherEvent {

    private String stationId;   // klucz do słownika stacji

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private double temperatureC;
    private double feelsLikeC;
    private double dewPointC;
    private double humidityPercent;
    private double pressureHpa;
    private double pressureTrendHpaH;   // zmiana ciśnienia na godzinę

    private double windSpeedMs;
    private double windGustMs;
    private double windDirectionDeg;

    private double precipitationMmH;    // opady w mm/h
    private double snowDepthCm;
    private double visibilityKm;

    private double solarRadiationWm2;
    private double uvIndex;
    private int    lightningCount10min;  // liczba wyładowań w ostatnich 10 min

    private String weatherCode;          // WMO code (np. "RA" deszcz, "SN" śnieg)
}