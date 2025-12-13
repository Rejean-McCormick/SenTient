# Data Dictionary & Type Mapping
**Artifact:** `SmartCell` Protocol  
**Schema Source:** `schemas/data/smart_cell.json`  
**Role:** The immutable contract between Java, Python, and the UI.

---

## 1. Introduction
In SenTient, data is never a primitive string. It is always encapsulated in a **SmartCell**.
This document defines the strict typing required to marshall/unmarshall this object across the three application layers.

**Golden Rule:** If a field is not defined here, it must be stripped before serialization.

---

## 2. Type Mapping Matrix

| Logical Field | JSON Type | Java Type (`com.google.refine.*`) | Python Type (`falcon_service`) | TypeScript Interface |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID (String)` | `java.util.UUID` | `str` | `string` |
| `raw_value` | `String` | `String` | `str` | `string` |
| `status` | `Enum (String)` | `Recon.Judgment` | `str` (Literal) | `CellStatus` |
| `consensus_score` | `Float` | `float` (transient) | `float` | `number` |
| `fingerprint` | `String` | `String` | `str` | `string` |
| `candidates` | `Array<Obj>` | `List<ReconCandidate>` | `List[dict]` | `Candidate[]` |
| `vector` | `Array<Float>` | `double[]` | `np.ndarray` | `number[]` |

---

## 3. The SmartCell Core Object

The root container for all cell data.

### `id`
* **Description:** Unique, random identifier assigned at creation. Used to map asynchronous NLP results back to the UI row.
* **Format:** UUID v4.
* **Nullable:** No.

### `raw_value`
* **Description:** The original, immutable user input.
* **Source:** User Upload (CSV/Excel).
* **Note:** This field is **never** modified by the AI. We only modify the `reconciliation` object attached to it.

### `fingerprint`
* **Description:** The "Key Collision" string used for local clustering.
* **Logic:** `lowercase(sort(tokenize(raw_value)))`.
* **Example:** "The Beatles" -> `beatles the`.

### `status`
* **Description:** The current state of the cell in the lifecycle.
* **Allowed Values:**
    * `NEW`: No reconciliation attempted.
    * `PENDING`: Sent to Solr/Falcon, awaiting Async response.
    * `AMBIGUOUS`: Returned with multiple candidates (Score 0.40 - 0.85).
    * `MATCHED`: Confirmed link to a QID (Score > 0.85 or User Action).
    * `REVIEW_REQUIRED`: Auto-matched but flagged by Scrutinizers (QA Error).

### `consensus_score`
* **Description:** The final calculated confidence.
* **Range:** `0.0` to `1.0`.
* **Calculation:** Computed in Java after receiving Python payload.
* **Critical:** If `null`, the UI displays "Processing...".

---

## 4. The Reconciliation Object (`recon`)

This sub-object contains the "AI Opinion".

### `match` (The Golden Record)
* **Type:** `Candidate` Object (Nullable).
* **Description:** The single winning entity. If this exists, the cell is considered "Reconciled".

### `candidates`
* **Type:** List of `Candidate` Objects.
* **Description:** The top $N$ suggestions (default 3) returned by the funnel.
* **Sort Order:** Descending by `consensus_score`.

---

## 5. The Candidate Object

A potential match (e.g., "Paris (Q90)").

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | The Wikidata QID (Must match regex `^Q[0-9]+$`). |
| `label` | `String` | The official label from Wikidata. |
| `types` | `Array<String>` | List of "Instance Of" QIDs (e.g., `["Q5", "Q3618"]`). |
| `description` | `String` | Short description text (e.g., "Capital of France"). |
| `features` | `Object` | The raw scores used for debugging/visualization. |

### `features` Breakdown (Telemetry)
* `tapioca_popularity` (`float`): Raw Log-Likelihood from Solr.
* `falcon_context` (`float`): Cosine Similarity (0-1) from SBERT.
* `levenshtein_distance` (`float`): Normalized string distance (0-1).

---

## 6. The NLP Context Object (`nlp_context`)

Data extracted by Falcon 2.0 to justify the decision.

### `surrounding_ngrams`
* **Type:** `Array<String>`
* **Description:** The context window used for vectorization.
* **Example:** `["born", "in", "honolulu"]` for entity "Barack Obama".

### `inferred_property`
* **Type:** `String` (PID)
* **Description:** The Wikidata Property ID that Falcon detected in the sentence.
* **Example:** `P19` (place of birth).
* **Use Case:** The UI highlights this property to show *why* the entity was chosen.

---

## 7. Pipeline Trace (`pipeline_trace`)

Metadata for debugging distributed transactions.

* `job_id`: The ThreadPoolExecutor ID.
* `tapioca_latency_ms`: Time spent in Layer 1.
* `falcon_latency_ms`: Time spent in Layer 2.
* `engine_version`: Version of the model used (e.g., "Sentient-v1.0").