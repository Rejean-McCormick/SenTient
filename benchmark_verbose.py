import requests
import pandas as pd
import json
import time
from datetime import datetime

# ==============================================================================
# 1. CONFIGURATION
# ==============================================================================
TAPIOCA_URL = "http://127.0.0.1:8080/api/annotate"
FALCON_URL = "http://127.0.0.1:5005/api/v1/disambiguate"
LOG_FILE = f"sentient_benchmark_{datetime.now().strftime('%Y%m%d_%H%M')}.csv"

# ==============================================================================
# 2. DATASET
# ==============================================================================
DATASET = [
    {
        "text": "Apple is a tech giant.", 
        "surface": "Apple",
        "context": ["is", "a", "tech", "giant"],
        "candidates": ["Q312", "Q89"],
        "expect": "Q312"
    },
    {
        "text": "I ate a red Apple.", 
        "surface": "Apple",
        "context": ["I", "ate", "a", "red"],
        "candidates": ["Q312", "Q89"],
        "expect": "Q89"
    },
    {
        "text": "Paris Hilton is famous.", 
        "surface": "Paris",
        "context": ["Hilton", "is", "famous"],
        "candidates": ["Q90", "Q47746"],
        "expect": "Q47746"
    },
    {
        "text": "I love visiting Paris.", 
        "surface": "Paris",
        "context": ["I", "love", "visiting"],
        "candidates": ["Q90", "Q47746"],
        "expect": "Q90"
    },
    {
        "text": "Amazon is a massive river.", 
        "surface": "Amazon",
        "context": ["is", "a", "massive", "river"],
        "candidates": ["Q3783", "Q3884"],
        "expect": "Q3783"
    },
    {
        "text": "Amazon delivers packages.", 
        "surface": "Amazon",
        "context": ["delivers", "packages"],
        "candidates": ["Q3783", "Q3884"],
        "expect": "Q3884"
    }
]

# ==============================================================================
# 3. ENGINE ADAPTERS
# ==============================================================================

def query_tapioca(text):
    try:
        response = requests.post(TAPIOCA_URL, data={"query": text}, timeout=10)
        if response.status_code == 200:
            data = response.json()
            ids = []
            if 'annotations' in data:
                for ann in data['annotations']:
                    for tag in ann.get('tags', []):
                        ids.append(tag.get('id'))
            return ids
        return f"Error {response.status_code}"
    except Exception as e:
        return "Connection Failed"

def query_falcon(surface, context, candidates):
    payload = {
        "surface_form": surface,
        "context_window": context,
        "candidates": candidates,
        "limit": 5
    }
    try:
        # TIMEOUT INCREASED TO 300 SECONDS (5 Minutes)
        # This allows the CPU to calculate vectors without the script giving up.
        response = requests.post(FALCON_URL, json=payload, timeout=300)
        
        if response.status_code == 200:
            data = response.json()
            ranked = data.get('ranked_candidates', [])
            if ranked:
                top_pick = ranked[0]
                return f"{top_pick['id']} ({top_pick['falcon_score']})"
            return "No Match"
        return f"Error {response.status_code}: {response.text[:50]}"
    except Exception as e:
        return f"Connection Failed: {str(e)[:50]}"

# ==============================================================================
# 4. RUNNER
# ==============================================================================
print(f"[*] Starting BENCHMARK... Output: {LOG_FILE}\n")
results = []

for case in DATASET:
    print(f"Processing: '{case['text']}'")
    
    # 1. Test OpenTapioca
    t_res = query_tapioca(case['text'])
    
    # 2. Test Falcon
    f_res = query_falcon(case['surface'], case['context'], case['candidates'])
    
    # 3. Log
    row = {
        "Query": case['text'],
        "Target_Entity": case['surface'],
        "Expected_ID": case['expect'],
        "OpenTapioca_Result": str(t_res),
        "Falcon_Result": str(f_res)
    }
    results.append(row)
    print(f"   -> Tapioca: {t_res}")
    print(f"   -> Falcon:  {f_res}")
    print("-" * 60)

df = pd.DataFrame(results)
df.to_csv(LOG_FILE, index=False)
print(f"\n[SUCCESS] Benchmark saved to {LOG_FILE}")