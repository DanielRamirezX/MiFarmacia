package com.mifarmacia.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Productor de ejemplo en Java: crea los tópicos configurados y publica
 * mensajes JSON en ellos de forma periódica.
 */
public final class ProducerApp {

    private static final Logger log = LoggerFactory.getLogger(ProducerApp.class);

    private static final List<String> SUCURSALES = List.of(
            "CDMX-Centro", "CDMX-Roma", "GDL-Chapalita", "MTY-San Pedro", "PUE-Angelopolis");
    private static final List<String> PRODUCTOS = List.of(
            "Paracetamol 500mg", "Ibuprofeno 400mg", "Amoxicilina 500mg", "Loratadina 10mg", "Omeprazol 20mg");

    /** Se baja al recibir SIGINT/SIGTERM para cortar el bucle de envío. */
    private static final CountDownLatch apagado = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        String bootstrap = Config.bootstrapServers();
        List<String> topics = Config.topics();
        String productorId = Config.env("PRODUCER_ID", "java-producer-1");
        long intervaloMs = Config.envInt("PRODUCE_INTERVAL_MS", 1000);
        int total = Config.envInt("MESSAGE_COUNT", 0); // 0 = indefinido
        int particiones = Config.envInt("TOPIC_PARTITIONS", 3);
        short replicas = (short) Config.envInt("TOPIC_REPLICATION_FACTOR", 1);
        Duration esperaMax = Duration.ofMillis(Config.envInt("KAFKA_STARTUP_TIMEOUT_MS", 120_000));

        if (topics.isEmpty()) {
            throw new IllegalArgumentException("KAFKA_TOPICS no puede estar vacío");
        }

        log.info("id={} brokers={} topicos={} intervalo={}ms total={}",
                productorId, bootstrap, topics, intervaloMs, total);

        // El hook libera el bucle y espera a que el productor cierre para que
        // la JVM no termine con envíos a medio confirmar.
        Thread principal = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            apagado.countDown();
            try {
                principal.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown-hook"));

        Config.waitForBrokerAndCreateTopics(bootstrap, topics, particiones, replicas, esperaMax);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, productorId);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);

        Random random = new Random();

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 1; total == 0 || i <= total; i++) {
                String topico = topics.get((i - 1) % topics.size());
                String sucursal = SUCURSALES.get(random.nextInt(SUCURSALES.size()));
                String valor = evento(productorId, topico, i, sucursal,
                        PRODUCTOS.get(random.nextInt(PRODUCTOS.size())),
                        1 + random.nextInt(20),
                        (random.nextInt(90_000) + 1_000) / 100.0);

                ProducerRecord<String, String> record = new ProducerRecord<>(topico, sucursal, valor);
                record.headers().add("origen", productorId.getBytes(StandardCharsets.UTF_8));
                record.headers().add("lenguaje", "java".getBytes(StandardCharsets.UTF_8));

                RecordMetadata meta = producer.send(record).get();
                log.info("-> topico={} particion={} offset={} key={} valor={}",
                        meta.topic(), meta.partition(), meta.offset(), sucursal, valor);

                if (apagado.await(intervaloMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    log.info("Señal de término recibida, deteniendo el envío");
                    break;
                }
            }
        }
        log.info("Productor finalizado");
    }

    /** Construye el JSON del evento. Los valores son controlados, pero las
     *  cadenas se escapan igualmente para no generar JSON inválido. */
    private static String evento(String origen, String topico, int secuencia, String sucursal,
                                 String producto, int cantidad, double precio) {
        return String.format(java.util.Locale.ROOT,
                "{\"id\":\"%s-%06d\",\"origen\":\"%s\",\"lenguaje\":\"java\",\"topico\":\"%s\","
                        + "\"secuencia\":%d,\"sucursal\":\"%s\",\"producto\":\"%s\","
                        + "\"cantidad\":%d,\"precio\":%.2f,\"timestamp\":\"%s\"}",
                esc(origen), secuencia, esc(origen), esc(topico), secuencia,
                esc(sucursal), esc(producto), cantidad, precio, Instant.now());
    }

    private static String esc(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
