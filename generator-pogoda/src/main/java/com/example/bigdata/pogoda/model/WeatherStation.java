package com.example.bigdata.pogoda.model;

import lombok.Data;

/** Słownik statyczny: metadane stacji meteorologicznej. */
@Data
public class WeatherStation {
    private String stationId;
    private String name;
    private double lat;
    private double lon;
    private double altitudeM;
    private String region;
    private String climateZone;
}