# SenTient — Semantic Entity Intelligent Transformation

**Declared project version:** `1.0.0-RC3` (see `package.json` and `config/orchestration/environment.json`)  
**Documentation reviewed:** October 27, 2026  
**Engineering status:** Integration under development; **not verified as an end-to-end production release**.

SenTient is a hybrid entity-reconciliation prototype aimed at Wikidata/Wikibase workflows. Its repository contains code for Solr/OpenTapioca-style entity discovery, Falcon-style contextual disambiguation, an OpenRefine-derived Java orchestration layer, a DuckDB sidecar, reconciliation schemas, and React UI components.

The presence of these components does **not** establish that the complete pipeline builds, starts, or achieves the accuracy/latency goals stated in older design documents. This README separates implemented code from unresolved integration work.

## Architecture at a glance

1. **Fast candidate discovery:** Solr core configuration and an OpenTapioca source tree (`server/solr/`, `opentapioca/`).
2. **Contextual re-ranking:** Flask endpoint `POST /api/v1/disambiguate` in `src/main.py` using Sentence Transformers and Elasticsearch.
3. **Consensus and state:** `SenTientOrchestrator.java` combines Solr, Falcon, and Levenshtein signals; `Cell.java` and `Recon.java` model reconciliation state.
4. **Sidecar persistence:** `DuckDBStore.java` contains tables and operations for candidate payloads and feedback.
5. **Presentation:** React components under `src/components/` implement a virtual grid and confidence display, but a complete frontend entry point was not present in the supplied snapshot.

The intended flow is **input → Solr candidates → optional Falcon disambiguation → Java scoring → DuckDB sidecar → UI**. The end-to-end behavior is *not currently demonstrated by the provided code snapshot*.

## Repository map

| Path | Purpose |
| --- | --- |
| `src/main.py` | Falcon Flask HTTP service and vector-scoring implementation |
| `src/falcon/` | Additional preprocessing/pipeline implementation (not the direct Flask entry point) |
| `modules/core/src/com/google/refine/` | Java orchestration, model, servlet, and sidecar code |
| `server/solr/` and `opentapioca/` | Solr core definition and upstream-style tagging source |
| `src/components/` | React grid and confidence components |
| `schemas/` | Proposed JSON contracts; not identical to Java wire serialization |
| `config/` | Service settings, schemas, rules, and deployment intent |
| `evaluation/` and `datasets/` | Evaluation script and small bundled datasets |
| `Docs/` | Architecture, layer-specific references, data model, API, and QA notes |

## Prerequisites and local evaluation

- Docker and the Compose plugin for service experiments. Check the full Compose configuration before starting any service.
- Java 17 (the Compose `core` image references `eclipse-temurin:17-jdk`). The snapshot does not include a verified complete Java build/dependency pipeline.
- Python 3.9+ for the Falcon container (`Dockerfile.falcon` uses Python 3.9); install dependencies from `src/requirements.txt` when running independently.
- Node.js 18+ for the frontend dependencies in `package.json`. The supplied snapshot lacks a root Vite entry point and `tsconfig.json`, so the frontend build is not confirmed.

The file `docker-compose.yml` defines Solr, OpenTapioca, Elasticsearch, Redis, Falcon, and the Java core. Its ports are published on **127.0.0.1** in the snapshot, but Elasticsearch security is disabled there; keep the stack on a trusted development machine.

Inspect the configuration first:

```bash
docker compose config
```

An experimental start, **not a validated installation procedure**, is:

```bash
docker compose up --build
```

Known blockers in this snapshot include a `Dockerfile.falcon` `COPY data/models` instruction without that directory in the supplied archive; a Java core that has no registered reconciliation/get-rows handlers; and a Solr response parser stub. Resolve these before claiming the whole stack is functional. For direct Python execution, create `logs/` before importing `src/main.py`; this file constructs a log handler at import time and loads the embedding model then.

## Available HTTP contract in source

The Python application declares:

- `GET /api/v1/health` — reports service information and an Elasticsearch connectivity boolean; its `"status": "healthy"` value is not a full dependency-readiness guarantee.
- `POST /api/v1/disambiguate` — accepts `surface_form`, `context_window`, `candidates`, and `limit`; returns `ranked_candidates` with `falcon_score` and `semantic_reason`, plus `inferred_property`.

The Java servlet contains a generic command dispatcher, but registration of `/command/core/reconcile` and `/command/core/get-rows` is commented out in `RefineServlet.java`. Do not treat these as verified public endpoints.

## Documentation and limits

Start with [Architecture](Docs/00_ARCHITECTURE_BLUEPRINT.md), [API and frontend](Docs/06_API_AND_FRONTEND.md), and [QA/known gaps](Docs/07_QA_AND_VALIDATION.md). The root `Docs/` name is **capitalized** in this repository.

**Not yet verified from this snapshot:** reproducible end-to-end startup; real Solr candidate parsing into Java records; real command registration; complete UI build; production deployment/security; or the historical precision, recall, and latency targets. Consult the source, contracts, and tests when changing these statements.

## License and third-party code

The repository embeds and adapts components with their own licensing and attribution requirements. Consult the applicable source directories and their license files before redistribution.
