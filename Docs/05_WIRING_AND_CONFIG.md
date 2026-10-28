# Wiring, ports, and configuration

**Snapshot reviewed:** October 27, 2026

## Docker Compose topology (as checked in)

| Compose service | Internal service name | Host-published port | Purpose |
| --- | --- | --- | --- |
| `solr` | `sentient_solr` | `127.0.0.1:8983` | Candidate index/tagging |
| `opentapioca` | `sentient_opentapioca` | `127.0.0.1:8080` | OpenTapioca container |
| `elasticsearch` | `sentient_elastic` | `127.0.0.1:9200` | Semantic index |
| `redis` | `sentient_redis` | `127.0.0.1:6379` | Redis service |
| `falcon` | `sentient_falcon` | `127.0.0.1:5005` | Python HTTP API |
| `core` | `sentient_core` | `127.0.0.1:3333` | Java core image/launcher |

These are **host** bindings. Inside Docker, services communicate over `sentient_net` using container/service DNS; `127.0.0.1` inside a container is not the host or another container.

## Configuration source of truth

- `docker-compose.yml`: declared development services, network, volumes, ports, and startup dependencies.
- `config/nlp/falcon_settings.yaml`: actual values read by `src/main.py` for model, Elasticsearch hosts/index names, stopwords, candidate limit, and Flask host/port.
- `config/orchestration/environment.json`: intended global registry, **not proof** that all programs read it at runtime.
- `config/core/butterfly.properties`: misleading filename; its contents use YAML-like syntax and do not provide the `sentient.layer*` Java property keys shown in `SenTientOrchestrator.java`.
- `config/elastic/falcon_mapping.json` and `server/solr/sentient-tapioca/`: index/core definitions; do not imply populated indexes.

## Starting and diagnosing the development stack

```bash
docker compose config
# Only after reviewing the configuration and prerequisites:
docker compose up --build
```

Startup is **not guaranteed** by this snapshot. `Dockerfile.falcon` attempts `COPY data/models /app/data/models`, but `data/models/` was absent from the supplied archive. `core` runs `./refine` under an Eclipse Temurin JDK image without a verified built Java package. The frontend is not a Compose service here.

When Falcon is running, inspect `GET http://127.0.0.1:5005/api/v1/health` and the `elasticsearch_connected` field; the static `healthy` status alone is not sufficient. Keep Elasticsearch off public networks: Compose explicitly configures `xpack.security.enabled=false`. Do not publish or port-forward these services without an appropriate security design.

## Common mismatches to check

1. Java's configured Solr URL must produce a valid `.../tag` endpoint for the `sentient-tapioca` core.
2. Java's configured Falcon URL must lead to `/api/v1/disambiguate`, not just `/disambiguate` at the server root.
3. `Dockerfile.falcon` uses an explicit **one-worker** Gunicorn command despite a `FALCON_WORKERS=4` environment setting in Compose.
4. The Python settings use `http://sentient_elastic:9200`, consistent with the container name; do not substitute host loopback inside containers.
5. Ensure writable directories and data volumes exist; check per-container healthchecks separately from functional integration tests.
