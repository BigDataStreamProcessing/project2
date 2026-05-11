package com.example.bigdata.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

/**
 * Zdarzenie strumieniowe: zamówienie/transakcja e-commerce.
 *
 * Agregat L1: GMV (Gross Merchandise Value) per kategoria per 1 minuta
 * Agregat L2: dzienny dashboard sprzedaży – top produkty, revenue, konwersja
 * Anomalia:   fraudScore > progu, wartość zamówienia > 5× średniej klienta,
 *             wiele zamówień z jednego IP w < 60 s
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderEvent {

    private String  orderId;
    private String  userId;
    private String  sessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private String  eventType;      // ORDER_PLACED / PAYMENT_OK / PAYMENT_FAILED / RETURN

    private List<OrderItem> items;

    private double  totalAmountPLN;
    private double  discountAmountPLN;
    private String  couponCode;

    private String  paymentMethod;  // CARD / BLIK / TRANSFER / PAYPAL
    private String  paymentStatus;  // PENDING / SUCCESS / FAILED
    private String  cardBin;        // pierwsze 6 cyfr karty (nie PCI-DSS sensitive)

    private String  shippingCountry;
    private String  shippingCity;
    private String  deviceType;     // MOBILE / DESKTOP / TABLET
    private String  browserFamily;

    private double  fraudScore;     // 0.0 – 1.0
    private boolean isFraudSuspected;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private int    quantity;
        private double unitPricePLN;
        private double lineTotalPLN;
    }
}