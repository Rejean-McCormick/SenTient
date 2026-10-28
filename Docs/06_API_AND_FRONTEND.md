# APIs and frontend integration

**Snapshot reviewed:** October 27, 2026

## Verified Python route declarations

`src/main.py` defines:

| Method | Path | Purpose | Code-level caveat |
| --- | --- | --- | --- |
| `GET` | `/api/v1/health` | Returns `service`, `model`, `elasticsearch_connected` and `status` | `status` remains `healthy` even if ES ping fails |
| `POST` | `/api/v1/disambiguate` | Contextual re-ranking of candidate QIDs | Requires model and ES index availability; returns 500 on unhandled failures |

Request:

```json
{
  "surface_form": "Paris",
  "context_window": ["hotel", "expensive"],
  "candidates": ["Q90", "Q47796"],
  "limit": 2
}
```

Response shape:

```json
{
  "inferred_property": null,
  "ranked_candidates": [
    {"id": "Q90", "falcon_score": 0.42, "semantic_reason": "Context match: 42%"}
  ]
}
```

The example values above are illustrative, **not measured results**. The source caps processed candidates at `thresholds.max_candidates` (3 in the included YAML), and it may return an empty list.

## Java command dispatch (incomplete)

`RefineServlet.java` contains code to dispatch command paths and allows the local development frontend origin (`http://127.0.0.1:3000` or `http://localhost:3000`). It **does not register** `reconcile` or `get-rows` in the supplied code; the registration lines are commented out.

`src/components/Grid/VirtualGrid.jsx` attempts to post `application/x-www-form-urlencoded` data to `/command/core/get-rows`, but that should be treated as a **frontend expectation**, not a verified operational API. Likewise, a command-style `/command/{module}/{action}` pattern is a design convention, not a promise that all endpoints exist.

## Frontend project status

`package.json` declares a Vite/React app and scripts (`dev`, `build`, `test`, `e2e`). The supplied source snapshot contains `src/components/Grid/VirtualGrid.jsx` and `src/components/Reconcile/ConfidenceBar.jsx`, but lacks a root `index.html`, Vite config, TS config, and frontend entry point. Therefore, it is **not appropriate to promise a working frontend at port 3000** based on these files alone.

## Integration checklist

- Register concrete server commands and integration-test their routing and error responses.
- Resolve how the frontend calls the Java server (proxy, same-origin, CORS, credentials/CSRF protection).
- Provide the missing build entry/configuration and verify frontend compilation.
- Verify that the serializations sent to `ConfidenceBar` and `VirtualGrid` match actual Java responses and sidecar hydration.
- Avoid exposing Solr, Elasticsearch, Redis, and Falcon directly to untrusted clients.
