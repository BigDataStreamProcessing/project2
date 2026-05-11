package com.example.bigdata.powietrze;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.powietrze.model.AirQualityEvent;
import com.example.bigdata.powietrze.model.AirStation;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

public class PowietrzeGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<AirStation> stations;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    public PowietrzeGenerator(double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.stations = loadStations();
    }

    public AirQualityEvent generate() {
        AirStation st = stations.get(RNG.nextInt(stations.size()));

        int month = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getMonthValue();
        int hour  = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getHour();

        // Sezonowość – smog głównie zima + pory szczytu komunikacyjnego
        boolean heatingSezon = month <= 3 || month >= 10;
        double  basePm25 = heatingSezon ? 25 : 8;
        boolean peakHour = (hour >= 7 && hour <= 9) || (hour >= 16 && hour <= 19);
        if (peakHour) basePm25 *= 1.4;

        boolean anomaly = RNG.nextDouble() < anomalyProbability;
        double pm25 = anomaly ? 60 + RNG.nextDouble() * 100
                              : basePm25 + (RNG.nextDouble() - 0.2) * basePm25;
        pm25 = Math.max(0, pm25);

        double pm10    = pm25 * (1.4 + RNG.nextDouble() * 0.4);
        double no2     = 15  + RNG.nextDouble() * (anomaly ? 80 : 30);
        double o3      = 20  + RNG.nextDouble() * (peakHour ? 20 : 60);
        double co      = 0.3 + RNG.nextDouble() * (anomaly ? 3 : 0.8);
        double so2     = 2   + RNG.nextDouble() * 10;
        double benzene = 0.5 + RNG.nextDouble() * (anomaly ? 4 : 1);

        return AirQualityEvent.builder()
                .stationId(st.getStationId())
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .pm25(r2(pm25))
                .pm10(r2(pm10))
                .no2(r2(no2))
                .o3(r2(o3))
                .co(r2(co))
                .so2(r2(so2))
                .benzene(r2(benzene))
                .temperatureC(r2(-5 + RNG.nextDouble() * 30))
                .humidityPercent(r2(40 + RNG.nextDouble() * 55))
                .windSpeedMs(r2(RNG.nextDouble() * 8))
                .windDirectionDeg(r2(RNG.nextDouble() * 360))
                .boundaryLayerHeightM(r2(anomaly ? 100 + RNG.nextDouble() * 200 : 500 + RNG.nextDouble() * 1000))
                .build();
    }

    public String toJson(AirQualityEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<AirStation> loadStations() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/air_stations.json")) {
            return MAPPER.readValue(is, MAPPER.getTypeFactory().constructCollectionType(List.class, AirStation.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
