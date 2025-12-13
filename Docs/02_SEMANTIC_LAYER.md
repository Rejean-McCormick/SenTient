# Layer 2: The Semantic Linguist (Falcon 2.0)

**Component:** `nlp_falcon`
**Technology:** Python 3.9+, Flask, ElasticSearch, SBERT (Sentence-BERT)
**Latency Budget:** \~200ms per entity batch (Target) / 15s (Hard Limit)

-----

## 1\. Overview

While Layer 1 (Solr) finds strings that *look like* entities, **Layer 2 (The Linguist)** determines if they *make sense* in the current sentence.

**Philosophy:** "Context is King."
This layer solves the "Paris Problem":

  * Input: *"The Paris Hilton hotel is expensive."*
  * Layer 1 sees: "Paris" (City? Person?), "Hilton" (Hotel? Person?).
  * **Layer 2 decides:** The proximity of "hotel" vectors suggests "Paris" is a location/brand modifier, not the capital of France.

-----

## 2\. The Processing Pipeline

The Python service (`src/main.py`) executes a 3-step NLP pipeline for every "Ambiguous" batch received from the Java Core.

### Phase A: The "Compression" (Stopwords & N-Grams)

Before expensive vector math, we clean the signal using the **Falcon Optimization**:

1.  **Tokenization:** Split sentence into words.
2.  **Stopword Pruning:** Remove non-semantic noise ("the", "is", "at") using `data/stopwords/falcon_extended_en.txt`.
      * *Note:* This list acts as a shared resource; users can update this file to filter domain-specific noise (e.g., "patient", "sample").
3.  **N-Gram Generation:** Create sliding windows of tokens (size 1 to 6) to detect compound predicates (e.g., "Mayor of" -\> `P6`).

### Phase B: Property Extraction (The Edge Detector)

Uniquely, SenTient tries to find the **Relationship (Predicate)** before confirming the **Entity (Subject)**.

  * **Mechanism:** The N-Grams are queried against the `sentient_properties_v1` ElasticSearch index.
  * **Query:** Matches `label` (fuzzy) and `usage_count` (boost).
  * **Result:** If we find "buried in" (P119), we boost candidate entities that are *Locations* (Q2221906) and penalize *People*.

### Phase C: Contextual Vector Scoring (The SBERT Model)

This is the heavy lifting. We calculate the pure semantic distance between the user's row context and the candidate's Wikidata description.

1.  **Embedding:** The context window (surrounding 5 words) is encoded into a 768-dimensional vector using `all-MiniLM-L6-v2`.
2.  **Retrieval:** We fetch the pre-encoded description vectors of the candidates from ElasticSearch (`sentient_entities_fallback`).
3.  **Cosine Similarity:**
    $$S_{Context} = \frac{A \cdot B}{\|A\| \|B\|}$$
      * Where $A$ is the input context vector and $B$ is the candidate description vector.
      * **Output:** A raw float between `0.0` (No relation) and `1.0` (Perfect semantic match).

> **Architectural Note:** This layer does **NOT** apply Levenshtein string distance penalties. That calculation is strictly reserved for the Java Core (Layer 3) to allow independent visualization of "Spelling vs. Meaning" in the UI. Ensure `ranking.levenshtein.enabled` is set to `false` in `falcon_settings.yaml`.

-----

## 3\. Data Integration (ElasticSearch)

Layer 2 relies on two specific indices in ElasticSearch.

### 3.1. `sentient_properties_v1`

Stores Wikidata Properties (P-items).

  * **Mapping:** defined in `config/elastic/falcon_mapping.json`.
  * **Key Fields:**
      * `label`: "place of birth"
      * `expected_types`: ["Q5"] (indicates this property usually applies to humans).
      * `context_vector`: Dense Vector of the property description.

### 3.2. `sentient_entities_fallback`

A partial mirror of Wikidata entities used *only* for re-ranking descriptions.

  * **Why?** Solr (Layer 1) is optimized for IDs and Labels, not long text descriptions. Elastic holds the descriptions for vector comparison.

-----

## 4\. API Interface (Internal)

The Java Orchestrator talks to the Python Layer via REST.

**Endpoint:** `POST /api/v1/disambiguate`

**Request Payload:**

```json
{
  "surface_form": "Paris",
  "context_window": ["Hilton", "hotel", "expensive"],
  "candidates": ["Q90", "Q47796", "Q167646"],
  "limit": 3
}
```

**Response Payload:**

```json
{
  "inferred_property": "P31",
  "ranked_candidates": [
    {
      "id": "Q167646", 
      "label": "Paris Las Vegas", 
      "falcon_score": 0.88,
      "semantic_reason": "High vector overlap with 'hotel'"
    },
    {
      "id": "Q90", 
      "label": "Paris", 
      "falcon_score": 0.12,
      "semantic_reason": "Context mismatch"
    }
  ]
}
```

-----

## 5\. Configuration Strategy

Controlled by `config/nlp/falcon_settings.yaml`.

  * **`embeddings.model_name`:** We strictly use **MiniLM** models. DO NOT use `bert-base-uncased` (too slow for CPU inference).
  * **`thresholds.max_candidates`:** Set to **3** for CPU deployments.
      * *Reason:* Processing 5+ candidates per row on a CPU causes latency to spike \>200ms, risking timeouts in the Java Core.
  * **`preprocessing.stopwords_file`:** This file acts as the primary noise filter. If processing domain-specific data (e.g., medical), add common terms like "patient" or "study" here to force the model to focus on the unique entities.

-----

## 6\. Troubleshooting

  * **Symptom:** "Connection Refused" on port 5005.
      * *Cause:* The Python Flask server died or didn't start. Check `logs/falcon_error.log`.
  * **Symptom:** High Latency (\>1 sec) or "Reconciliation Error" in UI.
      * *Cause:* **Queue Overflow.** The Java Core is sending batches faster than the Python CPU workers can process them.
      * *Fix:* Reduce `server.workers` in `falcon_settings.yaml` to throttle intake, or decrease `max_candidates`.
  * **Symptom:** Weird matches (e.g., matching "Apple" to a fruit in a tech context).
      * *Cause:* The context window is too small. Increase `ranking.context.window_size` in settings to capture more surrounding words.