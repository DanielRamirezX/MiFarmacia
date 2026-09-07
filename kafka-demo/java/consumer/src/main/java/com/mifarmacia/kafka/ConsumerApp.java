package com.mifarmacia.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumidor de ejemplo en Java: se une a un grupo de consumo, lee los
 * tópicos configurados e imprime cada mensaje en la consola.
 */
public final class ConsumerApp {

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);

    public static void main(String[] args) throws Exception {
        String bootstrap = Config.bootstrapServers();
        List<String> topics = Config.topics();
        String grupo = Config.env("KAFKA_GROUP_ID", "farmacia-java-consumers");
        String consumidorId = Config.env("CONSUMER_ID", "java-consumer-1");
        int particiones = Config.envInt("TOPIC_PARTITIONS", 3);
        short replicas = (short) Config.envInt("TOPIC_REPLICATION_FACTOR", 1);
        Duration esperaMax = Duration.ofMillis(Config.envInt("KAFKA_STARTUP_TIMEOUT_MS", 120_000));

        if (topics.isEmpty()) {
            throw new IllegalArgumentException("KAFKA_TOPICS no puede estar vacío");
        }

        log.info("id={} grupo={} brokers={} topicos={}", consumidorId, grupo, bootstrap, topics);

        // El consumidor también asegura los tópicos: así puede arrancar antes
        // que el productor sin quedarse esperando metadatos inexistentes.
        Config.waitForBrokerAndCreateTopics(bootstrap, topics, particiones, replicas, esperaMax);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, grupo);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, consumidorId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        AtomicBoolean corriendo = new AtomicBoolean(true);

        // wakeup() es el mecanismo seguro para interrumpir un poll() desde
        // otro hilo; el bucle principal lo traduce en una salida ordenada.
        Thread principal = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            corriendo.set(false);
            consumer.wakeup();
            try {
                principal.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));

        long recibidos = 0;
        try {
            consumer.subscribe(topics);
            log.info("Esperando mensajes... (Ctrl+C para salir)");

            while (corriendo.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> record : records) {
                    imprimir(consumidorId, ++recibidos, record);
                }
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            if (corriendo.get()) {
                throw e; // wakeup inesperado: no venía del apagado
            }
        } finally {
            try {
                consumer.close(Duration.ofSeconds(10));
            } finally {
                log.info("Consumidor detenido tras {} mensajes", recibidos);
            }
        }
    }

    private static void imprimir(String consumidorId, long n, ConsumerRecord<String, String> record) {
        StringJoiner headers = new StringJoiner(" ");
        for (Header h : record.headers()) {
            headers.add(h.key() + "=" + new String(h.value(), StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder("\n");
        sb.append("┌─ mensaje #").append(n).append(" recibido por ").append(consumidorId).append('\n');
        sb.append("│  topico=").append(record.topic())
          .append(" particion=").append(record.partition())
          .append(" offset=").append(record.offset()).append('\n');
        sb.append("│  key=").append(record.key())
          .append(" timestamp=").append(Instant.ofEpochMilli(record.timestamp())).append('\n');
        if (headers.length() > 0) {
            sb.append("│  headers=").append(headers).append('\n');
        }
        sb.append("└─ valor=").append(record.value());
        log.info(sb.toString());
    }
}
