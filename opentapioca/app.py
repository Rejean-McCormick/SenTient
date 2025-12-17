import os
import sys
import json
import logging
from bottle import route, run, default_app, static_file, request, abort, response
import bottle

# Import pynif
from pynif import NIFCollection

# Import OpenTapioca modules
from opentapioca.wikidatagraph import WikidataGraph
from opentapioca.languagemodel import BOWLanguageModel
from opentapioca.tagger import Tagger
from opentapioca.classifier import SimpleTagClassifier

# Import Settings
import settings

# Setup Logging
logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(name)-12s %(levelname)-8s %(message)s')

tapioca_dir = os.path.dirname(__file__)

# ------------------------------------------------------------------------------
# 1. INITIALIZE MODELS (With Graceful Failures)
# ------------------------------------------------------------------------------
print(f"[INFO] Initializing OpenTapioca...")

# Load Bag of Words Model
bow = BOWLanguageModel()
if settings.LANGUAGE_MODEL_PATH and os.path.exists(settings.LANGUAGE_MODEL_PATH):
    print(f"[INFO] Loading Language Model from {settings.LANGUAGE_MODEL_PATH}")
    bow.load(settings.LANGUAGE_MODEL_PATH)
else:
    print("[WARN] Language Model not found or not set. Running without it.")

# Load Graph / PageRank
graph = WikidataGraph()
if settings.PAGERANK_PATH and os.path.exists(settings.PAGERANK_PATH):
    print(f"[INFO] Loading PageRank from {settings.PAGERANK_PATH}")
    graph.load_pagerank(settings.PAGERANK_PATH)
else:
    print("[WARN] PageRank file not found. Running without it.")

tagger = None
classifier = None

# ------------------------------------------------------------------------------
# 2. SETUP TAGGER & SOLR CONNECTION (The Critical Patch)
# ------------------------------------------------------------------------------
if settings.SOLR_COLLECTION:
    print(f"[INFO] Connecting to Solr Collection: {settings.SOLR_COLLECTION}")
    
    # Initialize Tagger normally
    tagger = Tagger(settings.SOLR_COLLECTION, bow, graph)
    
    # [SENTIENT PATCH] Manually override the endpoint to use the Docker service address
    # We expect settings.SOLR_ENDPOINT to be 'http://sentient_solr:8983/solr/'
    solr_base = getattr(settings, 'SOLR_ENDPOINT', 'http://localhost:8983/solr/')
    if not solr_base.endswith('/'): 
        solr_base += '/'
    
    # Construct the full URL: http://sentient_solr:8983/solr/sentient-tapioca/tag
    tagger.solr_endpoint = f"{solr_base}{settings.SOLR_COLLECTION}/tag"
    print(f"[INFO] Solr Endpoint set to: {tagger.solr_endpoint}")

    # Initialize Classifier
    classifier = SimpleTagClassifier(tagger)
    if settings.CLASSIFIER_PATH and os.path.exists(settings.CLASSIFIER_PATH):
        print(f"[INFO] Loading Classifier from {settings.CLASSIFIER_PATH}")
        classifier.load(settings.CLASSIFIER_PATH)
    else:
        print("[WARN] Classifier model not found. Using raw Tagger results.")

# ------------------------------------------------------------------------------
# 3. WEB SERVER UTILITIES
# ------------------------------------------------------------------------------
def jsonp(view):
    """
    Decorator for views that return JSON
    """
    def wrapped(*posargs, **kwargs):
        args = {}
        for k in request.forms:
            args[k] = getattr(request.forms, k)
        for k in request.query:
            args[k] = getattr(request.query, k)
        
        callback = args.get('callback')
        status_code = 200
        
        try:
            result = view(args, *posargs, **kwargs)
        except Exception as e:
            # Enhanced Error Logging for Docker
            import traceback
            traceback.print_exc(file=sys.stdout)
            result = {
                'status': 'error',
                'message': 'Internal Server Error',
                'details': str(e)
            }
            # We return 200 with an error body so JSONP clients don't choke,
            # but you can change this to 500 if strict HTTP codes are needed.
            status_code = 500 

        if callback:
            result = '%s(%s);' % (callback, json.dumps(result))

        if status_code == 200 or status_code == 500:
            return result
        else:
            abort(status_code, result)

    return wrapped

# ------------------------------------------------------------------------------
# 4. API ROUTES
# ------------------------------------------------------------------------------

@route('/api/annotate', method=['GET','POST'])
@jsonp
def annotate_api(args):
    text = args.get('query')
    if not text:
        return {'error': 'No query provided'}

    # Use classifier if available, otherwise raw tagger
    if not classifier:
        mentions = tagger.tag_and_rank(text)
    else:
        mentions = classifier.create_mentions(text)
        classifier.classify_mentions(mentions)

    return {
        'text': text,
        'annotations': [m.json() for m in mentions]
    }

@route('/api/nif', method=['GET','POST'])
def nif_api(*args, **kwargs):
    content_format = request.headers.get('Content') or 'application/x-turtle'
    
    # backwards compatibility
    only_matching = request.GET.get('only_matching', 'true') == 'true'

    nif_body = request.body.read()
    if not nif_body:
        return "No content"

    nif_doc = NIFCollection.loads(nif_body)
    
    for context in nif_doc.contexts:
        logger.debug(context.mention)
        mentions = classifier.create_mentions(context.mention)
        classifier.classify_mentions(mentions)
        for mention in mentions:
            mention.add_phrase_to_nif_context(context, only_matching=only_matching)

    response.set_header('content-type', content_format)
    return nif_doc.dumps()

# ------------------------------------------------------------------------------
# 5. STATIC FILES (UI)
# ------------------------------------------------------------------------------
@route('/')
def home():
    return static_file('index.html', root=os.path.join(tapioca_dir, 'html/'))

@route('/css/<fname>')
def css(fname):
    return static_file(fname, root=os.path.join(tapioca_dir, 'html/css/'))

@route('/js/<fname>')
def js(fname):
    return static_file(fname, root=os.path.join(tapioca_dir, 'html/js/'))

# ------------------------------------------------------------------------------
# 6. MAIN ENTRY POINT
# ------------------------------------------------------------------------------
if __name__ == '__main__':
    # Changed to Port 80 for Docker compatibility
    run(host='0.0.0.0', port=80, debug=True)

app = application = default_app()