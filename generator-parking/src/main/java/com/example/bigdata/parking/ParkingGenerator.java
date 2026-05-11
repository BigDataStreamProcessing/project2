package com.example.bigdata.parking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.parking.model.ParkingEvent;
import com.example.bigdata.parking.model.ParkingZone;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ParkingGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<ParkingZone> zones;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    private static final String[] EVENT_TYPES     = {"ENTRY","ENTRY","EXIT","EXIT","STATUS_UPDATE"};
    private static final String[] VEHICLE_CATS    = {"CAR","CAR","CAR","MOTORCYCLE","TRUCK","EV"};
    private static final String[] PAYMENT_METHODS = {"APP","METER","CARD","SUBSCRIPTION"};

    public ParkingGenerator(double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.zones = loadZones();
    }

    public ParkingEvent generate() {
        ParkingZone zone    = zones.get(RNG.nextInt(zones.size()));
        int         hour    = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getHour();
        String      eventType = EVENT_TYPES[RNG.nextInt(EVENT_TYPES.length)];
        boolean     anomaly = RNG.nextDouble() < anomalyProbability;
        String      vehCat  = VEHICLE_CATS[RNG.nextInt(VEHICLE_CATS.length)];
        boolean     isEV    = vehCat.equals("EV");

        // Anomalia wydłuża czas postoju ponad maxStayHours — wykrywana post-enrichment
        Instant entryTime = Instant.now().minus(
                anomaly ? zone.getMaxStayHours() * 60 + 30 + RNG.nextInt(120)
                        : (int)(RNG.nextDouble() * zone.getMaxStayHours() * 60),
                ChronoUnit.MINUTES);

        int durationMin = eventType.equals("EXIT")
                ? (int) ChronoUnit.MINUTES.between(entryTime, Instant.now())
                : -1;

        int    spotNum   = 1 + RNG.nextInt(zone.getCapacity());
        double distanceM = r2(zone.getDistanceToCenterMinM()
                + RNG.nextDouble() * (zone.getDistanceToCenterMaxM() - zone.getDistanceToCenterMinM()));

        return ParkingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .spotId(zone.getZoneId() + "-" + String.format("%03d", spotNum))
                .zoneId(zone.getZoneId())
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .eventType(eventType)
                .vehicleHash(Integer.toHexString((zone.getZoneId() + spotNum + hour).hashCode()))
                .vehicleCategory(vehCat)
                .entryTime(entryTime)
                .durationMinutes(durationMin > 0 ? durationMin : null)
                .paymentMethod(PAYMENT_METHODS[RNG.nextInt(PAYMENT_METHODS.length)])
                .spotOccupied(!eventType.equals("EXIT"))
                .evChargingPoint(isEV)
                .evCharging(isEV && RNG.nextBoolean())
                .batteryLevelAtEntry(isEV ? r2(5 + RNG.nextDouble() * 80) : -1)
                .sensorConfidence(r2(0.85 + RNG.nextDouble() * 0.15))
                .distanceToCenterM(distanceM)
                .build();
    }

    public String toJson(ParkingEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<ParkingZone> loadZones() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/parking_zones.json")) {
            return MAPPER.readValue(is, MAPPER.getTypeFactory().constructCollectionType(List.class, ParkingZone.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
