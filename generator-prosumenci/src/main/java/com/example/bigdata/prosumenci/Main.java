package com.example.bigdata.prosumenci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Punkt startowy generatora prosumentów.
 *
 * Uruchomienie:
 *   mvn exec:java -pl generator-prosumenci
 * lub po spakowaniu:
 *   java -jar generator-prosumenci-1.0-SNAPSHOT.jar
 *
 * Parametry konfiguracyjne: src/main/resources/application.properties
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Properties cfg = loadConfig();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) cfg.setProperty(parts[0], parts[1]);
            }
        }

        String  bootstrap   = cfg.getProperty("kafka.bootstrap.servers", "localhost:9092");
        String  topic       = cfg.getProperty("kafka.topic.prosumenci",  "prosumenci-odczyty");
        int     count       = Integer.parseInt(cfg.getProperty("generator.prosumenci.count", "20"));
        long    intervalMs  = Long.parseLong(cfg.getProperty("generator.interval.ms",       "5000"));
        double  anomalyProb = Double.parseDouble(cfg.getProperty("generator.anomaly.probability", "0.05"));
        long    disorder    = Long.parseLong(cfg.getProperty("generator.disorder.max.ms",         "0"));

        log.info("Start generatora prosumentów | topic={} | prosumentow={} | interval={}ms", topic, count, intervalMs);

        ProsumenciGenerator generator = new ProsumenciGenerator(count, anomalyProb, disorder);

        boolean previewMode = Boolean.parseBoolean(System.getProperty("preview", "false"));

        if (previewMode) {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println(generator.toJson(generator.generate()));
                Thread.sleep(intervalMs);
            }
        } else {
            try (KafkaEventSender sender = new KafkaEventSender(bootstrap, topic, generator)) {
                while (!Thread.currentThread().isInterrupted()) {
                    sender.sendOne();
                    Thread.sleep(intervalMs);
                }
            }
        }
    }

    private static Properties loadConfig() throws Exception {
        Properties props = new Properties();
        try (InputStream is = Main.class.getResourceAsStream("/application.properties")) {
            if (is != null) props.load(is);
        }
        return props;
    }
}