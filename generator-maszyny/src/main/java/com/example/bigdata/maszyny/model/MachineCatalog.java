package com.example.bigdata.maszyny.model;

import lombok.Data;

/** Słownik statyczny: specyfikacja techniczna maszyny. */
@Data
public class MachineCatalog {
    private String machineId;
    private String name;
    private String type;
    private double nominalRpm;
    private double nominalCurrentA;
    private double vibrationWarnMms;
    private double vibrationAlarmMms;
    private double tempWarnC;
    private double tempAlarmC;
    private String productionLine;
    private int    plannedMaintenanceDays;
}