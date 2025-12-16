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
`http://127.0.0.1:3333/command/{module}/{action}`

---

## 2. Global Request Headers
All requests from the Frontend **MUST** include:

```http
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
X-Requested-With: XMLHttpRequest