package com.example.bigdata.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.ecommerce.model.OrderEvent;
import com.example.bigdata.ecommerce.model.ProductCatalog;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

public class EcommerceGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<ProductCatalog> products;
    private final List<String> userIds;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    private static final String[] PAYMENT_METHODS  = {"CARD","BLIK","TRANSFER","PAYPAL"};
    private static final String[] DEVICES          = {"MOBILE","DESKTOP","TABLET"};
    private static final String[] BROWSERS         = {"Chrome","Firefox","Safari","Edge"};
    private static final String[] CITIES           = {"Warszawa","Krakow","Wroclaw","Gdansk","Poznan","Lodz"};
    private static final String[] EVENT_TYPES      = {"ORDER_PLACED","ORDER_PLACED","ORDER_PLACED","PAYMENT_OK","PAYMENT_FAILED","RETURN"};

    public EcommerceGenerator(int usersCount, double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.products = loadProducts();
        this.userIds  = generateUserIds(usersCount);
    }

    public OrderEvent generate() {
        String userId = userIds.get(RNG.nextInt(userIds.size()));
        boolean anomaly = RNG.nextDouble() < anomalyProbability;

        int itemCount = 1 + RNG.nextInt(anomaly ? 8 : 3);
        List<OrderEvent.OrderItem> items = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < itemCount; i++) {
            ProductCatalog p = products.get(RNG.nextInt(products.size()));
            int qty = 1 + RNG.nextInt(anomaly ? 10 : 2);
            double price = p.getBasePricePLN() * (0.95 + RNG.nextDouble() * 0.1);
            double line  = r2(price * qty);
            total += line;
            items.add(OrderEvent.OrderItem.builder()
                    .productId(p.getProductId())
                    .quantity(qty)
                    .unitPricePLN(r2(price))
                    .lineTotalPLN(line)
                    .build());
        }

        double discount = RNG.nextDouble() < 0.2 ? r2(total * 0.1) : 0;
        double fraud    = anomaly ? 0.7 + RNG.nextDouble() * 0.3
                                  : items.stream().mapToDouble(i2 ->
                                      products.stream().filter(p -> p.getProductId().equals(i2.getProductId()))
                                              .mapToDouble(ProductCatalog::getFraudRiskScore).average().orElse(0.1)).max().orElse(0.1);

        String payMethod = PAYMENT_METHODS[RNG.nextInt(PAYMENT_METHODS.length)];
        String payStatus = anomaly && RNG.nextDouble() < 0.5 ? "FAILED" : "SUCCESS";

        return OrderEvent.builder()
                .orderId(UUID.randomUUID().toString())
                .userId(userId)
                .sessionId(UUID.randomUUID().toString().substring(0, 8))
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .eventType(EVENT_TYPES[RNG.nextInt(EVENT_TYPES.length)])
                .items(items)
                .totalAmountPLN(r2(total - discount))
                .discountAmountPLN(discount)
                .couponCode(discount > 0 ? "PROMO10" : null)
                .paymentMethod(payMethod)
                .paymentStatus(payStatus)
                .cardBin(payMethod.equals("CARD") ? String.valueOf(400000 + RNG.nextInt(99999)) : null)
                .shippingCountry("PL")
                .shippingCity(CITIES[RNG.nextInt(CITIES.length)])
                .deviceType(DEVICES[RNG.nextInt(DEVICES.length)])
                .browserFamily(BROWSERS[RNG.nextInt(BROWSERS.length)])
                .fraudScore(r2(fraud))
                .isFraudSuspected(fraud > 0.65)
                .build();
    }

    public String toJson(OrderEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<ProductCatalog> loadProducts() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/products.json")) {
            return MAPPER.readValue(is, MAPPER.getTypeFactory().constructCollectionType(List.class, ProductCatalog.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<String> generateUserIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) ids.add(String.format("USR-%06d", i));
        return ids;
    }

    private double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}