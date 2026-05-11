package com.example.bigdata.ecommerce.model;

import lombok.Data;

/** Słownik statyczny: produkt w katalogu sklepu. */
@Data
public class ProductCatalog {
    private String productId;
    private String name;
    private String category;
    private String subcategory;
    private double basePricePLN;
    private double marginPercent;
    private double weightKg;
    private double fraudRiskScore;
}