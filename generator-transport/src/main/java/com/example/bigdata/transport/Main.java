package com.example.bigdata.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import com.example.bigdata.transport.model.VehiclePositionEvent;

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
        String bootstrap  = cfg.getProperty("kafka.bootstrap.servers", "localhost:9092");
        String topic      = cfg.getProperty("kafka.topic.transport",   "transport-pozycje");
        int    vehicles   = Integer.parseInt(cfg.getProperty("generator.vehicles.count",    "30"));
        long   intervalMs = Long.parseLong(cfg.getProperty("generator.interval.ms",         "5000"));
        double anomaly    = Double.parseDouble(cfg.getProperty("generator.anomaly.probability", "0.06"));
        long   disorder   = Long.parseLong(cfg.getProperty("generator.disorder.max.ms",         "0"));

        TransportGenerator gen = new TransportGenerator(vehicles, anomaly, disorder);
        boolean preview = Boolean.parseBoolean(System.getProperty("preview", "false"));

        Properties kProps = new Properties();
        kProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrap);
        kProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        kProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kProps.put(ProducerConfig.ACKS_CONFIG, "1");

        log.info("Start generatora transportu | topic={} | vehicles={}", topic, vehicles);

        if (preview) {
            while (!Thread.currentThread().isInterrupted()) {
                VehiclePositionEvent event = gen.generate();
                System.out.println(gen.toJson(event));
                Thread.sleep(intervalMs);
            }
        } else {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(kProps)) {
                while (!Thread.currentThread().isInterrupted()) {
                    VehiclePositionEvent event = gen.generate();
                    producer.send(new ProducerRecord<>(topic, event.getVehicleId(), gen.toJson(event)));
                    Thread.sleep(intervalMs);
                }
            }
        }
    }
}