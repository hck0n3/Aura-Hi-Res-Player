import re
import json
import urllib.request
import os
import sys

def get_player_url():
    print("Fetching YouTube Music homepage...")
    req = urllib.request.Request("https://music.youtube.com/", headers={'User-Agent': 'Mozilla/5.0'})
    html = urllib.request.urlopen(req).read().decode('utf-8')
    match = re.search(r'jsUrl["\']\s*:\s*["\']([^"\']+/player/[a-f0-9]{8}/[^"\']+)["\']', html)
    if not match:
        raise Exception("Could not find player URL in music.youtube.com")
    url = match.group(1)
    if url.startswith('/'):
        url = 'https://music.youtube.com' + url
    return url

def update_config():
    player_url = get_player_url()
    hash_match = re.search(r'/player/([a-f0-9]{8})/', player_url)
    if not hash_match:
        raise Exception("Could not extract hash from " + player_url)
    
    player_hash = hash_match.group(1)
    print(f"Current player hash: {player_hash}")
    
    config_path = os.path.join(os.path.dirname(__file__), '..', 'player_configs.json')
    with open(config_path, 'r', encoding='utf-8') as f:
        configs = json.load(f)
        
    if player_hash in configs:
        print("Hash already exists in player_configs.json. No update needed.")
        return

    print(f"Downloading {player_url}...")
    req = urllib.request.Request(player_url, headers={'User-Agent': 'Mozilla/5.0'})
    player_js = urllib.request.urlopen(req).read().decode('utf-8')
    
    print("Extracting parameters...")
    
    # Extract signatureTimestamp (sts)
    sts_match = re.search(r'signatureTimestamp["\']?\s*:\s*(\d+)', player_js)
    sts = int(sts_match.group(1)) if sts_match else 0
    print(f"signatureTimestamp: {sts}")
    
    # Extract n function wrapper (VM-dispatch URL param trick)
    # Looking for: .get("n"))&&(b=new g.cY
    n_match = re.search(r'\.get\("n"\)\)\s*&&\s*\([a-zA-Z0-9$]+\s*=\s*new\s+g\.([a-zA-Z0-9$]+)', player_js)
    if not n_match:
        raise Exception("Could not find n-function URL wrapper class")
    n_class = n_match.group(1)
    n_expr = f"(function(n){{try{{var u=new g.{n_class}('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}}catch(e){{return n;}}}})(INPUT)"
    print(f"n_expr: {n_expr}")
    
    # Extract sig function
    # Looking for pattern: &&(VAR=FUNC(NUM,NUM,INPUT) or &&(VAR=FUNC(NUM,decodeURIComponent
    sig_expr = None
    
    # 2026 VM-dispatch pattern (like pB(20,268,JQ(74,8344,sig)))
    # This is tricky with simple regex because it's dynamic. Let's try to extract the main dispatcher.
    # We will search for decodeURIComponent(h.s) or similar usages.
    sig_match = re.search(r'&&\s*\([a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*[a-zA-Z0-9$]+\s*\)', player_js)
    if sig_match:
        func_name = sig_match.group(1)
        arg1 = sig_match.group(2)
        arg2 = sig_match.group(3)
        sig_expr = f"{func_name}({arg1},{arg2},INPUT)"
    else:
        sig_match2 = re.search(r'&&\s*\([a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent', player_js)
        if sig_match2:
            func_name = sig_match2.group(1)
            arg1 = sig_match2.group(2)
            sig_expr = f"{func_name}({arg1},INPUT)"
        else:
            print("Could not find sig expression, setting up empty structure for manual review")
            sig_expr = "UNKNOWN(INPUT)"
            
    print(f"sig_expr: {sig_expr}")
    
    new_config = {
        "sigFuncName": "_expr_sig",
        "sigConstantArg": None,
        "sigConstantArgs": None,
        "sigPreprocessFunc": None,
        "sigPreprocessArgs": None,
        "sigJsExpression": sig_expr,
        "nFuncName": "_expr_n",
        "nArrayIndex": None,
        "nConstantArgs": None,
        "nJsExpression": n_expr,
        "signatureTimestamp": sts
    }
    
    configs[player_hash] = new_config
    
    with open(config_path, 'w', encoding='utf-8') as f:
        json.dump(configs, f, indent=4)
        
    print(f"Successfully updated player_configs.json with hash {player_hash}")

if __name__ == '__main__':
    try:
        update_config()
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)
