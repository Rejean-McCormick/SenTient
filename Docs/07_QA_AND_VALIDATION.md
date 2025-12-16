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
    * Does every `MATCHED` cell have a valid QID (`^Q[0-9]+$`)?
    * Are there any "Ghost Cells" (Status matched, but ID null)?
    * **P-Tag Confusion:** Using a Property (P19) where an Item (Q5) is expected.
* **Action:** Blocks export with `FATAL` error unless Strict Mode is disabled.

### 2.2. Constraint Scrutinizers (Wikidata Alignment)
* **Class:** `org.openrefine.wikibase.qa.ConstraintScrutinizer`
* **Checks:**
    * **Single Value Constraint:** e.g., A country can only have one Capital.
    * **Format Constraint:** e.g., Dates must be ISO 8601 (`YYYY-MM-DD`).
    * **Chronology Constraint:** e.g., Death Date cannot precede Birth Date.
* **Action:** Shows `WARNING` in the UI. User can override.

### 2.3. Consensus Scrutinizers (SenTient Exclusive)
* **Logic:** Checks for statistical anomalies in the scores.
* **Rule:** "The Paris Hilton Rule"
    * `IF tapioca_popularity > 0.95 AND falcon_context < 0.1`
    * **Alert:** "High Popularity but Low Context Similarity. You may have selected a famous entity that does not fit this specific context."



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
python evaluation/evaluate_falcon_api.py --dataset datasets/lcquad2_test.json --output results/benchmark_v1.csv --limit 1000