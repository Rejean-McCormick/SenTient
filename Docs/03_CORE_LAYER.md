# Java core, reconciliation, and DuckDB sidecar

**Snapshot reviewed:** October 27, 2026  
**Main files:** `modules/core/src/com/google/refine/SenTientOrchestrator.java`, `RefineServlet.java`, `model/Cell.java`, `model/Recon.java`, `storage/DuckDBStore.java`.

## Java reconciliation flow

The orchestrator defines `reconcileBatch`, an asynchronous chain that requests Solr candidates, invokes Falcon if multiple candidates exist, calculates a weighted score, and attempts to persist candidate data via DuckDB. It uses `CompletableFuture` calls and waits for each batch to finish before sidecar insertion.

Its code-level weighting is:

| Signal | Source / transform | Weight |
| --- | --- | ---: |
| Solr popularity | Sigmoid applied to `tapioca_popularity` | 0.40 |
| Context similarity | `falcon_context` merged from Falcon JSON | 0.30 |
| Spelling similarity | `1 - (Levenshtein distance / max string length)` | 0.30 |

Source-level adjudication: score **>= 0.85** sets `Recon.Judgment.Matched`; **>= 0.40** sets `Ambiguous`; below that sets `None`. These are implementation thresholds, not evaluated precision guarantees. Null/empty values and failure paths require additional testing.

## Critical integration gaps

1. `parseSolrResponse` is incomplete and always returns an empty candidate list; a real score normally cannot progress to Falcon and consensus from live Solr responses.
2. `RefineServlet.init()` has commented-out registrations for `reconcile` and `get-rows`; it implements a generic dispatcher but not a usable registered set of SenTient commands in the snapshot.
3. It creates an empty `Properties` fallback when the servlet context does not already provide `butterfly.properties`; therefore, the layer URLs may be unset.
4. The provided `config/core/butterfly.properties` is written in **YAML-like syntax**, not Java `key=value` syntax matching the keys read by `SenTientOrchestrator` (`sentient.layer1.solr.url`, `sentient.layer2.falcon.url`, timeouts). Validate configuration before integration.
5. `RefineServlet` has no demonstrated complete Java build/dependency pipeline in the snapshot. Compilation and integration have not been validated.

## State and persistence

`Cell` has `v` (raw value), `r` (reconciliation), `id` (UUID), and `fp` (fingerprint) JSON properties. `Recon` defines judgments `None`, `Matched`, `New`, and `Ambiguous`, with a persisted match and transient candidates, consensus score and feature map.

`DuckDBStore` defines `sentient_recon` and `sentient_feedback` tables, methods to insert a batch, read visible rows, count judgments, and log manual corrections. Its existence does not demonstrate that the virtual grid is wired through a registered command or that the database initialization succeeds in the shipped runtime.

The older claims that this implementation is production-ready, supports multi-gigabyte datasets, or guarantees sub-10-ms faceting are unverified and should not be used as release criteria without tests.

See [Data dictionary](04_DATA_DICTIONARY.md), [API](06_API_AND_FRONTEND.md), and [QA](07_QA_AND_VALIDATION.md).
