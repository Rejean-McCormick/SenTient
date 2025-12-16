import os
import yaml
import logging
import numpy as np
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer, util
from elasticsearch import Elasticsearch, NotFoundError
from werkzeug.exceptions import BadRequest

# ==============================================================================
# 1. SETUP & CONFIGURATION
# ==============================================================================

# Configure Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("logs/falcon_service.log"),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("nlp_falcon")

# Resolve Paths
# BASE_DIR is the Project Root (assuming src/main.py)
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) 
SETTINGS_PATH = os.path.join(BASE_DIR, "config", "nlp", "falcon_settings.yaml")

# Load Configuration
try:
    with open(SETTINGS_PATH, 'r') as f:
        config = yaml.safe_load(f)
    logger.info(f"Loaded configuration from {SETTINGS_PATH}")
except Exception as e:
    logger.critical(f"Failed to load settings: {e}")
    exit(1)

# Initialize Flask
app = Flask(__name__)

# ==============================================================================
# 2. GLOBAL RESOURCES (Lazy Loading Pattern)
# ==============================================================================

# A. Sentence-BERT Model
# Loaded into memory once on startup to handle the vector math.
# Configured in falcon_settings.yaml (default: all-MiniLM-L6-v2)
logger.info(f"Loading Embedding Model: {config['embeddings']['model_name']}...")
model_device = config['embeddings']['device']
embedder = SentenceTransformer(config['embeddings']['model_name'], device=model_device)
logger.info("Model loaded successfully.")

# B. ElasticSearch Connection
# Used for Context Property lookups and Candidate Description fetching.
es_hosts = config['elasticsearch']['hosts']
es_client = Elasticsearch(
    hosts=es_hosts,
    request_timeout=config['elasticsearch']['connection']['timeout'],
    max_retries=config['elasticsearch']['connection']['max_retries']
)

# C. Stopwords List
# Critical for the "Compression" phase of the pipeline.
stopwords = set()
stopwords_rel_path = config['preprocessing']['stopwords_file']
STOPWORDS_PATH = os.path.join(BASE_DIR, stopwords_rel_path)

try:
    with open(STOPWORDS_PATH, 'r', encoding='utf-8') as f:
        for line in f:
            word = line.strip().lower()
            if word and not word.startswith('#'):
                stopwords.add(word)
    logger.info(f"Loaded {len(stopwords)} stopwords from {STOPWORDS_PATH}")
except FileNotFoundError:
    logger.warning(f"Stopwords file not found at {STOPWORDS_PATH}. Proceeding without filter.")

# ==============================================================================
# 3. HELPER FUNCTIONS
# ==============================================================================

def preprocess_context(context_tokens):
    """
    Pipeline Phase A: Compression
    Cleans the context window by removing stopwords defined in falcon_extended_en.txt.
    This increases the density of semantic signals (e.g., keeping "Mayor", "Paris" vs "The", "of").
    """
    return [
        word for word in context_tokens 
        if word.lower() not in stopwords
    ]

def fetch_candidate_descriptions(candidate_ids):
    """
    Retrieves candidate descriptions from the 'sentient_entities_fallback' index.
    Used because the Java request provides IDs (Q-items), but we need text for vectorization.
    """
    index_name = config['elasticsearch']['indexes']['entities']
    
    # Use mget for efficiency (Single Round Trip)
    try:
        response = es_client.mget(index=index_name, body={"ids": candidate_ids})
        descriptions = {}
        for doc in response['docs']:
            if doc['found']:
                # Prefer 'description', fallback to 'label' if description missing
                desc = doc['_source'].get('description', '')
                if not desc:
                    desc = doc['_source'].get('label', '')
                descriptions[doc['_id']] = desc
            else:
                descriptions[doc['_id']] = "" # Fallback if missing in ES
        return descriptions
    except Exception as e:
        logger.error(f"ElasticSearch mget failed: {e}")
        return {qid: "" for qid in candidate_ids}

def extract_inferred_property(context_tokens):
    """
    Pipeline Phase B: Edge Detection
    Scans the context N-Grams against 'sentient_properties_v1' to find predicates.
    e.g., "buried in" -> P119. This allows Falcon to boost 'Location' entities over 'People'.
    """
    index_name = config['elasticsearch']['indexes']['properties']
    window = " ".join(context_tokens)
    
    if not window.strip():
        return None

    # Fuzzy match query to handle slight variations
    query = {
        "query": {
            "match": {
                "label": {
                    "query": window,
                    "fuzziness": "AUTO"
                }
            }
        },
        "size": 1
    }
    
    try:
        res = es_client.search(index=index_name, body=query)
        if res['hits']['hits']:
            # Return the PID (e.g., P31)
            return res['hits']['hits'][0]['_source'].get('pid')
    except Exception as e:
        logger.error(f"Property extraction failed: {e}")
    
    return None

