package com.example.bigdata.transport.model;

import lombok.Data;
import java.util.List;

/** Słownik statyczny: definicja linii komunikacyjnej. */
@Data
public class RouteDefinition {
    private String lineId;
    private String name;
    private String type;
    private List<String> stops;
    private int scheduledIntervalMin;
    private double normalSpeedKmh;
}