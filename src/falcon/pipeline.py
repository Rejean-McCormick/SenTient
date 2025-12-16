import logging
from sentence_transformers import util
from elasticsearch import NotFoundError

# Assumes the existence of src/falcon/preprocessing.py (next in your list)
from src.falcon.preprocessing import FalconPreprocessor

logger = logging.getLogger("nlp_falcon")

class FalconPipeline:
    """
    The Core Semantic Engine of Layer 2.
    Orchestrates the transition from Raw Text -> Compressed Context -> Vector Scores.
    
    Architecture:
    1. Phase A: Compression (Stopwords & N-Grams via Preprocessor).
    2. Phase B: Edge Detection (Property Inference via Elastic).
    3. Phase C: Scoring (Contextual Vectors via SBERT).
    """

    def __init__(self, config, embedder, es_client):
        """
        :param config: Dict loaded from falcon_settings.yaml
        :param embedder: Loaded SentenceTransformer model (Singleton).
        :param es_client: Connected Elasticsearch client (Singleton).
        """
        self.config = config
        self.embedder = embedder
        self.es_client = es_client
        
        # Initialize the helper for Phase A
        self.preprocessor = FalconPreprocessor(config)
        
        # Cache index names for performance
        self.prop_index = config['elasticsearch']['indexes']['properties']
        self.ent_index = config['elasticsearch']['indexes']['entities']

    def run(self, surface_form, raw_context, candidate_ids):
        """
        Executes the full disambiguation funnel for a single row.
        
        :param surface_form: The entity string (e.g., "Paris").
        :param raw_context: List of surrounding words (e.g., ["Hilton", "hotel"]).
        :param candidate_ids: List of QIDs to rank (e.g., ["Q90", "Q167646"]).
        :return: Dict containing 'inferred_property' and 'ranked_candidates'.
        """
        # 

        # ======================================================================
        # PHASE A: COMPRESSION (Preprocessing)
        # ======================================================================
        # Clean noise to increase vector density
        clean_context_tokens = self.preprocessor.clean_context_window(raw_context)
        context_str = " ".join(clean_context_tokens)

        # ======================================================================
        # PHASE B: EDGE DETECTION (Property Extraction)
        # ======================================================================
        # Check if the context implies a specific relationship (e.g., "buried in" -> P119)
        inferred_pid = self._infer_property_from_ngrams(clean_context_tokens)

        # ======================================================================
        # PHASE C: VECTOR SCORING (SBERT)
        # ======================================================================
        if not candidate_ids:
            return {"inferred_property": inferred_pid, "ranked_candidates": []}

        # 1. Fetch Descriptions (The "B" Vectors)
        # We fetch descriptions from Elastic because Solr only holds labels.
        descriptions_map = self._fetch_descriptions(candidate_ids)

        # 2. Encode "Context" (Vector A)
        # Augment context with surface form for grounding: "Paris [SEP] Hilton hotel"
        input_text = f"{surface_form} {context_str}".strip()
        vector_a = self.embedder.encode(input_text, convert_to_tensor=True)

        # 3. Score Candidates
        results = []
        for qid in candidate_ids:
            desc = descriptions_map.get(qid, "")
            
            # Encode Candidate (Vector B)
            # Optimization: If description is empty, fall back to surface form to avoid zero-vector issues
            text_to_encode = desc if desc else surface_form
            vector_b = self.embedder.encode(text_to_encode, convert_to_tensor=True)

            # Cosine Similarity
            score = util.cos_sim(vector_a, vector_b).item()
            
            # Clamp to [0, 1]
            score = max(0.0, min(1.0, score))

            results.append({
                "id": qid,
                "falcon_score": round(score, 4),
                "semantic_reason": self._generate_reason(score)
            })

        # Sort descending by score
        results.sort(key=lambda x: x['falcon_score'], reverse=True)

        return {
            "inferred_property": inferred_pid,
            "ranked_candidates": results
        }

    def _infer_property_from_ngrams(self, tokens):
        """
        Queries 'sentient_properties_v1' using N-Grams generated from the context.
        """
        if not tokens:
            return None
            
        # Generate N-Grams (e.g., "buried", "buried in") via Preprocessor
        # Optimization: We just join the window for a fuzzy match query in Elastic
        # as defined in the 'falcon_mapping.json' analysis chain.
        window_query = " ".join(tokens)
        
        query_body = {
            "query": {
                "match": {
                    "label": {
                        "query": window_query,
                        "fuzziness": "AUTO" # fuzzy match "born in" vs "bor in"
                    }
                }
            },
            "size": 1
        }

        try:
            res = self.es_client.search(index=self.prop_index, body=query_body)
            if res['hits']['hits']:
                # Return the PID (e.g., P31)
                return res['hits']['hits'][0]['_source'].get('pid')
        except Exception as e:
            logger.warning(f"Property inference failed: {e}")
        
        return None

    def _fetch_descriptions(self, qids):
        """
        Batch fetch descriptions from 'sentient_entities_fallback'.
        """
        try:
            response = self.es_client.mget(index=self.ent_index, body={"ids": qids})
            descriptions = {}
            for doc in response['docs']:
                if doc['found']:
                    # Prioritize 'description', fallback to 'label'
                    src = doc['_source']
                    descriptions[doc['_id']] = src.get('description') or src.get('label') or ""
                else:
                    descriptions[doc['_id']] = ""
            return descriptions
        except Exception as e:
            logger.error(f"ElasticSearch fetch failed: {e}")
            return {qid: "" for qid in qids}

    def _generate_reason(self, score):
        """Generates a human-readable explanation for the UI Confidence Bar."""
        if score > 0.8:
            return f"Strong Context Match ({int(score*100)}%)"
        elif score > 0.4:
            return f"Moderate Context Overlap ({int(score*100)}%)"
        else:
            return "Low Context Similarity"