// Consumidor de ejemplo en Go: se une a un grupo de consumo, lee los
// tópicos configurados e imprime cada mensaje en la consola.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/segmentio/kafka-go"

	"github.com/danielramirezx/mifarmacia/kafka-demo/go/internal/kafkacfg"
)

func main() {
	log.SetFlags(0)

	var (
		brokers     = kafkacfg.Brokers()
		topics      = kafkacfg.Topics()
		grupo       = kafkacfg.Env("KAFKA_GROUP_ID", "farmacia-go-consumers")
		consumidor  = kafkacfg.Env("CONSUMER_ID", "go-consumer-1")
		particiones = kafkacfg.EnvInt("TOPIC_PARTITIONS", 3)
		replicas    = kafkacfg.EnvInt("TOPIC_REPLICATION_FACTOR", 1)
		esperaMax   = kafkacfg.EnvDuration("KAFKA_STARTUP_TIMEOUT_MS", 120000)
	)

	if len(topics) == 0 {
		log.Fatal("[consumer] KAFKA_TOPICS no puede estar vacío")
	}

	log.Printf("[consumer] id=%s grupo=%s brokers=%v topicos=%v",
		consumidor, grupo, brokers, topics)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// El consumidor también asegura los tópicos: así puede arrancar antes
	// que el productor sin quedarse esperando metadatos inexistentes.
	arranque, cancelArranque := context.WithTimeout(ctx, esperaMax)
	conn, err := kafkacfg.WaitForBroker(arranque, brokers, 3*time.Second)
	if err != nil {
		cancelArranque()
		log.Fatalf("[consumer] %v", err)
	}
	if err := kafkacfg.EnsureTopics(arranque, conn, topics, particiones, replicas); err != nil {
		conn.Close()
		cancelArranque()
		log.Fatalf("[consumer] %v", err)
	}
	conn.Close()
	cancelArranque()

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:     brokers,
		GroupID:     grupo,
		GroupTopics: topics,
		MinBytes:    1,
		MaxBytes:    10e6,
		StartOffset: kafka.FirstOffset,
	})
	defer func() {
		if err := reader.Close(); err != nil {
			log.Printf("[consumer] error al cerrar el reader: %v", err)
		}
	}()

	log.Println("[consumer] esperando mensajes... (Ctrl+C para salir)")

	var recibidos int
	for {
		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil || errors.Is(err, io.EOF) {
				log.Printf("[consumer] detenido tras %d mensajes", recibidos)
				return
			}
			log.Fatalf("[consumer] error al leer: %v", err)
		}

		recibidos++
		imprimir(consumidor, recibidos, msg)
	}
}

func imprimir(consumidor string, n int, msg kafka.Message) {
	var cabeceras []string
	for _, h := range msg.Headers {
		cabeceras = append(cabeceras, h.Key+"="+string(h.Value))
	}

	log.Printf("┌─ mensaje #%d recibido por %s", n, consumidor)
	log.Printf("│  topico=%s particion=%d offset=%d", msg.Topic, msg.Partition, msg.Offset)
	log.Printf("│  key=%s timestamp=%s", string(msg.Key), msg.Time.Format(time.RFC3339))
	if len(cabeceras) > 0 {
		log.Printf("│  headers=%s", strings.Join(cabeceras, " "))
	}
	log.Printf("└─ valor=%s", formatear(msg.Value))
}

// formatear muestra el JSON indentado cuando el valor es JSON válido y, en
// caso contrario, devuelve el texto tal cual llegó.
func formatear(valor []byte) string {
	var buf map[string]any
	if err := json.Unmarshal(valor, &buf); err != nil {
		return string(valor)
	}
	bonito, err := json.MarshalIndent(buf, "   ", "  ")
	if err != nil {
		return string(valor)
	}
	return string(bonito)
}
