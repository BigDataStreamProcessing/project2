package com.example.bigdata.gielda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.bigdata.gielda.model.CommodityTickEvent;
import com.example.bigdata.gielda.model.ContractDefinition;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

public class GieldaGenerator {

    private static final Random RNG = new Random();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final List<ContractDefinition> contracts;
    private final double anomalyProbability;
    private final long disorderMaxMs;

    // Stan cenowy per kontrakt – random walk
    private final Map<String, Double> priceState    = new HashMap<>();
    private final Map<String, Double> ma20State     = new HashMap<>();
    private final Map<String, Long>   openInterest  = new HashMap<>();

    public GieldaGenerator(double anomalyProbability, long disorderMaxMs) {
        this.anomalyProbability = anomalyProbability;
        this.disorderMaxMs      = disorderMaxMs;
        this.contracts = loadContracts();
        contracts.forEach(c -> {
            double midPrice = c.getTypicalPriceRangeMin() + RNG.nextDouble() *
                              (c.getTypicalPriceRangeMax() - c.getTypicalPriceRangeMin());
            priceState.put(c.getContractId(), midPrice);
            ma20State.put(c.getContractId(), midPrice);
            openInterest.put(c.getContractId(), 10000L + (long)(RNG.nextDouble() * 50000));
        });
    }

    public CommodityTickEvent generate() {
        ContractDefinition ct = contracts.get(RNG.nextInt(contracts.size()));
        String cid = ct.getContractId();

        int hour = Instant.now().atZone(ZoneId.of("Europe/Warsaw")).getHour();
        boolean marketOpen = (hour >= 9 && hour <= 17);
        String session = !marketOpen ? "AFTER_HOURS"
                       : (hour == 9) ? "PRE_MARKET"
                       : (hour == 17) ? "CLOSE" : "OPEN";

        boolean anomaly = RNG.nextDouble() < anomalyProbability;

        double prev = priceState.get(cid);
        double tick = ct.getTickSizeEUR() > 0 ? ct.getTickSizeEUR() : ct.getTickSizeUSD();
        double move = anomaly
                ? tick * (RNG.nextBoolean() ? 1 : -1) * (20 + RNG.nextInt(30))   // flash crash
                : tick * (RNG.nextDouble() - 0.48) * 4;                           // normalny ruch

        double price = Math.max(ct.getTypicalPriceRangeMin(),
                       Math.min(ct.getTypicalPriceRangeMax(), prev + move));
        priceState.put(cid, price);

        double ma20 = ma20State.get(cid) * 0.95 + price * 0.05;
        ma20State.put(cid, ma20);

        double spread = tick * (1 + RNG.nextDouble() * 2);
        double change    = price - prev;
        double changePct = prev > 0 ? change / prev * 100 : 0;

        long vol = anomaly
                ? (long)(5000 + RNG.nextDouble() * 20000)
                : (long)(100  + RNG.nextDouble() * 2000);

        double range = ct.getTypicalPriceRangeMax() - ct.getTypicalPriceRangeMin();
        double limitPct = 0.05;

        return CommodityTickEvent.builder()
                .contractId(cid)
                .timestamp(Instant.now().minusMillis((long)(RNG.nextDouble() * disorderMaxMs)))
                .price(r2(price))
                .priceChange(r4(change))
                .priceChangePct(r4(changePct))
                .bidPrice(r2(price - spread / 2))
                .askPrice(r2(price + spread / 2))
                .spread(r4(spread))
                .volumeLots(vol)
                .openInterest(openInterest.get(cid) + (long)((RNG.nextDouble() - 0.5) * 100))
                .volumeValueEur(r2(vol * ct.getLotSizeT() * price))
                .high24h(r2(price + RNG.nextDouble() * range * 0.03))
                .low24h(r2(price  - RNG.nextDouble() * range * 0.03))
                .settlementPrice(r2(prev))
                .impliedVolatility(r2(10 + RNG.nextDouble() * (anomaly ? 30 : 10)))
                .rsi14(r2(30 + RNG.nextDouble() * 40 + (anomaly ? 20 : 0)))
                .ma20(r2(ma20))
                .limitUp(changePct > limitPct * 100)
                .limitDown(changePct < -limitPct * 100)
                .flashCrash(anomaly && Math.abs(changePct) > 2.5)
                .session(session)
                .build();
    }

    public String toJson(CommodityTickEvent e) throws Exception { return MAPPER.writeValueAsString(e); }

    private List<ContractDefinition> loadContracts() {
        try (InputStream is = getClass().getResourceAsStream("/dictionary/contracts.json")) {
            return MAPPER.readValue(is, MAPPER.getTypeFactory().constructCollectionType(List.class, ContractDefinition.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private double r2(double v) { return Math.round(v * 100.0)    / 100.0; }
    private double r4(double v) { return Math.round(v * 10000.0)  / 10000.0; }
}