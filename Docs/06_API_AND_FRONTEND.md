# API & Frontend Integration
**Component:** Interface Layer  
**Technology:** React (Frontend) <-> Jetty (Backend)  
**Protocol:** HTTP/1.1 REST-like Commands

---

## 1. Overview
SenTient decouples the User Interface from the Core Logic.
* **Backend:** Runs on Port `3333` (Jetty). Exposes a "Command" API.
* **Frontend:** Runs on Port `3000` (Vite/React). Consumes JSON.

**The Command Pattern:**
Unlike standard REST resources, SenTient uses functional endpoints:
`http://localhost:3333/command/{module}/{action}`

---

## 2. Global Request Headers
All requests from the Frontend **MUST** include:

```http
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
X-Requested-With: XMLHttpRequest
````

> **CORS Note:** The backend is configured to allow `localhost:3000` via `butterfly.properties`. No proxy is required in dev mode.

-----

## 3\. The "Engine" Parameter (Crucial)

Almost every read/write request requires the `engine` parameter. This defines the **Current State of the View** (active facets and text filters).

**Structure (JSON String):**

```json
{
  "facets": [
    {
      "name": "type",
      "expression": "value.type",
      "columnName": "type",
      "invert": false,
      "mode": "text",
      "selection": [
        {"v": {"v": "Q5", "l": "Human"}, "d": "Human"}
      ]
    }
  ],
  "mode": "row-based"
}
```

  * **Frontend Responsibility:** You must serialize the current Redux/Context state of facets into this JSON string for every API call.

-----

## 4\. Core Synchronous Endpoints

### 4.1. Fetch Rows (The Grid)

**Endpoint:** `POST /command/core/get-rows`
**Use Case:** Populating the Virtual Scroll Grid.

**Parameters:**

  * `project`: Project ID (e.g., `123456789`)
  * `start`: Offset (e.g., `0`)
  * `limit`: Page size (e.g., `50`)
  * `engine`: (See Section 3)

**Response:**

```json
{
  "code": "ok",
  "total": 1500,
  "filtered": 450,
  "rows": [
    {
      "i": 0,
      "cells": [
        {
          "v": "Paris",
          "recon": {
            "id": "Q90",
            "match": { "id": "Q90", "name": "Paris", "score": 100 },
            "judgment": "matched",
            "features": {
                "tapioca_popularity": 0.99,
                "falcon_context": 0.12,
                "levenshtein_distance": 1.0
            }
          }
        }
      ]
    }
  ]
}
```

### 4.2. Compute Facets

**Endpoint:** `POST /command/core/compute-facets`
**Use Case:** Updating the sidebar counts (histogram).

**Response:**

```json
{
  "facets": [
    {
      "name": "type",
      "choices": [
        { "v": {"v": "Q5", "l": "Human"}, "c": 42, "s": true },
        { "v": {"v": "Q6256", "l": "Country"}, "c": 12, "s": false }
      ]
    }
  ]
}
```

-----

## 5\. The Async Process Pattern (Long-Running Jobs)

Reconciliation and Exports take time. The Frontend must implement a **Polling Mechanism**.

### Step 1: Trigger the Job

**Endpoint:** `POST /command/core/reconcile`
**Payload:**

```json
{
  "columnName": "City",
  "mode": "standard-service",
  "service": "http://localhost:3333/api/reconcile" 
}
```

**Response:**

```json
{
  "code": "ok",
  "historyEntry": { "id": "12345", "description": "Reconcile cells..." },
  "process": "on-the-fly" 
}
```

### Step 2: Poll for Status

**Endpoint:** `GET /command/core/get-processes`
**Frequency:** Every 500ms.

**Response (In Progress):**

```json
{
  "processes": [
    {
      "id": "12345",
      "description": "Reconciling batch 40/100...",
      "progress": 40,
      "status": "pending"
    }
  ]
}
```

**Response (Done):**

```json
{ "processes": [] }
```

  * **Logic:** When the array is empty (or the specific ID is gone), trigger a **Data Refresh** (`get-rows`).

-----

## 6\. Visualization Specs (Consensus Score)

SenTient requires a custom UI component to visualize *why* an entity was chosen.

**Data Source:** `cell.recon.match.features`

### UI Logic for "Confidence Bar"

The Frontend should render a stacked bar chart for each matched cell:

1.  **Blue Segment (Popularity):**
      * `width = features.tapioca_popularity * 40%`
      * Tooltip: "Base Popularity (Solr)"
2.  **Green Segment (Context):**
      * `width = features.falcon_context * 30%`
      * Tooltip: "Contextual Match (Falcon)"
      * *Highlight:* If `falcon_context > 0.8`, add a "Star" icon.
3.  **Yellow Segment (Spelling):**
      * `width = features.levenshtein_distance * 30%`
      * Tooltip: "Spelling Similarity"

### UI Logic for "Ambiguity Warning"

If `cell.recon.judgment == 'none'` AND `cell.recon.candidates.length > 0`:

  * Display the **"Did you mean?"** dropdown.
  * Show the top 3 candidates.
  * Next to each candidate, show the **Consensus Score** (0-100).

-----

## 7\. Frontend Tech Stack Requirements

To support the high-density grid of OpenRefine:

  * **Framework:** React 18+ (Strict Mode).
  * **State Management:** TanStack Query (React Query) for API caching.
  * **Grid Component:** `react-window` or `TanStack Virtual` (Mandatory).
      * *Constraint:* DOM nodes cannot exceed \~500. We must virtually render the 10,000+ rows.
  * **Icons:** FontAwesome or Material UI.

-----

## 8\. Error Handling Standards

The backend returns `200 OK` even for logical errors. The Frontend must check the JSON `code` field.

| Code | Meaning | Frontend Action |
| :--- | :--- | :--- |
| `ok` | Success | Render data. |
| `error` | Logic Error | Show Toast Notification with `message`. |
| `pending` | Async | Continue polling. |
| `fatal` | Java Crash | Show "Server Disconnected" Modal. |

**Example Error Response:**

```json
{
  "code": "error",
  "message": "Column 'City' not found in project 12345.",
  "stack": "java.lang.Exception..."
}
```

