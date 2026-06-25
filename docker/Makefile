.PHONY: core pipeline all down logs

core:
	@echo "[INFO]: Starting Core infrastructure (Databases seeded, Kafka inactive)..."
	docker compose --profile core up -d

# Starts Pipeline dependencies: ts_db, Kafka, Connect (ts_db NOT seeded)
pipeline-empty:
	@echo "[INFO]: Starting Pipeline infrastructure (Kafka active, Timescale unseeded)..."
	TS_FLYWAY_LOCATIONS="filesystem:/flyway/sql/schema" docker compose --profile pipeline up -d

# Starts Pipeline dependencies: ts_db, Kafka, Connect (ts_db IS seeded)
pipeline-seeded:
	@echo "[INFO]: Starting Pipeline infrastructure (Kafka active, Timescale seeded)..."
	docker compose --profile pipeline up -d

# Starts Everything: core + pipeline (ts_db NOT seeded)
all-empty:
	@echo "[INFO]: Starting Full Stack (Kafka active, Timescale unseeded)..."
	TS_FLYWAY_LOCATIONS="filesystem:/flyway/sql/schema" docker compose --profile core --profile pipeline up -d

# Starts Everything: core + pipeline (ts_db IS seeded)
all-seeded:
	@echo "[INFO]: Starting Full Stack (Kafka active, Timescale seeded)..."
	docker compose --profile core --profile pipeline up -d

# Stops and removes all containers and networks, keeping volumes intact
down:
	@echo "[INFO]: Stopping all infrastructure..."
	docker compose --profile core --profile pipeline down

# Helper: View logs for all running containers
logs:
	docker compose --profile core --profile pipeline logs -f