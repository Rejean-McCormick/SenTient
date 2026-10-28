# Ingestion and fast entity tagging

**Snapshot reviewed:** October 27, 2026  
**Scope:** Solr/OpenTapioca candidate discovery, not semantic disambiguation.

## Design

The proposed fast path identifies matching labels and aliases and passes candidate IDs to the core for further judgment. The repository contains a Solr core (`server/solr/sentient-tapioca/`), a separate OpenTapioca source tree (`opentapioca/`), and a Java request to the Solr tagging endpoint.

`SenTientOrchestrator.callLayer1Solr` constructs an HTTP request using `sentient.layer1.solr.url` and appends:

```text
/tag?overlaps=NO_SUB&tagsLimit=10&fl=id,label,popularity_score,types
```

The code sends the cell's text as `text/plain` and defaults to a **500 ms** per-request timeout from `sentient.layer1.timeout_ms`. An older document specified `tagsLimit=5000`; that is not the value in this Java call.

## Current implementation limits

- The method `parseSolrResponse(String jsonBody)` creates a JSON object but contains a TODO-style placeholder and returns an **empty candidate list**. Consequently, the Java reconciliation funnel cannot be claimed functional as delivered.
- The design document described a particular OpenRefine fingerprinting algorithm and Redis deduplication, but the observed Java orchestrator does not execute that algorithm; `Cell.fingerprint` exists as a field. Treat deduplication/cache behavior as **intended**, not demonstrated.
- Solr schemas and the included OpenTapioca project are not evidence that a Wikidata index with millions of aliases has been built or populated.
- There is no measured evidence for the older **under-50-ms** latency claim.

## Configuration and verification

- Review `docker-compose.yml`, `server/solr/sentient-tapioca/`, and `config/solr/tapioca_schema.xml` for the actual development core layout.
- Confirm the `solr-precreate` step succeeds, populate the index with test data, and probe the tag endpoint directly.
- Implement and test the Solr JSON response mapping to `ReconCandidate` before enabling any end-to-end status claims.
- Check the exact Solr URL and request timeout assigned to the Java core at runtime; they are not safely inferred from the conceptual topology.

See [Architecture](00_ARCHITECTURE_BLUEPRINT.md) and [QA](07_QA_AND_VALIDATION.md).
