package com.example.bigdata.maszyny;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.maszyny.model.MachineCatalog;
import com.example.bigdata.maszyny.model.MachineEvent;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

public class MaszynyGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<MachineCatalog> machines;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    private static final String[] STATES = {"RUNNING","RUNNING","RUNNING","RUNNING","IDLE","SETUP","FAULT"};

    public MaszynyGenerator(double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.machines = loadMachines();
    }

    public MachineEvent generate() {
        MachineCatalog mc  = machines.get(RNG.nextInt(machines.size()));
        String mid         = mc.getMachineId();
        boolean anomaly    = RNG.nextDouble() < anomalyProbability;
        String  state      = anomaly ? "FAULT" : STATES[RNG.nextInt(STATES.length)];
        boolean running    = state.equals("RUNNING");

        double rpm         = running ? mc.getNominalRpm() * (0.85 + RNG.nextDouble() * 0.2) : 0;
        double current     = running ? mc.getNominalCurrentA() * (anomaly ? 1.35 : 0.8 + RNG.nextDouble() * 0.4) : 2;
        double vib         = anomaly ? mc.getVibrationWarnMms() * 1.5 + RNG.nextDouble() * 3
                                     : RNG.nextDouble() * mc.getVibrationWarnMms() * 0.7;
        double bearingTemp = running ? 45 + RNG.nextDouble() * 20 + (anomaly ? 20 : 0) : 30 + RNG.nextDouble() * 5;

        int    partsDelta  = running ? 1 + RNG.nextInt(3) : 0;
        int    defectDelta = (anomaly && running) ? 1 : 0;
        double energyDelta = r4(current * 0.4 / 1000.0);

        double cycleTarget = 45 + RNG.nextDouble() * 15;
        double cycleActual = running ? cycleTarget * (anomaly ? 1.4 + RNG.nextDouble() : 0.9 + RNG.nextDouble() * 0.3) : 0;

        int hour  = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getHour();
        String shift = hour < 8 ? "C" : hour < 16 ? "A" : "B";

        return MachineEvent.builder()
                .machineId(mid)
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .machineState(state)
                .spindleRpm(r2(rpm))
                .spindleLoadPercent(r2(running ? 40 + RNG.nextDouble() * 55 : 0))
                .feedRateMmMin(r2(running ? 100 + RNG.nextDouble() * 400 : 0))
                .motorCurrentA(r2(current))
                .motorVoltageV(r2(380 + (RNG.nextDouble() - 0.5) * 10))
                .powerKw(r2(current * 0.38))
                .energyKwhDelta(energyDelta)
                .bearingTempC(r2(bearingTemp))
                .motorTempC(r2(bearingTemp - 5 + RNG.nextDouble() * 8))
                .coolantTempC(r2(25 + RNG.nextDouble() * 15))
                .vibrationXMms(r2(vib * (0.8 + RNG.nextDouble() * 0.4)))
                .vibrationYMms(r2(vib * (0.7 + RNG.nextDouble() * 0.6)))
                .vibrationZMms(r2(vib * (0.5 + RNG.nextDouble() * 0.3)))
                .vibrationRmsMms(r2(vib))
                .partsProducedDelta(partsDelta)
                .defectPartsDelta(defectDelta)
                .cycleTimeS(r2(cycleActual))
                .shift(shift)
                .build();
    }

    public String toJson(MachineEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<MachineCatalog> loadMachines() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/machine_catalog.json")) {
            return MAPPER.readValue(is, MAPPER.getTypeFactory().constructCollectionType(List.class, MachineCatalog.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private double r2(double v) { return Math.round(v * 100.0)   / 100.0; }
    private double r4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
