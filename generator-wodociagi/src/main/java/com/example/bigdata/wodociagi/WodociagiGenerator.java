package com.example.bigdata.wodociagi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.wodociagi.model.NetworkNode;
import com.example.bigdata.wodociagi.model.WaterNetworkEvent;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

public class WodociagiGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<NetworkNode> nodes;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    public WodociagiGenerator(double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.nodes = loadNodes();
    }

    public WaterNetworkEvent generate() {
        NetworkNode node = nodes.get(RNG.nextInt(nodes.size()));

        int hour = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getHour();
        // Pasma zapotrzebowania: PEAK (6-9, 18-21), NIGHT (1-5), NORMAL (pozostałe)
        double demandFactor = (hour >= 6 && hour <= 9) || (hour >= 18 && hour <= 21) ? 1.3
                            : (hour >= 1 && hour <= 5) ? 0.3 : 1.0;

        boolean anomaly  = RNG.nextDouble() < anomalyProbability;
        double  flow     = node.getNominalFlowM3h() * demandFactor * (0.8 + RNG.nextDouble() * 0.4);
        double  pressure = node.getNominalPressureBar() * (0.9 + RNG.nextDouble() * 0.2);

        if (anomaly) {
            pressure *= 0.55; // wyciek – nagły spadek ciśnienia do ~55% nominału
            flow     *= 1.8;  // wzrost przepływu
        }

        boolean isPump = node.getType().equals("PUMP_STATION");

        return WaterNetworkEvent.builder()
                .nodeId(node.getNodeId())
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .flowM3h(r2(flow))
                .pressureBar(r2(pressure))
                .pressureDropBarH(r2((RNG.nextDouble() - 0.5) * (anomaly ? 1.5 : 0.2)))
                .waterTempC(r2(8 + RNG.nextDouble() * 7))
                .chlorineMgL(r2(0.2 + RNG.nextDouble() * 0.3))
                .turbidityNTU(r2(anomaly ? 2 + RNG.nextDouble() * 3 : 0.3 + RNG.nextDouble() * 0.5))
                .phLevel(r2(7.0 + (RNG.nextDouble() - 0.5) * 0.6))
                .pumpSpeedRpm(isPump ? r2(1450 * demandFactor * (0.9 + RNG.nextDouble() * 0.2)) : 0)
                .motorCurrentA(isPump ? r2(45 + RNG.nextDouble() * 20) : 0)
                .vibrationMms(isPump ? r2(anomaly ? 8 + RNG.nextDouble() * 5 : 1 + RNG.nextDouble() * 2) : 0)
                .valveOpen(!anomaly || RNG.nextBoolean())
                .build();
    }

    public String toJson(WaterNetworkEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<NetworkNode> loadNodes() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/network_nodes.json")) {
            return MAPPER.readValue(is,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, NetworkNode.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private double r2(double v) { return Math.round(v * 100.0) / 100.0; }
}
