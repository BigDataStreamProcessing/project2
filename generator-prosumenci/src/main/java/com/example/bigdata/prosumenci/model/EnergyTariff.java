package com.example.bigdata.prosumenci.model;

import lombok.Data;
import java.util.List;

/** Słownik statyczny: taryfa energetyczna (G11 / G12 / G12w). */
@Data
public class EnergyTariff {
    private String tariffCode;
    private String description;
    private List<TariffZone> zones;
    private double monthlyFixedFeePLN;
    private double networkFeePLN;

    @Data
    public static class TariffZone {
        private String name;
        private String hours;
        private double pricePerKwhPLN;
    }
}