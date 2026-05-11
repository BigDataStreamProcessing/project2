package com.example.bigdata.parking.model;

import lombok.Data;

/** Słownik statyczny: strefa parkingowa. */
@Data
public class ParkingZone {
    private String zoneId;
    private String name;
    private int    capacity;
    private double pricePerHourPLN;
    private int    maxStayHours;
    private String type;
    private String district;
    private double distanceToCenterMinM;
    private double distanceToCenterMaxM;
}
