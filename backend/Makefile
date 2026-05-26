SHELL := /bin/bash
OSM_URL := https://download.geofabrik.de/south-america/argentina-latest.osm.pbf
DATA_DIR := osm-data
OSRM_IMAGE := ghcr.io/project-osrm/osrm-backend:latest

.PHONY: osm-download osrm-prep up down logs test run

osm-download:
	mkdir -p $(DATA_DIR)
	test -f $(DATA_DIR)/argentina-latest.osm.pbf || \
		wget --show-progress -O $(DATA_DIR)/argentina-latest.osm.pbf $(OSM_URL)

osrm-prep: osm-download
	docker run --rm -t -v $$PWD/$(DATA_DIR):/data $(OSRM_IMAGE) \
		osrm-extract -p /opt/car.lua /data/argentina-latest.osm.pbf
	docker run --rm -t -v $$PWD/$(DATA_DIR):/data $(OSRM_IMAGE) \
		osrm-partition /data/argentina-latest.osrm
	docker run --rm -t -v $$PWD/$(DATA_DIR):/data $(OSRM_IMAGE) \
		osrm-customize /data/argentina-latest.osrm

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f --tail=100

test:
	pytest -q

run:
	uvicorn main:app --host 127.0.0.1 --port 8000 --reload
