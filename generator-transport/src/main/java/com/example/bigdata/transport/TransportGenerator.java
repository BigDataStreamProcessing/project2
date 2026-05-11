package com.example.bigdata.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.transport.model.RouteDefinition;
import com.example.bigdata.transport.model.VehiclePositionEvent;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

public class TransportGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<RouteDefinition> routes;
    private final List<String> vehicleIds;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    // Centrum Warszawy – punkt bazowy
    private static final double BASE_LAT = 52.2297;
    private static final double BASE_LON = 21.0122;

    public TransportGenerator(int vehicleCount, double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.routes     = loadRoutes();
        this.vehicleIds = generateVehicleIds(vehicleCount);
    }

    public VehiclePositionEvent generate() {
        String vehicleId = vehicleIds.get(RNG.nextInt(vehicleIds.size()));
        RouteDefinition route = routes.get(RNG.nextInt(routes.size()));
        List<String> stops = route.getStops();
        int stopIdx = RNG.nextInt(stops.size() - 1);

        boolean anomaly = RNG.nextDouble() < anomalyProbability;
        double speed    = anomaly ? 0 : route.getNormalSpeedKmh() * (0.5 + RNG.nextDouble() * 0.7);
        int    delay    = anomaly
                ? 300 + RNG.nextInt(600)                    // awaria: 5–15 min opóźnienia
                : (int)((RNG.nextDouble() - 0.3) * 180);   // norma: -54..+126 s

        int capacity   = route.getType().equals("METRO") ? 800 : (route.getType().equals("TRAM") ? 200 : 80);
        int passengers = (int)(capacity * (0.1 + RNG.nextDouble() * 0.9));

        return VehiclePositionEvent.builder()
                .vehicleId(vehicleId)
                .lineId(route.getLineId())
                .currentStop(stops.get(stopIdx))
                .nextStop(stops.get(stopIdx + 1))
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .latitude(round5(BASE_LAT + (RNG.nextDouble() - 0.5) * 0.1))
                .longitude(round5(BASE_LON + (RNG.nextDouble() - 0.5) * 0.15))
                .speedKmh(round2(speed))
                .headingDeg(round2(RNG.nextDouble() * 360))
                .passengerCount(passengers)
                .delaySeconds(delay)
                .doorsOpen(speed == 0 && RNG.nextBoolean())
                .airConditioningOn(true)
                .engineTempC(round2(70 + RNG.nextDouble() * 30))
                .fuelLevelPercent(round2(20 + RNG.nextDouble() * 80))
                .build();
    }

    public String toJson(VehiclePositionEvent e) throws Exception {
        return MAPPER.writeValueAsString(e);
    }

    private List<RouteDefinition> loadRoutes() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/routes.json")) {
            return MAPPER.readValue(is,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, RouteDefinition.class));
        } catch (Exception e) {
            System.err.println("Blad ladowania tras: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> generateVehicleIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) ids.add(String.format("VEH-%04d", i));
        return ids;
    }

    private double round2(double v) { return Math.round(v * 100.0)   / 100.0; }
    private double round5(double v) { return Math.round(v * 100000.0)/ 100000.0; }
}
