# Falcon contextual disambiguation service

**Snapshot reviewed:** October 27, 2026  
**Source entry point:** `src/main.py`  
**Configured model:** `all-MiniLM-L6-v2` in `config/nlp/falcon_settings.yaml`.

## Available source-level endpoints

- `GET /api/v1/health`: returns `status`, `service`, `model`, and `elasticsearch_connected`. Note that the literal `status: healthy` is returned even when Elasticsearch ping is false; examine the boolean separately.
- `POST /api/v1/disambiguate`: accepts candidate IDs and context and returns a ranked list.

Example request:

```http
POST /api/v1/disambiguate
Content-Type: application/json
```

```json
{
  "surface_form": "Paris",
  "context_window": ["Hilton", "hotel", "expensive"],
  "candidates": ["Q90", "Q47796"],
  "limit": 2
}
```

Example response **shape only** (not a real model result):

```json
{
  "inferred_property": null,
  "ranked_candidates": [
    {"id": "Q90", "falcon_score": 0.42, "semantic_reason": "Context match: 42%"}
  ]
}
```

## What the main endpoint does

1. Loads settings and initializes Sentence Transformers and an Elasticsearch client at module import time.
2. Filters context tokens using `data/stopwords/falcon_extended_en.txt`.
3. Searches the configured property index for a fuzzy label match and an optional `pid`.
4. Fetches candidate descriptions with Elasticsearch `mget`.
5. Embeds the **input surface form plus filtered context** and each candidate description (or falls back to the surface form), computes cosine similarity, clamps scores to **0–1**, then sorts descending.
6. Limits processing to `min(request.limit, thresholds.max_candidates)`, where the checked-in configuration sets **3** as the maximum.

`src/falcon/pipeline.py` and `src/falcon/preprocessing.py` define additional pipeline classes, but `src/main.py` implements its own route logic; it does not call `FalconPipeline.run` in this snapshot. Do not describe those classes as executing automatically within the Flask handler.

## Caveats and deployment

- The old claim that this model produces **768-dimensional embeddings** is inaccurate for the configured MiniLM-L6-v2 model (384 dimensions).
- Description vectors are computed by the route; it does **not** read precomputed vectors from Elasticsearch as the old document claimed.
- The Elasticsearch indices `sentient_properties_v1` and `sentient_entities_fallback` are named in config; this archive does not prove they are populated.
- `src/main.py` creates `logs/falcon_service.log` on import. Direct execution requires a writable `logs/` directory, whereas `Dockerfile.falcon` creates one.
- `Dockerfile.falcon` specifies one Gunicorn worker, while `docker-compose.yml` advertises `FALCON_WORKERS=4` as an environment variable; the explicit Docker command determines the worker count unless modified.
- The Java orchestrator builds its Falcon path by appending `/disambiguate` to a configurable base URL. Confirm it resolves to **`/api/v1/disambiguate`** before testing integration.

No production latency or classification quality is established by these source files. See [QA](07_QA_AND_VALIDATION.md).
