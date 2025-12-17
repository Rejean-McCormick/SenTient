import subprocess
import requests
import time
import json
import sys

# ==============================================================================
# CONFIGURATION
# ==============================================================================
CONTAINERS = ["sentient_falcon", "sentient_opentapioca"]
ENDPOINTS = {
    "sentient_falcon": "http://127.0.0.1:5005/api?mode=long",
    "sentient_opentapioca": "http://127.0.0.1:8080/api/annotate"
}
PAYLOADS = {
    "sentient_falcon": {"text": "Apple is a tech giant."},
    "sentient_opentapioca": {"query": "Apple is a tech giant."} # Tapioca uses 'query', not 'text'
}

LOG_FILE = "system_diagnosis_report.txt"

def run_command(cmd):
    """Runs a shell command and returns output."""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        return result.stdout.strip() + result.stderr.strip()
    except Exception as e:
        return str(e)

def get_container_status(name):
    """Checks if a container is Up or Exited."""
    cmd = f"docker inspect -f '{{{{.State.Status}}}}' {name}"
    return run_command(cmd)

def get_container_logs(name, lines=50):
    """Fetches the last N lines of logs."""
    cmd = f"docker logs --tail {lines} {name}"
    return run_command(cmd)

def test_endpoint(name, url, payload):
    """Tries to send a small interaction to the API."""
    try:
        # Determine if JSON or Form Data (Tapioca is Form Data)
        if "opentapioca" in name:
            response = requests.post(url, data=payload, timeout=2)
        else:
            response = requests.post(url, json=payload, timeout=2)
            
        return f"HTTP {response.status_code}\nResponse: {response.text[:200]}..."
    except Exception as e:
        return f"CONNECTION FAILED: {str(e)}"

# ==============================================================================
# MAIN DIAGNOSTIC LOOP
# ==============================================================================
with open(LOG_FILE, "w", encoding="utf-8") as f:
    def log(msg):
        print(msg)
        f.write(msg + "\n")

    log("="*60)
    log("SENTIENT SYSTEM DIAGNOSTICS")
    log("="*60 + "\n")

    for container in CONTAINERS:
        log(f"[*] DIAGNOSING: {container}")
        
        # 1. Check Docker Status
        status = get_container_status(container)
        log(f"    STATUS: {status}")
        
        # 2. Extract Logs (The "Autopsy")
        log(f"    --- INTERNAL LOGS (Last 50 Lines) ---")
        logs = get_container_logs(container)
        if not logs:
            log("    [NO LOGS FOUND]")
        else:
            # Indent logs for readability
            for line in logs.split('\n'):
                log(f"    | {line}")
        log("    -------------------------------------")

        # 3. Attempt Interaction (Only if running)
        if "running" in status.lower():
            log(f"    --- INTERACTION TEST ---")
            url = ENDPOINTS.get(container)
            payload = PAYLOADS.get(container)
            result = test_endpoint(container, url, payload)
            log(f"    RESULT: {result}")
        else:
            log("    [SKIP] Interaction skipped because container is dead.")
        
        log("\n" + "="*60 + "\n")

log(f"[DONE] Report saved to {LOG_FILE}")