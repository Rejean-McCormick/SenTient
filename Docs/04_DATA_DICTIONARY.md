# Data dictionary and wire-contract boundaries

**Snapshot reviewed:** October 27, 2026

Three distinct representations appear in this repository. They are **not automatically interchangeable**:

1. **Logical SmartCell contract:** `schemas/data/smart_cell.json`.
2. **Java OpenRefine-derived object:** `modules/core/src/com/google/refine/model/Cell.java` and `Recon.java`.
3. **Falcon API payload:** request/response constructed in `src/main.py`.

## Logical SmartCell schema

`schemas/data/smart_cell.json` declares fields including `id`, `raw_value`, `fingerprint`, `status`, and `consensus_score` as required; additional fields describe candidates and provenance. It defines an **intended interchange schema**, not evidence that the Java `Cell` serializer produces it.

## Java serialization

| Java model | JSON name | Meaning |
| --- | --- | --- |
| `Cell.value` | `v` | Cell value |
| `Cell.recon` | `r` | Reconciliation state |
| `Cell.id` | `id` | UUID-like tracking ID |
| `Cell.fingerprint` | `fp` | Optional fingerprint; generation is not shown in `Cell` |
| `Recon.judgment` | `j` | `none`, `matched`, `new`, `ambiguous` |
| `Recon.match` | `m` | Selected candidate |
| `Recon.matchRank` | `score` | Match score field |

The Java `Recon` fields `candidates`, `consensusScore`, and `features` are `transient` and marked `@JsonIgnore`, while the logical SmartCell schema expects differently named fields. The sidecar serializes candidates/features separately into JSON. A translation layer is required for reliable schema compliance; one is not established by this snapshot.

## Python Falcon API

Request keys: `surface_form` (string), `context_window` (list), `candidates` (list of IDs), and optional `limit` (integer-like parameter). Response keys: `inferred_property` and `ranked_candidates` with `id`, `falcon_score`, `semantic_reason`. These are the keys generated in the actual Flask endpoint.

Do not confuse `falcon_score` with the Java weighted **consensus score**. Their calculations and semantics are different.

## Persistence schema

The Java sidecar writes row-level state to `sentient_recon`, keyed by `(project_id, row_index)`, with `judgment`, `consensus_score`, `heavy_payload`, and `processed_at`. It also defines `sentient_feedback` for manual corrections. The file `config/storage/duckdb_schema.sql` is a separate artifact; validate migration/compatibility rather than assuming schema parity.

## Verification checklist

- Generate a JSON example from the actual Java serializer and validate against `schemas/data/smart_cell.json`.
- Add a mapping test for statuses and score units (Java candidate score is multiplied by **100**; recon consensus is **0–1**).
- Test persisted/rehydrated candidate fidelity and manual-correction provenance.
- Do not assert `UNRESOLVED`, `BLOCKED`, or `REVIEW_REQUIRED` are executable `Recon.Judgment` values; these names appear in older design docs but not in the Java enum supplied.
