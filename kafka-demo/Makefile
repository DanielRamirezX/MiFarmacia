COMPOSE ?= docker compose

.PHONY: help build up down logs logs-go logs-java topics clean images

help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## Construye las 4 imágenes
	$(COMPOSE) build

up: ## Levanta Kafka y los 4 servicios
	$(COMPOSE) up --build

down: ## Detiene y elimina los contenedores (y volúmenes anónimos)
	$(COMPOSE) down -v

logs: ## Sigue los logs de los 4 servicios
	$(COMPOSE) logs -f go-producer go-consumer java-producer java-consumer

logs-go: ## Sigue solo los logs de Go
	$(COMPOSE) logs -f go-producer go-consumer

logs-java: ## Sigue solo los logs de Java
	$(COMPOSE) logs -f java-producer java-consumer

topics: ## Lista los tópicos del broker
	$(COMPOSE) exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list

images: ## Lista las imágenes construidas
	docker images --filter=reference='mifarmacia/kafka-*'

clean: down ## Alias de down
