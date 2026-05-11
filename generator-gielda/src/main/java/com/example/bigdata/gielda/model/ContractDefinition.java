package com.example.bigdata.gielda.model;

import lombok.Data;
import java.util.List;

/** Słownik statyczny: specyfikacja kontraktu terminowego. */
@Data
public class ContractDefinition {
    private String       contractId;
    private String       commodity;
    private String       exchange;
    private String       currency;
    private String       unit;
    private double       tickSizeEUR;
    private double       tickSizeUSD;
    private double       lotSizeT;
    private String       harvestMonth;
    private List<String> mainExporters;
    private double       typicalPriceRangeMin;
    private double       typicalPriceRangeMax;
}