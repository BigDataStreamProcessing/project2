package com.example.bigdata.powietrze.model;

import lombok.Data;

/** Słownik statyczny: stacja monitoringu jakości powietrza. */
@Data
public class AirStation {
    private String stationId;
    private String name;
    private double lat;
    private double lon;
    private String district;
    private String stationType;
}