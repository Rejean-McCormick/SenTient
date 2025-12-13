# Layer 3: The Core Orchestrator (Java / OpenRefine Heritage)

**Component:** `core_java`
**Technology:** Java 17, Jetty 10, Butterfly Framework
**Role:** State Management, API Gateway, and Final Adjudication

-----

## 1\. Overview

The **Core Layer** is the stable foundation upon which the experimental AI layers rest. It does not perform NLP; it performs **Management**.

**Philosophy:** "The Database in RAM."
Unlike traditional web apps that query a database per request, the Core loads the entire Project (Rows, Cells, History) into the **Java Heap**. This allows for:

1.  **Instant Faceting:** Filtering 100k rows in \< 10ms.
2.  **Global Transformation:** Applying GREL scripts across the whole dataset instantly.
3.  **Atomic Consistency:** The project state is always valid.

-----

## 2\. The Butterfly Architecture

SenTient retains the **Butterfly** modular framework to handle the HTTP lifecycle.

### 2.1. The Servlet Model

The entry point is `com.google.refine.RefineServlet`. It routes requests based on the Command Pattern.

  * **Endpoint:** `http://localhost:3333/command/core/*`
  * **Routing Logic:**
    1.  Frontend sends `POST /command/core/reconcile`.
    2.  Butterfly looks up the registered **Command** class (`ReconcileCommand`).
    3.  Command triggers a **Process** (`LongRunningProcess`).
    4.  Command returns a JSON `{"code": "ok", "jobID": "123"}` immediately.

### 2.2. The Async Process Manager

Because Layer 2 (Falcon) can take minutes to process large datasets, the Core uses a **Non-Blocking** architecture.

  * **Class:** `com.google.refine.process.ProcessManager`
  * **Mechanism:**
      * The `ProcessManager` maintains a queue of `LongRunningProcess` objects.
      * It uses the `ThreadPoolExecutorAdapter` (configured in `butterfly.properties`) to allocate threads.
      * **Polling:** The Frontend polls `/command/core/get-processes` every 500ms to update the progress bar.

-----

## 3\. The Data Model (The "SmartCell" in Java)

The most significant modification in SenTient is the extension of the `Cell` and `Recon` objects.

### 3.1. The `Cell` Object

Located in `com.google.refine.model.Cell`.
It est the atomic unit of storage. In SenTient, it is polymorphic:

  * **Raw State:** Contains only `value` (String/Number).
  * **Reconciled State:** Contains a `recon` object.

### 3.2. The `EnhancedRecon` Object

We have extended the standard `Recon` class to support the **Consensus Score**.

**Fields added to Java Class:**

```java
public class Recon {
    // Standard Fields
    public long id;
    public String judgment; // MATCHED, NEW, NONE
    public List<ReconCandidate> candidates;

    // SenTient Extensions (Transient - not saved to project history to save space)
    public transient float consensusScore; 
    public transient Map<String, Double> featureVector; // {tapioca: 0.9, falcon: 0.2, levenshtein: 0.95}
}
```

-----

## 4\. Orchestration Logic & Consensus Scoring

The `SenTientOrchestrator` class acts as the bridge, enforcing the three-layer funnel and calculating the final truth score.

### 4.1. The Reconciliation Loop & Timeouts

When the user clicks "Start Reconciliation":

1.  **Batching:** The Core slices the 10,000 rows into batches of 10 (configurable).
2.  **Layer 1 Call (Parallel):**
      * Uses `Http2SolrClient` to hit Solr (`/tag`).
      * **Timeout:** **500ms hard limit** (Aligned with `butterfly.properties`). If Solr exceeds this, the search is cancelled (Fail Fast).
3.  **Ambiguity Check:**
      * If Solr returns a unique, highly popular candidate, the process is short-circuiter.
      * If ambiguous, the Core proceeds to Layer 2.
4.  **Layer 2 Call (Serial/Throttled):**
      * Sends payload to Python (`/disambiguate`) including **Row Context**.

### 4.2. Consensus Scoring (The Adjudication)

Upon receiving scores from Falcon (Layer 2), the Core performs the final, weighted calculation:

#### A. Score Normalization (Critical Step)

The raw log-likelihood score (`log_score`) from Solr must be normalized to a 0-1 scale before weighting, preventing the score from exploding past 1.0.

$$S_{Normalized} = \frac{1}{1 + e^{-k \cdot (\log_{score} - m)}}$$
*Where $k=2.0$ (steepness) and $m=3.0$ (midpoint) are sigmoid constants defined in `environment.json`. This maps the Solr $\log_{10}$ range of [0, 6] to a float [0, 1].*

#### B. Final Consensus Formula

The Core calculates the Levenshtein distance between the raw cell value and the candidate's label, then applies the weights:

$$Score_{Sentry} = (S_{Normalized} \times 0.4) + (S_{Falcon} \times 0.3) + (S_{Levenshtein} \times 0.3)$$

  * **$S_{Falcon}$:** Pure vector similarity received from Python.
  * **$S_{Levenshtein}$:** Calculated by Java (`editdistance.eval()`) normalized to 0-1 (1.0 = perfect match).

<!-- end list -->

5.  **Update:**
      * The `Cell.recon` field is updated with the final `consensusScore` and the raw feature vector.
      * The `History` is *not* updated yet (results are ephemeral until user confirms).

-----

## 5\. History & Serialization (Safety Net)

SenTient treats every data modification as a **Transaction**.

### 5.1. The Command Pattern

Every action (e.g., "Match Cell to Q42") is a class implementing `AbstractOperation`.

  * **Method `createChange()`:** Returns a `Change` object.
  * **Method `apply()`:** Mutates the Project state.
  * **Method `revert()`:** Rolls back the mutation.

### 5.2. Serialization

  * **Format:** JSON (via Jackson) or Binary (Protobuf for large projects).
  * **Storage:** `data/workspace/{project_id}/history/`.
  * **Recovery:** If the server crashes, the Project loads the initial data and re-applies the History log to restore state.

-----

## 6\. QA & Export (The Scrutinizers)

Before data leaves the Core (e.g., to QuickStatements or Wikibase), it passes through the **Scrutinizer Gate**.

  * **Location:** `extensions/wikibase/src/org/openrefine/wikibase/qa/`
  * **Trigger:** `SchemaValidator.validate()`
  * **Mechanism:**
      * The schema is evaluated against the current data.
      * `ConstraintScrutinizer` checks for Wikidata violations (e.g., "Single Value").
      * `FormatScrutinizer` checks Regex patterns (defined in `scrutinizer_rules.yaml`).
  * **Result:** A list of `QAWarning` objects is returned to the UI. The user *cannot* perform the upload if `ERROR` level warnings exist (unless "Strict Mode" is disabled).

-----

## 7\. Performance Tuning

To ensure the Core runs smoothly alongside Solr and Python:

  * **Heap Allocation:**
      * Recommended: `-Xmx4G` (Configured in `environment.json`).
      * The Core is memory-hungry. If it runs out of RAM, it will drop the LRU Cache of reconciliation results, forcing a re-fetch.
  * **Garbage Collection:**
      * Use G1GC: `-XX:+UseG1GC` to prevent long "Stop-the-world" pauses during batch reconciliation.