# ==============================================================================
# 4. API ENDPOINTS
# ==============================================================================

@app.route('/api/v1/health', methods=['GET'])
def health_check():
    """Liveness probe for the Java ProcessManager."""
    try:
        es_health = es_client.ping()
    except Exception:
        es_health = False
        
    return jsonify({
        "status": "healthy",
        "service": "nlp_falcon",
        "model": config['embeddings']['model_name'],
        "elasticsearch_connected": es_health
    })

@app.route('/api/v1/disambiguate', methods=['POST'])
def disambiguate():
    """
    The Main Pipeline [Docs/02_SEMANTIC_LAYER.md].
    Receives: Surface form, Context Window, Candidate QIDs.
    Returns: Ranked Candidates with Semantic Scores.
    """
    

    try:
        payload = request.get_json()
        if not payload:
            raise BadRequest("Empty payload")

        # 1. Parse Input
        surface_form = payload.get('surface_form', '')
        raw_context = payload.get('context_window', [])
        candidate_ids = payload.get('candidates', [])
        limit_req = payload.get('limit', 3)
        
        # Enforce CPU safety limit [config/nlp/falcon_settings.yaml]
        max_c = config['thresholds']['max_candidates']
        process_limit = min(limit_req, max_c)
        candidates_to_process = candidate_ids[:process_limit]

        if not candidates_to_process:
            return jsonify({"ranked_candidates": [], "inferred_property": None})

        # 2. Pipeline Phase A: Compression
        # Remove stopwords to densify the semantic signal
        clean_context = preprocess_context(raw_context)
        context_str = " ".join(clean_context)
        
        # 3. Pipeline Phase B: Edge Detection (Property Extraction)
        # Try to find if the context implies a specific property (e.g., "born in")
        inferred_pid = extract_inferred_property(clean_context)

        # 4. Pipeline Phase C: Vector Scoring
        # 4a. Fetch Descriptions (The "B" Vectors)
        descriptions_map = fetch_candidate_descriptions(candidates_to_process)
        
        # 4b. Encode "Context" (Vector A)
        # We augment the context with the surface form for better grounding
        # e.g. "Paris [SEP] Hilton hotel expensive"
        input_text = f"{surface_form} {context_str}"
        vector_a = embedder.encode(input_text, convert_to_tensor=True)

        # 4c. Encode "Candidates" (Vector B) and Calculate Cosine Similarity
        ranked_results = []
        
        for qid in candidates_to_process:
            desc = descriptions_map.get(qid, "")
            
            # Encode Candidate Description
            # Note: For this version, we calculate on-the-fly to avoid strict dependency 
            # on pre-calculated vectors in ES, preventing crashes if the index is partial.
            # "desc or surface_form" ensures we have something to encode.
            text_to_encode = desc if desc else surface_form
            vector_b = embedder.encode(text_to_encode, convert_to_tensor=True)
            
            # Cosine Similarity
            score = util.cos_sim(vector_a, vector_b).item()
            
            # Normalize to 0-1
            score = max(0.0, min(1.0, score))

            ranked_results.append({
                "id": qid,
                "falcon_score": round(score, 4),
                # Simple reasoning generation for the UI "Confidence Bar"
                "semantic_reason": f"Context match: {int(score*100)}%" if score > 0.4 else "Low context overlap"
            })

        # 5. Sort and Return
        ranked_results.sort(key=lambda x: x['falcon_score'], reverse=True)
        
        return jsonify({
            "inferred_property": inferred_pid,
            "ranked_candidates": ranked_results
        })

    except Exception as e:
        logger.error(f"Disambiguation error: {str(e)}", exc_info=True)
        return jsonify({"error": str(e)}), 500

# ==============================================================================
# 5. SERVER ENTRY POINT
# ==============================================================================
if __name__ == '__main__':
    # Configuration is loaded from falcon_settings.yaml
    # [ALIGNMENT] strictly binds to 127.0.0.1 per golden variables
    host = config['server']['host']
    port = config['server']['port']
    
    logger.info(f"Starting SenTient Falcon 2.0 on {host}:{port}")
    app.run(host=host, port=port, debug=False)