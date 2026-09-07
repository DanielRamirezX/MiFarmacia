// Package kafkacfg concentra la lectura de variables de entorno y las
// utilidades de arranque (espera del broker y creación de tópicos) que
// comparten el productor y el consumidor.
package kafkacfg

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/segmentio/kafka-go"
)

// Brokers devuelve la lista de brokers a partir de KAFKA_BOOTSTRAP_SERVERS.
func Brokers() []string {
	return splitCSV(Env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
}

// Topics devuelve los tópicos configurados en KAFKA_TOPICS.
func Topics() []string {
	return splitCSV(Env("KAFKA_TOPICS", "farmacia.pedidos,farmacia.inventario"))
}

// Env lee una variable de entorno y aplica un valor por omisión.
func Env(key, def string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return def
}

// EnvInt lee una variable de entorno numérica y aplica un valor por omisión.
func EnvInt(key string, def int) int {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return def
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		log.Printf("[config] %s=%q no es un entero válido, se usa %d", key, raw, def)
		return def
	}
	return n
}

// EnvDuration lee una variable de entorno expresada en milisegundos.
func EnvDuration(key string, defMillis int) time.Duration {
	return time.Duration(EnvInt(key, defMillis)) * time.Millisecond
}

func splitCSV(raw string) []string {
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}

// WaitForBroker reintenta la conexión hasta que el clúster responda o se
// agote el contexto. Evita que el contenedor muera cuando Kafka todavía
// está arrancando.
func WaitForBroker(ctx context.Context, brokers []string, retryEvery time.Duration) (*kafka.Conn, error) {
	var lastErr error
	for attempt := 1; ; attempt++ {
		conn, err := kafka.DialContext(ctx, "tcp", brokers[0])
		if err == nil {
			if _, err = conn.Brokers(); err == nil {
				log.Printf("[kafka] conectado a %s (intento %d)", brokers[0], attempt)
				return conn, nil
			}
			conn.Close()
		}
		lastErr = err
		log.Printf("[kafka] broker %s no disponible (intento %d): %v", brokers[0], attempt, err)

		select {
		case <-ctx.Done():
			return nil, fmt.Errorf("no se pudo conectar a %s: %w", brokers[0], errors.Join(lastErr, ctx.Err()))
		case <-time.After(retryEvery):
		}
	}
}

// EnsureTopics crea los tópicos indicados si aún no existen. Kafka responde
// sin error cuando el tópico ya está creado, así que la operación es
// idempotente y puede ejecutarse en cada arranque.
func EnsureTopics(ctx context.Context, conn *kafka.Conn, topics []string, partitions, replication int) error {
	controller, err := conn.Controller()
	if err != nil {
		return fmt.Errorf("no se pudo obtener el controller: %w", err)
	}

	addr := net.JoinHostPort(controller.Host, strconv.Itoa(controller.Port))
	ctrlConn, err := kafka.DialContext(ctx, "tcp", addr)
	if err != nil {
		return fmt.Errorf("no se pudo conectar al controller %s: %w", addr, err)
	}
	defer ctrlConn.Close()

	configs := make([]kafka.TopicConfig, 0, len(topics))
	for _, t := range topics {
		configs = append(configs, kafka.TopicConfig{
			Topic:             t,
			NumPartitions:     partitions,
			ReplicationFactor: replication,
		})
	}

	if err := ctrlConn.CreateTopics(configs...); err != nil {
		return fmt.Errorf("no se pudieron crear los tópicos %v: %w", topics, err)
	}
	log.Printf("[kafka] tópicos listos: %s (particiones=%d, réplicas=%d)",
		strings.Join(topics, ", "), partitions, replication)
	return nil
}
