package com.example.bigdata.prosumenci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.prosumenci.model.EnergyTariff;
import com.example.bigdata.prosumenci.model.ProsumentEvent;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

/**
 * Generator zdarzeń strumieniowych dla prosumentów energii.
 *
 * Symuluje realistyczne dane PV z uwzględnieniem:
 * – pory dnia (produkcja tylko w dzień),
 * – sezonowości (lato/zima),
 * – losowego ładowania EV (wieczorem),
 * – rzadkich anomalii (nadprodukcja, spike konsumpcji).
 */
public class ProsumenciGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final List<String> prosumentIds;
    private final List<EnergyTariff> tariffs;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    private static final String[] LOCALITIES = {
        "Warszawa-Wola", "Warszawa-Mokotow", "Krakow-Podgorze",
        "Gdansk-Wrzeszcz", "Wroclaw-Krzyki", "Poznan-Grunwald"
    };

    private static final String[] TARIFF_CODES = {"G11", "G12", "G12w"};

    public ProsumenciGenerator(int prosumenciCount, double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.prosumentIds = generateIds(prosumenciCount);
        this.tariffs = loadTariffs();
    }

    /** Generuje jedno zdarzenie dla losowego prosumenta. */
    public ProsumentEvent generate() {
        String id       = prosumentIds.get(RNG.nextInt(prosumentIds.size()));
        String tariff   = TARIFF_CODES[RNG.nextInt(TARIFF_CODES.length)];
        String locality = LOCALITIES[RNG.nextInt(LOCALITIES.length)];

        int hour        = Instant.now().atZone(java.time.ZoneId.of("Europe/Warsaw")).getHour();
        boolean isDaytime = hour >= 6 && hour <= 20;

        double maxPvKw  = 5.0 + RNG.nextDouble() * 5.0; // 5–10 kW instalacja
        double irradiance = isDaytime
                ? 200 + RNG.nextDouble() * 800  // 200–1000 W/m²
                : 0;
        double pvKw     = isDaytime ? maxPvKw * (irradiance / 1000.0) * (0.85 + RNG.nextDouble() * 0.15) : 0;
        double loadKw   = 0.3 + RNG.nextDouble() * 3.2; // 0.3–3.5 kW bazowe

        boolean evCharging = (hour >= 17 && hour <= 22) && RNG.nextDouble() < 0.3;
        if (evCharging) loadKw += 7.4; // wallbox 7.4 kW

        // Anomalia – nadprodukcja lub spike konsumpcji
        boolean anomaly = RNG.nextDouble() < anomalyProbability;
        if (anomaly && isDaytime) pvKw *= 1.20;
        if (anomaly && !isDaytime) loadKw *= 4.5;

        double interval = 15.0 / 60.0;  // 15 minut w h
        double prodKwh  = pvKw   * interval;
        double consKwh  = loadKw * interval;
        double netKwh   = prodKwh - consKwh;

        double battery  = 20 + RNG.nextDouble() * 60; // 20–80 %

        return ProsumentEvent.builder()
                .prosumentId(id)
                .tariffCode(tariff)
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .productionKwh(round2(prodKwh))
                .consumptionKwh(round2(consKwh))
                .pvPowerKw(round2(pvKw))
                .loadPowerKw(round2(loadKw))
                .gridExportKwh(round2(Math.max(0, netKwh)))
                .gridImportKwh(round2(Math.max(0, -netKwh)))
                .batteryStatePercent(round2(battery))
                .evCharging(evCharging)
                .voltageV(round2(230 + (RNG.nextDouble() - 0.5) * 10))
                .pvTemperatureC(round2(25 + irradiance / 50.0))
                .irradianceWm2(round2(irradiance))
                .locality(locality)
                .build();
    }

    /** Serializuje zdarzenie do JSON. */
    public String toJson(ProsumentEvent event) throws Exception {
        return MAPPER.writeValueAsString(event);
    }

    private List<String> generateIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add(String.format("PRO-%05d", i));
        }
        return ids;
    }

    private List<EnergyTariff> loadTariffs() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/tariffs.json")) {
            return MAPPER.readValue(is,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, EnergyTariff.class));
        } catch (Exception e) {
            System.err.println("Nie mozna zaladowac slownika taryf: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}