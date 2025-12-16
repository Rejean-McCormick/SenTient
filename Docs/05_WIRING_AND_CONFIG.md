# Wiring & Configuration Strategy
**Component:** System Orchestration  
**Config Source:** `config/orchestration/environment.json`  
**Role:** Network Topology, IPC Protocols, and Startup Sequence.

---

## 1. Network Topology (The Port Map)

SenTient is a distributed system running on `localhost`. Strict port discipline is enforced to prevent conflicts.

| Service | Role | Port | Protocol | Bound Interface |
| :--- | :--- | :--- | :--- | :--- |
| **Java Core** | Orchestrator / UI | `3333` | HTTP/1.1 | `127.0.0.1` |
| **Falcon (Python)** | NLP Service | `5005` | HTTP/1.1 | `127.0.0.1` |
| **Solr (Tapioca)** | Fast Tagger | `8983` | HTTP/2 | `127.0.0.1` |
| **ElasticSearch** | Context Store | `9200` | HTTP/TCP | `127.0.0.1` |
| **Redis** | Result Cache | `6379` | TCP | `127.0.0.1` |

> **Security Note:** All services are bound strictly to `127.0.0.1`. No external traffic is allowed directly to Solr, Elastic, or Python. All external requests MUST go through the Java Core (Port 3333).

---

## 2. Inter-Process Communication (IPC)

The Java Core acts as the "Master" node. It communicates with "Worker" nodes via specific protocols defined in `butterfly.properties`.



### 2.1. Link A: Java -> Solr (Layer 1)
* **Purpose:** High-speed entity spotting.
* **Client:** `org.apache.solr.client.solrj.impl.Http2SolrClient`
* **Concurrency:** Highly parallel. Java sends async batches.
* **Timeout:** **Strict 500ms**.
    * *Logic:* If Solr takes >500ms, the FST logic is stuck. Fail fast and mark cell as `UNRECONCILED` rather than blocking the UI.

### 2.2. Link B: Java -> Falcon (Layer 2)
* **Purpose:** Deep semantic analysis.
* **Client:** `java.net.http.HttpClient` (Java 11+).
* **Concurrency:** Throttled (Max 4 concurrent requests).
    * *Logic:* Python is CPU-bound (SBERT vectors). Flooding it causes thrashing.
* **Timeout:** **Loose 120s**.
    * *Logic:* Vector calculations take time. We wait.

### 2.3. Link C: Python -> ElasticSearch
* **Purpose:** Fetching property context vectors.
* **Client:** `elasticsearch-py`.
* **Protocol:** Persistent TCP connection (Keep-Alive).

---

## 3. File System Layout (Physical Wiring)

The application expects a specific directory structure relative to `SENTIENT_HOME`.

```text
SENTIENT_HOME/
├── refine                 # The specific Java startup shell script
├── refine.ini             # JVM Memory arguments (-Xmx)
├── config/                # THE NERVE CENTER
│   ├── core/butterfly.properties        # Java Settings
│   ├── nlp/falcon_settings.yaml         # Python Settings
│   ├── solr/tapioca_schema.xml          # Solr Schema
│   ├── elastic/falcon_mapping.json      # Elastic Schema
│   └── orchestration/environment.json   # Global Paths
├── modules/               # Core logic (Refine)
├── extensions/            # Plugins (Wikibase, JDBC)
├── solr/                  # Embedded Solr Server
├── python_venv/           # Python Virtual Environment
└── data/
    ├── workspace/         # User Project Data (JSON/History)
    └── models/            # SBERT models & Stopwords