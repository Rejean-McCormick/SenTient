import re
import os
import logging

logger = logging.getLogger("nlp_falcon")

class FalconPreprocessor:
    """
    Phase A: The 'Compression' Engine.
    Responsible for cleaning raw text and removing non-semantic noise to 
    prepare high-density input for the SBERT vectorizer.
    
    Logic defined in: Docs/02_SEMANTIC_LAYER.md
    """

    def __init__(self, config):
        """
        :param config: The loaded dictionary from 'falcon_settings.yaml'.
        """
        self.config = config
        self.stopwords = set()
        
        # 1. Load Stopwords
        # We load the extended stopword list to filter structural noise (e.g., 'http', 'null')
        # alongside standard grammatical noise (e.g., 'the', 'is').
        self._load_stopwords()

        # 2. Compile Regex
        # Pre-compile the cleaning regex defined in settings for performance
        # Default: "[^a-zA-Z0-9\\s]" (Remove punctuation/symbols)
        regex_pattern = config['preprocessing'].get('clean_regex', "[^a-zA-Z0-9\\s]")
        self.clean_regex = re.compile(regex_pattern)

    def _load_stopwords(self):
        """
        Loads the 'falcon_extended_en.txt' file relative to the project root.
        This list acts as the primary noise filter.
        """
        # Resolve path relative to this file location or project root
        # Assuming typical structure: src/falcon/preprocessing.py -> data/stopwords/...
        # However, the config usually provides a path relative to the service root (src/main.py).
        
        relative_path = self.config['preprocessing']['stopwords_file']
        
        # Construct absolute path based on where the service is running (usually project root)
        # We use os.getcwd() because main.py runs from root.
        stopwords_path = os.path.join(os.getcwd(), relative_path)

        try:
            with open(stopwords_path, 'r', encoding='utf-8') as f:
                count = 0
                for line in f:
                    word = line.strip().lower()
                    # Skip comments and empty lines
                    if word and not word.startswith('#'):
                        self.stopwords.add(word)
                        count += 1
            logger.info(f"Preprocessor loaded {count} stopwords from {relative_path}")
        except FileNotFoundError:
            logger.warning(f"Stopwords file not found at {stopwords_path}. Compression disabled.")

    def clean_context_window(self, context_tokens):
        """
        The Core Compression Logic.
        Takes a raw list of context words and returns a pruned, densified list.
        
        Example: 
        Input:  ["The", "Hilton", "hotel", "is", "expensive", "."]
        Output: ["Hilton", "hotel", "expensive"]
        """
        cleaned_tokens = []
        
        for token in context_tokens:
            # 1. Normalize (Lowercase)
            token_lower = token.lower()
            
            # 2. Clean Punctuation
            # "hotel," -> "hotel"
            token_clean = self.clean_regex.sub("", token_lower)
            
            # 3. Filter
            # Must not be empty and must not be a stopword
            if token_clean and token_clean not in self.stopwords:
                # We preserve the original casing? No, SBERT handles lowercase fine 
                # and our stopwords are lower. For consistency, we return lower.
                # However, if we want to preserve Proper Nouns for SBERT, we might want original.
                # But 'token_clean' is lowercased. 
                # Strategy: If the cleaned lower version is valid, return the cleaned lower version.
                cleaned_tokens.append(token_clean)
                
        return cleaned_tokens

    def generate_ngrams(self, tokens, n_min=1, n_max=6):
        """
        Generates sliding window N-Grams for Phase B (Edge Detection).
        Used to find compound predicates like "born in" or "mayor of".
        
        :param tokens: List of cleaned strings.
        :return: List of N-Gram strings.
        """
        ngrams = []
        count = len(tokens)
        
        for n in range(n_min, n_max + 1):
            for i in range(count - n + 1):
                ngram = " ".join(tokens[i : i + n])
                ngrams.append(ngram)
                
        return ngrams