# Demo Kafka: 4 imágenes Docker (Go y Java)

Cuatro imágenes Docker propias sobre un broker de **Apache Kafka en modo KRaft**
(sin ZooKeeper):

| # | Imagen | Lenguaje | Rol |
|---|--------|----------|-----|
| 1 | `mifarmacia/kafka-go-producer:1.0.0` | Go 1.24 | Crea los tópicos y publica mensajes |
| 2 | `mifarmacia/kafka-go-consumer:1.0.0` | Go 1.24 | Consume los tópicos e imprime en consola |
| 3 | `mifarmacia/kafka-java-producer:1.0.0` | Java 21 | Crea los tópicos y publica mensajes |
| 4 | `mifarmacia/kafka-java-consumer:1.0.0` | Java 21 | Consume los tópicos e imprime en consola |

El broker (`apache/kafka:4.1.2`) no es una de las cuatro imágenes: se usa la
oficial, tal como se haría en un entorno real.

Los dos productores escriben en los **mismos tópicos** y cada consumidor usa su
**propio grupo de consumo**, así que ambos consumidores reciben *todos* los
mensajes, vengan de Go o de Java.

```
                    ┌──────────────┐
   go-producer ────►│              │────► go-consumer    (grupo farmacia-go-consumers)
                    │  Kafka       │
 java-producer ────►│  KRaft       │────► java-consumer  (grupo farmacia-java-consumers)
                    └──────────────┘
                     farmacia.pedidos
                     farmacia.inventario
```

## Arranque rápido

```bash
cd kafka-demo
docker compose up --build
```

Esto construye las cuatro imágenes, levanta el broker, espera a que esté sano
(`healthcheck`) y arranca los cuatro contenedores. Para detenerlo y eliminar
los contenedores:

```bash
docker compose down
```

El broker no monta ningún volumen, así que cada `up` arranca con los tópicos
y los offsets en blanco.

También hay atajos en el `Makefile`: `make build`, `make up`, `make logs`,
`make logs-go`, `make logs-java`, `make topics`, `make down`.

## Qué se ve en la consola

Los productores registran cada envío:

```
[producer] -> topico=farmacia.pedidos key=GDL-Chapalita valor={"id":"go-producer-1-000001",...}
```

Y los consumidores imprimen cada mensaje recibido con sus metadatos:

```
┌─ mensaje #1 recibido por go-consumer-1
│  topico=farmacia.pedidos particion=2 offset=0
│  key=GDL-Chapalita timestamp=2026-09-07T07:10:11Z
│  headers=origen=java-producer-1 lenguaje=java
└─ valor={
     "cantidad": 7,
     "id": "java-producer-1-000001",
     "lenguaje": "java",
     ...
   }
```

## Construir las imágenes por separado

```bash
# Go (contexto compartido: un solo módulo con dos binarios)
docker build -f go/Dockerfile.producer -t mifarmacia/kafka-go-producer:1.0.0 ./go
docker build -f go/Dockerfile.consumer -t mifarmacia/kafka-go-consumer:1.0.0 ./go

# Java (un proyecto Maven independiente por imagen)
docker build -t mifarmacia/kafka-java-producer:1.0.0 ./java/producer
docker build -t mifarmacia/kafka-java-consumer:1.0.0 ./java/consumer
```

Y ejecutar una imagen suelta contra un Kafka ya levantado:

```bash
docker run --rm --network kafka-demo_kafka-net \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e KAFKA_TOPICS=farmacia.pedidos \
  -e MESSAGE_COUNT=5 \
  mifarmacia/kafka-go-producer:1.0.0
```

## Variables de entorno

Todas las imágenes leen la misma configuración:

