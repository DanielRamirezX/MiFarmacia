package com.mifarmacia.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lectura de variables de entorno y utilidades de arranque (espera del
 * broker y creación de tópicos) compartidas por productor y consumidor.
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private Config() {
    }

    /** Lee una variable de entorno aplicando un valor por omisión. */
    public static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /** Lee una variable de entorno numérica aplicando un valor por omisión. */
    public static int envInt(String key, int defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("{}={} no es un entero válido, se usa {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /** Lee una lista separada por comas. */
    public static List<String> envList(String key, String defaultValue) {
        List<String> out = new ArrayList<>();
        for (String part : env(key, defaultValue).split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public static String bootstrapServers() {
        return env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    }

    public static List<String> topics() {
        return envList("KAFKA_TOPICS", "farmacia.pedidos,farmacia.inventario");
    }

    /**
     * Espera a que el clúster responda y crea los tópicos que falten. Kafka
     * puede tardar en arrancar, así que se reintenta hasta agotar el tiempo
     * máximo configurado en KAFKA_STARTUP_TIMEOUT_MS.
     */
    public static void waitForBrokerAndCreateTopics(String bootstrapServers,
                                                    Collection<String> topics,
                                                    int partitions,
                                                    short replicationFactor,
                                                    Duration timeout) throws InterruptedException {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000);

        long deadline = System.nanoTime() + timeout.toNanos();
        int attempt = 0;

        while (true) {
            attempt++;
            try (Admin admin = Admin.create(props)) {
                int nodes = admin.describeCluster().nodes().get().size();
                log.info("Conectado a Kafka en {} ({} nodo(s), intento {})", bootstrapServers, nodes, attempt);

                List<NewTopic> nuevos = topics.stream()
                        .map(t -> new NewTopic(t, partitions, replicationFactor))
                        .toList();
                try {
                    admin.createTopics(nuevos).all().get();
                    log.info("Tópicos creados: {} (particiones={}, réplicas={})", topics, partitions, replicationFactor);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TopicExistsException) {
                        log.info("Tópicos ya existentes, no se recrean: {}", topics);
                    } else {
                        throw e;
                    }
                }
                return;
            } catch (ExecutionException | RuntimeException e) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException(
                            "No se pudo preparar Kafka en " + bootstrapServers + " tras " + attempt + " intentos", e);
                }
                log.warn("Kafka aún no está disponible en {} (intento {}): {}",
                        bootstrapServers, attempt, e.getMessage());
                Thread.sleep(3000);
            }
        }
    }
}
