# Layer 1: Ingestion & Fast Tagging (The Sieve)
**Component:** `index_solr` + `core_java (Clustering)`  
**Technology:** Apache Solr 9.x (FST Tagger) + Java String Algorithms  
**Latency Budget:** < 50ms per batch

---

## 1. Overview
The **Ingestion Layer** is responsible for the initial intake of raw data and the high-speed identification of "Surface Forms" (substrings that resemble known entities).

**Philosophy:** "Fail Fast, Filter Early."
This layer does not attempt to understand context. Its only job is to reduce the infinite search space of text into a finite list of Candidate QIDs using strict string matching and pre-calculated popularity.

---

## 2. The Processing Pipeline

Data flows through this layer in three strict phases:

### Phase A: Normalization & Fingerprinting (Client-Side / Java)
Before any network call is made to Solr, the Java Orchestrator performs local deduplication to reduce API load.

**The Algorithm (Key Collision):**
We utilize the **OpenRefine Fingerprint** method:
1.  **Tokenize:** Split string by whitespace.
2.  **Clean:** Remove punctuation and control characters.
3.  **Lowercase:** Convert all tokens to lowercase.
4.  **Sort:** Alphabetize the tokens.
5.  **Deduplicate:** Remove duplicate tokens.
6.  **Join:** Reassemble into a normalized string.

> **Example:**
> * Input 1: "The  University  of... Oxford."
> * Input 2: "Oxford, University of"
> * **Fingerprint (Both):** `of oxford university`
>
> *Result:* Both inputs map to the same Cache Key. We query Solr once, not twice.

### Phase B: The FST Tagger (Server-Side / Solr)
We do not use standard Lucene text search (TF-IDF). We use the **Solr TaggerHandler**.

* **Mechanism:** The entire dictionary of Wikidata labels and aliases (~14M items) is compiled into a **Finite State Transducer (FST)** in memory.
* **Operation:** The raw text acts as a cursor traversing the FST graph.
* **Performance:** Lookup time is $O(k)$ where $k$ is the length of the input text, independent of the index size.

### Phase C: The Authority Filter (Pruning)
Solr returns candidates. We immediately discard any candidate that:
1.  Has a `popularity_score` < 100 (log-likelihood threshold).
2.  Is a "Stop Word" entity (e.g., "The" -> Band "The", "To" -> TV Show "To") unless exact capitalization matches.

---

## 3. Configuration & Schemas

### 3.1. Artificial Aliases (The OpenTapioca Strategy)
To make Solr "smart" without NLP, we inject metadata into the `text` field during the build process. This allows users to search by IDs as if they were names.

| Input Type | Raw Value | Indexed As (Solr `text`) | Resolved QID |
| :--- | :--- | :--- | :--- |
| **Label** | "Douglas Adams" | `douglas adams` | Q42 |
| **Twitter** | "@dna" | `@dna` | Q42 |
| **IMDb** | "nm0000726" | `nm0000726` | Q42 |
| **GRID ID** | "grid.4991.5" | `grid.4991.5` | Q42 |

### 3.2. Solr Request Parameters
The Java Orchestrator must call Solr with these exact parameters to enable FST mode:

```http
POST /solr/sentient-tapioca/tag?overlaps=NO_SUB&tagsLimit=5000&fl=id,label,popularity_score,types
Content-Type: text/plain

<Raw Text Body>