| Variable | Por omisión | Aplica a | Descripción |
|----------|-------------|----------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | todas | Lista de brokers separada por comas |
| `KAFKA_TOPICS` | `farmacia.pedidos,farmacia.inventario` | todas | Tópicos separados por comas |
| `TOPIC_PARTITIONS` | `3` | todas | Particiones al crear los tópicos |
| `TOPIC_REPLICATION_FACTOR` | `1` | todas | Factor de réplica al crear los tópicos |
| `KAFKA_STARTUP_TIMEOUT_MS` | `120000` | todas | Tiempo máximo esperando al broker |
| `PRODUCER_ID` | `go-producer-1` / `java-producer-1` | productores | Identificador del productor |
| `PRODUCE_INTERVAL_MS` | `1000` | productores | Pausa entre mensajes |
| `MESSAGE_COUNT` | `0` | productores | Mensajes a enviar; `0` = indefinido |
| `KAFKA_GROUP_ID` | `farmacia-go-consumers` / `farmacia-java-consumers` | consumidores | Grupo de consumo |
| `CONSUMER_ID` | `go-consumer-1` / `java-consumer-1` | consumidores | Identificador del consumidor |

## Detalles de implementación

**Creación de tópicos.** El broker arranca con `auto.create.topics.enable=false`
a propósito: los tópicos los crean explícitamente las aplicaciones
(`CreateTopics` en Go, `AdminClient` en Java). La operación es idempotente
—si el tópico ya existe se ignora el error— así que cualquiera de los cuatro
contenedores puede arrancar primero.

**Espera del broker.** Además del `healthcheck` de Compose, cada aplicación
reintenta la conexión hasta `KAFKA_STARTUP_TIMEOUT_MS`, de modo que las
imágenes también funcionan fuera de Compose.

**Apagado ordenado.** Los cuatro programas atienden `SIGINT`/`SIGTERM`: los
productores terminan el envío en curso y cierran el `writer`; los consumidores
hacen `commit` de lo procesado y cierran la sesión del grupo (en Java vía
`consumer.wakeup()`), así el rebalanceo del grupo es inmediato.

**Entrega.** Los productores usan `acks=all` (y en Java además idempotencia) y
los consumidores hacen *commit* manual después de imprimir el lote, para no
perder mensajes ante un reinicio.

**Imágenes.** Las cuatro usan compilación multi-etapa: el compilador y Maven
quedan en la etapa de build y no viajan en la imagen final. Go produce un
binario estático sobre `alpine:3.21`; Java un *fat jar* sobre
`eclipse-temurin:21-jre-alpine`. Los contenedores corren con un usuario sin
privilegios (`kafka`, uid 10001).

## Estructura

```
kafka-demo/
├── docker-compose.yml          # broker + los 4 servicios
├── Makefile
├── go/
│   ├── Dockerfile.producer     # imagen 1
│   ├── Dockerfile.consumer     # imagen 2
│   ├── go.mod / go.sum         # dependencia: segmentio/kafka-go
│   ├── cmd/producer/main.go
│   ├── cmd/consumer/main.go
│   └── internal/kafkacfg/      # configuración y arranque compartidos
└── java/
    ├── producer/               # imagen 3 (proyecto Maven propio)
    │   ├── Dockerfile
    │   ├── pom.xml             # dependencia: org.apache.kafka:kafka-clients
    │   └── src/main/java/com/mifarmacia/kafka/{ProducerApp,Config}.java
    └── consumer/               # imagen 4 (proyecto Maven propio)
        ├── Dockerfile
        ├── pom.xml
        └── src/main/java/com/mifarmacia/kafka/{ConsumerApp,Config}.java
```

## Desarrollo sin Docker

Con un broker escuchando en `localhost:29092` (el listener externo del
`docker-compose.yml`):

```bash
# Go
cd go
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 go run ./cmd/producer
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 go run ./cmd/consumer

# Java
cd java/producer && mvn -q clean package
KAFKA_BOOTSTRAP_SERVERS=localhost:29092 java -jar target/kafka-producer.jar
```

## Inspeccionar el broker

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:9092 --describe

docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 --describe --all-groups
```
