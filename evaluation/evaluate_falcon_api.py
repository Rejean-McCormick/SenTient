# evaluation\evaluate_falcon_api.py
import argparse
import csv
import json
import time
import requests
import logging
import statistics
from datetime import datetime
from tqdm import tqdm

# ==============================================================================
# SenTient Benchmark Script (Falcon API Evaluator)
# ==============================================================================
# Role: Validates the Precision/Recall of the Semantic Layer against Golden Data.
# Docs: Docs/07_QA_AND_VALIDATION.md
# ==============================================================================

# Configure Logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("evaluate_falcon")

# Constants
# [ALIGNMENT] Use 127.0.0.1 to match system-wide Golden Variables
API_ENDPOINT = "http://127.0.0.1:5005/api/v1/disambiguate"
DEFAULT_DATASET = "datasets/lcquad2_test.json"
DEFAULT_OUTPUT = "results/benchmark_results.csv"

def load_dataset(filepath):
    """
    Parses the Golden Standard dataset.
    Supports LC-QuAD 2.0 JSON format or SimpleQuestions TSV.
    """
    data = []
    logger.info(f"Loading dataset from {filepath}...")
    
    try:
        if filepath.endswith('.json'):
            with open(filepath, 'r', encoding='utf-8') as f:
                raw_data = json.load(f)
                # Normalize LC-QuAD 2.0 structure
                for item in raw_data:
                    # Extract necessary fields. Adjust keys based on specific JSON schema.
                    # Required schema: { "surface_form": "Paris", "context": [...], "candidates": ["Q90", ...], "expected_id": "Q90" }
                    if 'expected_id' in item and 'candidates' in item:
                        data.append(item)
        else:
            # Assume TSV: subject_qid \t property_pid \t object_qid \t question
            with open(filepath, 'r', encoding='utf-8') as f:
                reader = csv.reader(f, delimiter='\t')
                for row in reader:
                    if len(row) >= 4:
                        # Synthetic test generation from SimpleQuestions
                        # Note: This requires a separate hydration step to fetch Labels/Candidates via Solr
                        pass 
    except Exception as e:
        logger.error(f"Failed to load dataset: {e}")
        exit(1)
        
    logger.info(f"Loaded {len(data)} valid test cases.")
    return data

def evaluate_api(dataset, limit=None):
    """
    Runs the benchmark loop.
    """
    

    if limit:
        dataset = dataset[:limit]

    results = []
    latencies = []
    
    correct_matches = 0
    total_processed = 0

    logger.info(f"Starting evaluation on {len(dataset)} items...")

    for case in tqdm(dataset):
        surface_form = case.get('surface_form')
        context = case.get('context', [])
        expected_id = case.get('expected_id')
        # Falcon REQUIREs candidates to rank. In benchmark mode, these must be pre-populated
        # or fetched from Solr in a pre-processing step.
        candidates = case.get('candidates', []) 

        if not surface_form or not expected_id or not candidates:
            continue

        payload = {
            "surface_form": surface_form,
            "context_window": context,
            "candidates": candidates,
            "limit": 5
        }

        try:
            start_time = time.time()
            response = requests.post(API_ENDPOINT, json=payload, timeout=5)
            latency = (time.time() - start_time) * 1000 # ms
            latencies.append(latency)

            if response.status_code == 200:
                resp_json = response.json()
                ranked = resp_json.get('ranked_candidates', [])
                
                # Check Top-1 Accuracy
                predicted_id = ranked[0]['id'] if ranked else None
                
                is_correct = (predicted_id == expected_id)
                if is_correct:
                    correct_matches += 1
                
                results.append({
                    "surface_form": surface_form,
                    "expected": expected_id,
                    "predicted": predicted_id,
                    "correct": is_correct,
                    "score": ranked[0]['falcon_score'] if ranked else 0.0,
                    "latency_ms": latency
                })
                
                total_processed += 1
            else:
                logger.warning(f"API Error {response.status_code} for {surface_form}")

        except requests.exceptions.RequestException as e:
            logger.error(f"Request failed: {e}")

    return results, latencies, correct_matches, total_processed

def save_report(results, latencies, correct, total, output_path):
    """
    Calculates metrics and saves CSV report.
    """
    if total == 0:
        logger.warning("No records processed.")
        return

    accuracy = correct / total
    avg_latency = statistics.mean(latencies) if latencies else 0
    p95_latency = statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else avg_latency

    print("\n" + "="*40)
    print(f" BENCHMARK RESULTS (N={total})")
    print("="*40)
    print(f" Accuracy (Precision@1): {accuracy:.2%}")
    print(f" Avg Latency:            {avg_latency:.2f} ms")
    print(f" P95 Latency:            {p95_latency:.2f} ms")
    print("="*40 + "\n")

    # Write CSV
    if results:
        keys = results[0].keys()
        with open(output_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=keys)
            writer.writeheader()
            writer.writerows(results)
    
    logger.info(f"Detailed report saved to {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SenTient Falcon API Benchmark")
    parser.add_argument("--dataset", type=str, default=DEFAULT_DATASET, help="Path to test JSON")
    parser.add_argument("--output", type=str, default=DEFAULT_OUTPUT, help="Path to output CSV")
    parser.add_argument("--limit", type=int, help="Limit number of test cases")
    
    args = parser.parse_args()
    
    data = load_dataset(args.dataset)
    results, latencies, correct, total = evaluate_api(data, args.limit)
    save_report(results, latencies, correct, total, args.output)