# Layer 3: The Core Orchestrator (Java / OpenRefine Heritage)

**Component:** `core_java`  
**Technology:** Java 17, Jetty 10, Butterfly Framework  
**Role:** State Management, API Gateway, and Final Adjudication

---

## 1. Overview

The **Core Layer** is the stable foundation upon which the experimental AI layers rest. It does not perform NLP; it performs **Management**.

**Philosophy:** "Hybrid Memory Architecture."  
Unlike the original OpenRefine "Database in RAM" model which hits a hard ceiling with large AI vectors, SenTient adopts a split-state strategy:

1.  **Hot Data (RAM):** Row IDs, Status flags, and Raw text values. This preserves the **Instant Faceting** capability (< 10ms filtering).
2.  **Cold Data (DuckDB Sidecar):** Heavy AI payloads (Vectors, Candidate descriptions, Confidence Scores). These are loaded only when the user scrolls them into view.

---

## 2. The Butterfly Architecture

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

---

## 3. The Data Model (The "SmartCell" in Java)

The most significant modification in SenTient is the extension of the `Cell` and `Recon` objects.

### 3.1. The `Cell` Object

Located in `com.google.refine.model.Cell`.
It is the atomic unit of storage. In SenTient, it is polymorphic:

* **Raw State:** Contains only `value` (String/Number).
* **Reconciled State:** Contains a `recon` object.

### 3.2. The `EnhancedRecon` Object

We have extended the standard `Recon` class to support the **Consensus Score** and **Lazy Loading**.

**Fields added to Java Class:**

```java
public class Recon {
    // Standard Fields
    public long id;
    public String judgment; // MATCHED, NEW, NONE
    
    // SenTient Extensions (Lazy Loaded from Sidecar)
    // NOTE: These are now transient or null until hydrated by DuckDBStore.fetchVisibleRows()
    public transient List<ReconCandidate> candidates; 
    public transient float consensusScore; 
    public transient Map<String, Double> featureVector; 
}