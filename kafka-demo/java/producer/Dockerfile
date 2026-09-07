# syntax=docker/dockerfile:1
# Imagen 3 de 4: productor Kafka escrito en Java.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /src

# El pom se copia primero para que la descarga de dependencias quede
# cacheada mientras solo cambie el código fuente.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package

FROM eclipse-temurin:21-jre-alpine

RUN adduser -D -u 10001 kafka
WORKDIR /app

COPY --from=build /src/target/kafka-producer.jar /app/app.jar

USER kafka
ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
    KAFKA_TOPICS=farmacia.pedidos,farmacia.inventario \
    PRODUCER_ID=java-producer-1 \
    PRODUCE_INTERVAL_MS=1000 \
    MESSAGE_COUNT=0 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Las opciones de codificación van fuera de JAVA_OPTS para que sigan
# aplicándose aunque se sobrescriba esa variable: sin ellas la consola de
# la imagen (sin locale) rompe los acentos y los caracteres de recuadro.
ENTRYPOINT ["sh", "-c", "exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 $JAVA_OPTS -jar /app/app.jar"]
