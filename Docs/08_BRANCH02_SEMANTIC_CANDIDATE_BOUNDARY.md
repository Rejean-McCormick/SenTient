# Branch 02 — SenTient as a noncanonical external candidate producer

**Status:** a locally unit-tested, offline adapter around the actual Falcon API response fields; **not** a complete SenTient service integration, diK importer or Framework canonical author.

## Source shape and output

The existing `src/main.py` endpoint `POST /api/v1/disambiguate` returns `ranked_candidates` entries with `id` (`Q…`), `falcon_score` and `semantic_reason`, plus optional `inferred_property` (`P…`). The new `src/candidate_export.py` function `observation(response, source_ref=..., evidence_sha256=...)` converts a bounded response into an explicitly **unreviewed candidate observation** with stable response SHA-256 and reference to independently preserved source evidence.

It rejects non-Q IDs (including KQ), duplicates, unsafe scores (`NaN`, infinity or out of 0–1), invalid P identifiers, unknown payload shapes, missing evidence digest and error responses. It performs **no network calls, crosswalk writes or canonical ID assignment**. Its format string is an **owner-local candidate label** (`sentient.falcon-candidate-observation/0.1-noncanonical`), NOT an adopted IK/diK/Framework contract.

## Critical trust split

- **SenTient**: may rank and explain candidates only. Falcon score is not external source authenticity, exact identity evidence or permission to register a KQ.
- **diK/Kristalizer**: may inspect human-reviewed proposals under their own versioned contract; they cannot treat model confidence as an approved exactMatch.
- **Framework/Kit**: only the authorized Kit authoring process can register valid canonical state under a pinned Framework profile.

No automatic SenTient→diK ingestion is implemented. Before adoption, both owners must agree on exact source packages, evidence validation, review signatures/KS references, applicable tenant boundaries, acceptance receipts and negative import tests.

## Verification and independent blockers

```bash
python -m unittest -v tests.test_candidate_export
python -m compileall -q src/candidate_export.py
```

These tests verify the pure Python adapter and its rejection cases, not Solr/Java/Flask/DuckDB/React integration. The current Java Solr candidate parser remains a stub, servlet handlers remain unregistered and Java runtime/build evidence is absent from this ZIP. Do not label the full pipeline deployed or qualified from the adapter tests.
