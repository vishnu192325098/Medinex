"""
verify_deployment.py - Live Web Deployment Verification
Verifies that the published GitHub Pages webpage is deployed, reachable with HTTP 200,
and serves all expected DOM structures and application root containers.
"""

import sys
import time
import requests

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

LIVE_WEB_URL = "https://vishnu192325098.github.io/Medinex/"
EXPECTED_ELEMENTS = ["<title>", "app-root", "deviceFrame", "screenContainer", "bottomNavBar"]

def verify_live_deployment():
    print("=" * 60)
    print("🌐 MEDINEX LIVE WEB DEPLOYMENT VERIFICATION")
    print(f"Target URL: {LIVE_WEB_URL}")
    print("=" * 60)

    max_retries = 6
    delay_sec = 6

    for attempt in range(1, max_retries + 1):
        print(f"\n[Attempt {attempt}/{max_retries}] Pinging {LIVE_WEB_URL}...")
        t0 = time.time()
        try:
            resp = requests.get(LIVE_WEB_URL, headers={"User-Agent": "Medinex-Deployment-Verifier/1.0"}, timeout=20)
            elapsed = time.time() - t0
            print(f"  HTTP Response: {resp.status_code} in {elapsed:.2f}s (Content Length: {len(resp.text)} bytes)")

            if resp.status_code == 200:
                missing = [el for el in EXPECTED_ELEMENTS if el not in resp.text]
                if not missing:
                    print(f"✓ Deployment Verified: All critical DOM containers confirmed ({len(EXPECTED_ELEMENTS)}/{len(EXPECTED_ELEMENTS)}).")
                    return True
                else:
                    print(f"  Notice: Page responded with 200 but waiting for elements: {missing}")
            else:
                print(f"  Warning: Unexpected HTTP status {resp.status_code}")
        except Exception as e:
            print(f"  Network notice on attempt {attempt}: {e}")

        if attempt < max_retries:
            print(f"  Waiting {delay_sec}s before next verification probe...")
            time.sleep(delay_sec)

    print(f"❌ ERROR: Live web deployment verification failed after {max_retries} attempts.")
    return False

if __name__ == "__main__":
    success = verify_live_deployment()
    if not success:
        sys.exit(1)
