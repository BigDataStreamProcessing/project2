package com.example.bigdata.wodociagi.model;

import lombok.Data;

/** Słownik statyczny: węzeł sieci wodociągowej. */
@Data
public class NetworkNode {
    private String nodeId;
    private String type;
    private String zone;
    private double nominalFlowM3h;
    private double nominalPressureBar;
    private double diameterMm;
    private String district;
}