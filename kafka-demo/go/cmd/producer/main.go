// Productor de ejemplo en Go: crea los tópicos configurados y publica
// mensajes JSON en ellos de forma periódica.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/segmentio/kafka-go"

	"github.com/danielramirezx/mifarmacia/kafka-demo/go/internal/kafkacfg"
)

// Evento es el mensaje que viaja por Kafka.
type Evento struct {
	ID        string    `json:"id"`
	Origen    string    `json:"origen"`
	Lenguaje  string    `json:"lenguaje"`
	Topico    string    `json:"topico"`
	Secuencia int       `json:"secuencia"`
	Sucursal  string    `json:"sucursal"`
	Producto  string    `json:"producto"`
	Cantidad  int       `json:"cantidad"`
	Precio    float64   `json:"precio"`
	Timestamp time.Time `json:"timestamp"`
}

var (
	sucursales = []string{"CDMX-Centro", "CDMX-Roma", "GDL-Chapalita", "MTY-San Pedro", "PUE-Angelopolis"}
	productos  = []string{"Paracetamol 500mg", "Ibuprofeno 400mg", "Amoxicilina 500mg", "Loratadina 10mg", "Omeprazol 20mg"}
)

func main() {
	log.SetFlags(0)

	var (
		brokers     = kafkacfg.Brokers()
		topics      = kafkacfg.Topics()
		productorID = kafkacfg.Env("PRODUCER_ID", "go-producer-1")
		intervalo   = kafkacfg.EnvDuration("PRODUCE_INTERVAL_MS", 1000)
		total       = kafkacfg.EnvInt("MESSAGE_COUNT", 0) // 0 = indefinido
		particiones = kafkacfg.EnvInt("TOPIC_PARTITIONS", 3)
		replicas    = kafkacfg.EnvInt("TOPIC_REPLICATION_FACTOR", 1)
		esperaMax   = kafkacfg.EnvDuration("KAFKA_STARTUP_TIMEOUT_MS", 120000)
	)

	if len(topics) == 0 {
		log.Fatal("[producer] KAFKA_TOPICS no puede estar vacío")
	}

	log.Printf("[producer] id=%s brokers=%v topicos=%v intervalo=%s total=%d",
		productorID, brokers, topics, intervalo, total)

	// El contexto se cancela con Ctrl+C o con el SIGTERM de `docker stop`.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	arranque, cancelArranque := context.WithTimeout(ctx, esperaMax)
	conn, err := kafkacfg.WaitForBroker(arranque, brokers, 3*time.Second)
	if err != nil {
		cancelArranque()
		log.Fatalf("[producer] %v", err)
	}
	if err := kafkacfg.EnsureTopics(arranque, conn, topics, particiones, replicas); err != nil {
		conn.Close()
		cancelArranque()
		log.Fatalf("[producer] %v", err)
	}
	conn.Close()
	cancelArranque()

	writer := &kafka.Writer{
		Addr:         kafka.TCP(brokers...),
		Balancer:     &kafka.Hash{}, // misma clave -> misma partición
		RequiredAcks: kafka.RequireAll,
		BatchTimeout: 50 * time.Millisecond,
		Async:        false,
	}
	defer func() {
		if err := writer.Close(); err != nil {
			log.Printf("[producer] error al cerrar el writer: %v", err)
		}
	}()

	if err := publicar(ctx, writer, topics, productorID, intervalo, total); err != nil {
		log.Fatalf("[producer] %v", err)
	}
	log.Println("[producer] finalizado")
}

func publicar(ctx context.Context, w *kafka.Writer, topics []string, productorID string, intervalo time.Duration, total int) error {
	for i := 1; total == 0 || i <= total; i++ {
		topico := topics[(i-1)%len(topics)]
		evento := Evento{
			ID:        fmt.Sprintf("%s-%06d", productorID, i),
			Origen:    productorID,
			Lenguaje:  "go",
			Topico:    topico,
			Secuencia: i,
			Sucursal:  sucursales[rand.Intn(len(sucursales))],
			Producto:  productos[rand.Intn(len(productos))],
			Cantidad:  1 + rand.Intn(20),
			Precio:    float64(rand.Intn(90000)+1000) / 100,
			Timestamp: time.Now().UTC(),
		}

		valor, err := json.Marshal(evento)
		if err != nil {
			return fmt.Errorf("no se pudo serializar el evento %s: %w", evento.ID, err)
		}

		msg := kafka.Message{
			Topic: topico,
			Key:   []byte(evento.Sucursal),
			Value: valor,
			Headers: []kafka.Header{
				{Key: "origen", Value: []byte(productorID)},
				{Key: "lenguaje", Value: []byte("go")},
			},
		}

		if err := w.WriteMessages(ctx, msg); err != nil {
			if ctx.Err() != nil {
				log.Println("[producer] señal de término recibida, deteniendo el envío")
				return nil
			}
			return fmt.Errorf("no se pudo publicar en %s: %w", topico, err)
		}

		log.Printf("[producer] -> topico=%s key=%s valor=%s", topico, evento.Sucursal, valor)

		select {
		case <-ctx.Done():
			log.Println("[producer] señal de término recibida, deteniendo el envío")
			return nil
		case <-time.After(intervalo):
		}
	}
	return nil
}
