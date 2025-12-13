# SenTient Architecture Blueprint
**Version:** 1.0.0-RC1  
**Status:** PROPOSED  
**Classification:** INTERNAL TECHNICAL REFERENCE

---

## 1. Executive Summary
**SenTient** is a next-generation Entity Reconciliation and Relation Extraction engine. It is designed to bridge the gap between messy, unstructured text and structured Knowledge Graphs (Wikidata/Wikibase).

It is not a monolithic application but a **Hybrid Orchestration System** that combines three distinct technological lineages into a single pipeline:
1.  **Speed (Layer 1):** The FST-based rapid tagging of **OpenTapioca** (Solr).
2.  **Semantics (Layer 2):** The context-aware NLP of **Falcon 2.0** (Python/Elastic).
3.  **Structure (Layer 3):** The robust data modeling and QA of **OpenRefine** (Java).

The goal is to achieve **High Precision (>0.85)** and **High Recall (>0.80)** on short-text queries while maintaining a sub-second response time for user interactivity.

---

## 2. High-Level Architecture (The "Funnel" Logic)

SenTient operates on a "Funnel" strategy: broad and fast at the top, narrow and precise at the bottom.

```mermaid
graph TD
    User[User / Frontend] -->|1. Raw Text Batch| Core(Java Core Orchestrator)
    
    subgraph "Layer 0: Ingestion & Fingerprinting"
    Core -->|2. Normalize & Fingerprint| Cache{Local Redis Cache}
    Cache -->|Hit| Core
    end
    
    Cache -->|Miss| Layer1
    
    subgraph "Layer 1: The Sieve (OpenTapioca)"
    Layer1[Solr FST Tagger]
    Layer1 -->|3. Spot Surface Forms| Candidates[Raw Candidates]
    Layer1 -->|4. Inject Popularity Score| Candidates
    end
    
    subgraph "Layer 2: The Linguist (Falcon 2.0)"
    Candidates -->|5. Context Extraction| NLP[Python NLP Service]
    NLP -->|6. Fetch Properties| Elastic[(ElasticSearch)]
    NLP -->|7. Vector Re-ranking| ScoredCandidates
    end
    
    subgraph "Layer 3: The Judge (OpenRefine)"
    ScoredCandidates -->|8. SmartCell Assembly| SmartCell[SmartCell Object]
    SmartCell -->|9. Consensus Scoring| FinalScore
    FinalScore -->|10. Scrutinizer QA| ValidatedCell
    end
    
    ValidatedCell -->|11. JSON Response| Core
    Core -->|12. Async Update| User