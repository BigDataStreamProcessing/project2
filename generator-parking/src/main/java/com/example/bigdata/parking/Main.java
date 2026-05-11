package com.example.bigdata.parking;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.bigdata.parking.model.ParkingEvent;

import java.io.InputStream;
import java.util.Properties;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Properties cfg = new Properties();
        try (InputStream is = Main.class.getResourceAsStream("/application.properties")) {
            if (is != null) cfg.load(is);
        }
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) cfg.setProperty(parts[0], parts[1]);
            }
        }
        String  bootstrap = cfg.getProperty("kafka.bootstrap.servers","localhost:9092");
        String  topic     = cfg.getProperty("kafka.topic.parking",    "parking-zdarzenia");
        long    interval  = Long.parseLong(cfg.getProperty("generator.interval.ms",       "3000"));
        double  anomaly   = Double.parseDouble(cfg.getProperty("generator.anomaly.probability","0.05"));
        long    disorder  = Long.parseLong(cfg.getProperty("generator.disorder.max.ms",        "0"));

        ParkingGenerator gen = new ParkingGenerator(anomaly, disorder);
        boolean preview = Boolean.parseBoolean(System.getProperty("preview","false"));

        Properties kp = new Properties();
        kp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrap);
        kp.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        kp.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        log.info("Start generatora parkingu | topic={}", topic);
        if (preview) {
            while (!Thread.currentThread().isInterrupted()) {
                ParkingEvent e = gen.generate();
                System.out.println(gen.toJson(e));
                Thread.sleep(interval);
            }
        } else {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(kp)) {
                while (!Thread.currentThread().isInterrupted()) {
                    ParkingEvent e = gen.generate();
                    producer.send(new ProducerRecord<>(topic, e.getZoneId(), gen.toJson(e)));
                    Thread.sleep(interval);
                }
            }
        }
    }
}