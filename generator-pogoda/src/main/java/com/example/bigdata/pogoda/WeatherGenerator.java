package com.example.bigdata.pogoda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.pogoda.model.WeatherEvent;
import com.example.bigdata.pogoda.model.WeatherStation;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

public class WeatherGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<WeatherStation> stations;
    private final double anomalyProbability;
    private final long disorderMaxMs;
    // Stan ciśnienia per stacja – symuluje trend
    private final Map<String, Double> pressureState = new HashMap<>();

    private static final String[] WMO_CODES = {"CLR","FEW","SCT","BKN","OVC","RA","TSRA","SN","FG","HZ"};

    public WeatherGenerator(double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.stations = loadStations();
        stations.forEach(s -> pressureState.put(s.getStationId(), 1013.0 + RNG.nextDouble() * 20 - 10));
    }

    public WeatherEvent generate() {
        WeatherStation station = stations.get(RNG.nextInt(stations.size()));
        String sid = station.getStationId();

        int month = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getMonthValue();
        double baseTemp = (month >= 6 && month <= 8) ? 22.0 : (month <= 2 || month == 12) ? -2.0 : 10.0;

        double temp = baseTemp + (RNG.nextDouble() - 0.5) * 8;
        double humidity = 40 + RNG.nextDouble() * 55;

        // Symulacja trendu ciśnienia
        double pressure = pressureState.get(sid);
        double trend = (RNG.nextDouble() - 0.5) * 2.0;
        pressure = Math.max(960, Math.min(1040, pressure + trend * 0.1));
        pressureState.put(sid, pressure);

        boolean anomaly = RNG.nextDouble() < anomalyProbability;
        double wind = anomaly ? 22 + RNG.nextDouble() * 15 : RNG.nextDouble() * 12;
        if (anomaly) trend = -6.5; // nagły spadek ciśnienia

        double precip = (humidity > 85 || anomaly) ? RNG.nextDouble() * (anomaly ? 40 : 5) : 0;

        return WeatherEvent.builder()
                .stationId(sid)
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .temperatureC(r2(temp))
                .feelsLikeC(r2(temp - wind * 0.3))
                .dewPointC(r2(temp - (100 - humidity) / 5.0))
                .humidityPercent(r2(humidity))
                .pressureHpa(r2(pressure))
                .pressureTrendHpaH(r2(trend))
                .windSpeedMs(r2(wind))
                .windGustMs(r2(wind * (1.2 + RNG.nextDouble() * 0.5)))
                .windDirectionDeg(r2(RNG.nextDouble() * 360))
                .precipitationMmH(r2(precip))
                .snowDepthCm(temp < 0 ? r2(RNG.nextDouble() * 20) : 0)
                .visibilityKm(r2(anomaly && precip > 10 ? 1 + RNG.nextDouble() * 3 : 5 + RNG.nextDouble() * 15))
                .solarRadiationWm2(r2(Math.max(0, 800 - humidity * 6 + (RNG.nextDouble() - 0.5) * 100)))
                .uvIndex(r2(Math.max(0, (800 - humidity * 6) / 100.0)))
                .lightningCount10min(anomaly && precip > 20 ? RNG.nextInt(15) : 0)
                .weatherCode(WMO_CODES[RNG.nextInt(WMO_CODES.length)])
                .build();
    }

    public String toJson(WeatherEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<WeatherStation> loadStations() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/stations.json")) {
            return MAPPER.readValue(is,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, WeatherStation.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}