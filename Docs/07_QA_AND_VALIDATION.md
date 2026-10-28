# QA, benchmarks, and unresolved integration risks

**Snapshot reviewed:** October 27, 2026  
**Evaluation status:** source inspection only; no full integration, performance, accuracy, or deployment benchmark was run.

## Available validation materials

- `evaluation/evaluate_falcon_api.py`: a Python evaluator containing data loading, API evaluation, and CSV reporting logic.
- `datasets/lcquad2_test.json`, `datasets/simplequestions.txt`, and `datasets/webqsp.test.entities.json`: small bundled dataset samples; **not** proven to contain full canonical benchmark populations.
- `config/qa/scrutinizer_rules.yaml`: QA rule declarations.
- `extensions/wikibase/src/org/openrefine/wikibase/qa/`: Java scrubber/scrutinizer classes.
- `schemas/data/smart_cell.json` and `schemas/api/reconciliation_protocol.json`: intended structural contracts.
- `opentapioca/opentapioca/tests/`: upstream-style Python tests for included OpenTapioca code; not proof that SenTient's Java/Python stack passes integration.

The earlier documentation claimed **5,000+ LC-QuAD questions** in the bundled test file. The supplied `datasets/lcquad2_test.json` is only a few kilobytes, so that volume must not be claimed. Likewise, precision/recall targets and sub-50-ms/sub-second performance are unmeasured goals.

## Recommended test gates

1. **Packaging and startup:** verify Compose syntax, build paths, Java build artifacts, Python model loading, and datastore availability.
2. **Solr contract:** index deterministic fixtures; ensure a live response maps to nonempty `ReconCandidate` records (currently not implemented).
3. **Falcon API:** test success, empty candidates, invalid payloads, missing ES indexes, and model loading; record versions and environment.
4. **Orchestration:** register concrete handlers and verify the complete Solr → Falcon → score → store path with controlled input.
5. **Data model:** compare Java serializer output to JSON Schema and sidecar rehydration; test judgment enum mapping.
6. **Frontend:** rebuild from a complete entry point, validate request/response shapes, keyboard use, and CORS/CSRF handling.
7. **Accuracy and latency:** use versioned datasets with documented split, ground truth, precision, recall, F1, per-layer latency distributions, and error analysis.
8. **Security:** verify service bindings, export permission checks, dependency authentication, and secure configuration before any external deployment.

## Known blockers found in this snapshot

| Area | Evidence | Consequence |
| --- | --- | --- |
| Solr → Java | `SenTientOrchestrator.parseSolrResponse` returns an empty list | No real tagger candidates enter the Java scorer |
| HTTP commands | `RefineServlet` command registrations commented out | `/command/core/reconcile` and `/command/core/get-rows` are not demonstrated |
| Java configuration | `config/core/butterfly.properties` uses YAML-like syntax without expected Java keys | Layer URL binding is uncertain |
| Docker build | `Dockerfile.falcon` copies absent `data/models/` | Build can fail before service starts |
| Frontend | Root Vite/TS files absent from archive | Complete frontend build not verified |
| Data contract | Java compact `v`/`r`/`fp` vs logical schema fields | Serialization mapping needs explicit testing |
| Security | Compose disables Elasticsearch security | Not suitable for an exposed or production environment as-is |

## Acceptance criteria

Do not promote the project to a verified production/`FINAL` status until the above tests pass with recorded evidence. The project metadata label `1.0.0-RC3` is retained as a **declared version**, not an independently verified release qualification.
