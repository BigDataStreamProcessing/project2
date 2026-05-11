package com.example.bigdata.prosumenci;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.bigdata.prosumenci.model.ProsumentEvent;

import java.util.Properties;

/**
 * Wysyła zdarzenia na temat Kafki.
 * Klucz rekordu = prosumentId → gwarantuje partycjonowanie per prosument.
 */
public class KafkaEventSender implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventSender.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ProsumenciGenerator generator;

    public KafkaEventSender(String bootstrapServers, String topic, ProsumenciGenerator generator) {
        this.topic     = topic;
        this.generator = generator;

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        this.producer = new KafkaProducer<>(props);
    }

    public void sendOne() throws Exception {
        ProsumentEvent event = generator.generate();
        String json  = generator.toJson(event);
        var record   = new ProducerRecord<>(topic, event.getProsumentId(), json);
        producer.send(record, (meta, ex) -> {
            if (ex != null) {
                log.error("Blad wysylki: {}", ex.getMessage());
            } else {
                log.debug("Wysłano do {}:{} – {}", meta.topic(), meta.partition(), event.getProsumentId());
            }
        });
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }
}