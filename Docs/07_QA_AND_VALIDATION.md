# QA, Validation & Benchmarking
**Component:** Quality Assurance  
**Role:** Verification of Accuracy (Precision/Recall) and Performance (Latency).

---

## 1. Overview
In a probabilistic system like SenTient, "It works on my machine" is not enough. We must statistically prove that the system improves over time.

**The QA Strategy relies on 3 Pillars:**
1.  **Automated Unit Tests:** Checking the code logic (Java/Python).
2.  **Semantic Scrutinizers:** Checking the data validity (Rules).
3.  **Golden Standard Benchmarks:** Checking the AI accuracy (Datasets).

---

## 2. The Scrutinizers (Runtime Validation)

Scrutinizers are "Linting Rules for Data". They run automatically in the Java Core *before* any export action.

**Configuration:** `config/qa/scrutinizer_rules.yaml`

### 2.1. Integrity Scrutinizers
* **Class:** `org.openrefine.wikibase.qa.IntegrityScrutinizer`
* **Checks:**
    * Does every `MATCHED` cell have a valid QID?
    * Are there any "Ghost Cells" (Status matched, but ID null)?
* **Action:** Blocks export with `FATAL` error.

### 2.2. Constraint Scrutinizers (Wikidata Alignment)
* **Class:** `org.openrefine.wikibase.qa.ConstraintScrutinizer`
* **Checks:**
    * **Single Value Constraint:** e.g., A country can only have one Capital.
    * **Format Constraint:** e.g., Dates must be ISO 8601.
    * **Inverse Constraint:** If A is father of B, B must be child of A.
* **Action:** Shows `WARNING` in the UI. User can override unless "Strict Mode" is on.

### 2.3. Consensus Scrutinizers (SenTient Exclusive)
* **Logic:** Checks for statistical anomalies in the scores.
* **Rule:** "The Paris Hilton Rule"
    * `IF tapioca_popularity > 0.95 AND falcon_context < 0.1`
    * **Alert:** "You selected a very famous entity that does not fit the context."

---

## 3. Golden Standard Datasets

We do not guess if the model is good. We measure it against ground truth.

### 3.1. LC-QuAD 2.0 (Large Scale Question Answering)
* **File:** `datasets/lcquad2_test.json`
* **Size:** 5000+ Questions.
* **Use Case:** Validating complex relation extraction.
* **Metric:** F-Score (Harmonic mean of Precision and Recall).

### 3.2. SimpleQuestions (Wikidata Version)
* **File:** `datasets/simplequestions.txt`
* **Format:** `subject_qid \t property_pid \t object_qid \t question`
* **Use Case:** Validating simple entity spotting speed.
* **Target:** Latency < 50ms/query.

### 3.3. WebQSP (Web Questions Semantic Parse)
* **File:** `datasets/webqsp.test.entities.json`
* **Use Case:** Testing disambiguation of ambiguous surface forms.

---

## 4. Benchmarking Scripts

Located in `evaluation/`. These scripts run the full pipeline against the datasets.

### `evaluate_falcon_api.py`
**Command:**
```bash
python evaluation/evaluate_falcon_api.py --dataset lcquad2 --output results/benchmark_v1.csv
````

**Output Matrix:**
| Metric | Target (v1.0) | Acceptable Range |
| :--- | :--- | :--- |
| **Precision** | 0.85 | \> 0.80 |
| **Recall** | 0.82 | \> 0.75 |
| **F-Score** | 0.83 | \> 0.78 |
| **Latency (p95)** | 200ms | \< 500ms |

**Logic:**
The script compares the `SmartCell.recon.match.id` output by SenTient against the QID in the Golden Standard file.

-----

## 5\. Continuous Integration (CI) Pipeline

Every Pull Request triggers the following QA actions via GitHub Actions.

### 5.1. Java Core Tests (`mvn test`)

  * Checks `StatementMerger` logic.
  * Checks History serialization (Undo/Redo safety).
  * Checks GREL function correctness.

### 5.2. Python NLP Tests (`pytest`)

  * Checks `stopwords.py` logic.
  * Checks ElasticSearch connectivity.
  * **Mini-Benchmark:** Runs a subset (100 rows) of SimpleQuestions to ensure no massive regression in accuracy.

### 5.3. End-to-End (E2E) Test (`cypress`)

  * Boots the full stack (Java + Solr + Python).
  * Loads a CSV in the UI.
  * Clicks "Reconcile".
  * Verifies that the "Confidence Bar" renders correctly in the grid.

-----

## 6\. How to Validate a New Model

If you retrain SBERT or update the Solr FST index:

1.  **Stop** the production service.
2.  **Update** the model files in `data/models/`.
3.  **Run** `python evaluation/evaluate_falcon_api.py`.
4.  **Compare** the new F-Score with the previous `results/benchmark_master.csv`.
5.  **Rule:** If Precision drops by \> 2%, the deployment is **rejected**.

