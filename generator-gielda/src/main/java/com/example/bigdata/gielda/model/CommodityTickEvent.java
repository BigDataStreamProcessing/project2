package com.example.bigdata.gielda.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CommodityTickEvent {

    private String contractId;   // klucz do słownika kontraktów

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private double price;
    private double priceChange;        // zmiana względem poprzedniego ticka
    private double priceChangePct;     // zmiana procentowa
    private double bidPrice;
    private double askPrice;
    private double spread;

    private long   volumeLots;         // wolumen w lotach
    private long   openInterest;       // otwarte pozycje
    private double volumeValueEur;

    private double high24h;
    private double low24h;
    private double settlementPrice;    // cena rozliczenia z poprzedniej sesji

    private double impliedVolatility;  // implikowana zmienność [%]
    private double rsi14;              // wskaźnik RSI(14)
    private double ma20;               // średnia krocząca 20-tickowa

    private boolean limitUp;
    private boolean limitDown;
    private boolean flashCrash;

    private String session;            // PRE_MARKET / OPEN / CLOSE / AFTER_HOURS
}