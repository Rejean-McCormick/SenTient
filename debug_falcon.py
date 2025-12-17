import requests
import json

# Target: Public Falcon API (since local failed)
url = "https://labs.tib.eu/falcon/falcon2/api?mode=long"
text = "Who is the CEO of Google?"

print(f"[*] Querying: {text}")
print(f"[*] Target:   {url}")

try:
    response = requests.post(url, json={"text": text})
    data = response.json()
    
    print("\n[!] RAW RESPONSE FROM API:")
    print(json.dumps(data, indent=2))
    
    # Check specifically for Wikidata
    print("\n[*] Analysis:")
    if 'entities_k' in data:
        print(f"    Wikidata Entities found: {len(data['entities_k'])}")
        for e in data['entities_k']:
            print(f"    - {e[0]} ({e[1]})")
    else:
        print("    NO 'entities_k' key found (No Wikidata returned).")
        
except Exception as e:
    print(f"[!] Error: {e}")