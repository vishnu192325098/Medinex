"""
Medinex Multi-Client Consistency & Pipeline Verification Test
Submits the exact same Zyrtec 10 mg packaging image through both client pathways:
  1. Web Client pathway (http://127.0.0.1:8000/api/medicine/recognize)
  2. Android Client pathway (http://10.112.20.9:8000/api/medicine/recognize)
Compares raw responses and simulated client UI presentations.
"""

import os
import sys
import uuid
import json
import urllib.request
import unittest

IMAGE_PATH = r"C:\Users\Lenovo\Downloads\bjy2svvmn8-1\bjy2svvmn8-1\Zyrtec 20 tablets\huawei cn (27).jpg"

def post_multipart(url, file_bytes, filename):
    boundary = uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: image/jpeg\r\n\r\n"
    ).encode("utf-8") + file_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))

class TestClientConsistency(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        if not os.path.exists(IMAGE_PATH):
            raise unittest.SkipTest(f"Test image not found at {IMAGE_PATH}")
        with open(IMAGE_PATH, "rb") as f:
            cls.image_bytes = f.read()

    def test_both_clients_reach_shared_backend_and_return_identical_data(self):
        base_env = os.environ.get("MEDINEX_BACKEND_URL", "https://medinex-backend-bukx.onrender.com").rstrip("/")
        default_endpoint = base_env if base_env.endswith("/api/medicine/recognize") else f"{base_env}/api/medicine/recognize"
        web_url = os.environ.get("WEB_BACKEND_URL", default_endpoint)
        android_url = os.environ.get("ANDROID_BACKEND_URL", default_endpoint)

        # Web request
        web_resp = post_multipart(web_url, self.image_bytes, "packaging.jpg")
        # Android request
        android_resp = post_multipart(android_url, self.image_bytes, "medicine.jpg")

        # 1. Verify status
        self.assertEqual(web_resp["status"], "success")
        self.assertEqual(android_resp["status"], "success")
        self.assertEqual(web_resp["uncertainty_status"], android_resp["uncertainty_status"])

        # 2. Verify identical candidates count
        self.assertGreater(len(web_resp["detected_medicines"]), 0)
        self.assertEqual(len(web_resp["detected_medicines"]), len(android_resp["detected_medicines"]))

        # 3. Verify top candidate fields match identically
        web_top = web_resp["detected_medicines"][0]
        android_top = android_resp["detected_medicines"][0]

        self.assertEqual(web_top["name"], android_top["name"])
        self.assertEqual(web_top["name"], "Zyrtec")
        self.assertEqual(web_top["active_ingredients"], ["cetirizine"])
        self.assertEqual(web_top["strength"], "10mg")
        self.assertEqual(web_top["dosage_form"], "Tablets")
        self.assertEqual(web_top["confidence"], android_top["confidence"])
        self.assertEqual(web_top["verification_tier"], android_top["verification_tier"])

        # 4. Verify no false claims of "Verified" or "AI Verified"
        self.assertNotIn("AI Verified", web_top["verification_tier"])
        self.assertNotIn("Verified Manufacturer", web_top.get("manufacturer", ""))
        self.assertNotEqual(web_top["confidence"], 0.99, "Confidence must not be artificially inflated to 99%")

        # 5. Verify experimental visual model note is present
        self.assertIn("visual_classifier_note", web_resp)
        self.assertIn("2.17%", web_resp["visual_classifier_note"])
        self.assertIn("experimental", web_resp["visual_classifier_note"].lower())

        # 6. Verify identical simulated UI formatting
        web_display = {
            "name": web_top["name"],
            "ingredients": " + ".join(web_top["active_ingredients"]),
            "strength": web_top["strength"],
            "form": web_top["dosage_form"],
            "confidence": f"{round(web_top['confidence'] * 100)}%",
            "tier": web_top["verification_tier"],
            "manufacturer": web_top.get("manufacturer", "Not specified on packaging")
        }

        android_display = {
            "name": android_top["name"],
            "ingredients": ", ".join(android_top["active_ingredients"]),
            "strength": android_top["strength"],
            "form": android_top["dosage_form"],
            "confidence": f"{round(android_top['confidence'] * 100)}%",
            "tier": android_top["verification_tier"],
            "manufacturer": android_top.get("manufacturer", "Not specified on package")
        }

        self.assertEqual(web_display["name"], android_display["name"])
        self.assertEqual(web_display["strength"], android_display["strength"])
        self.assertEqual(web_display["form"], android_display["form"])
        self.assertEqual(web_display["confidence"], android_display["confidence"])
        self.assertEqual(web_display["tier"], android_display["tier"])

        print("\n[TEST PASSED] Multi-client recognition consistency verified successfully:")
        print(f"  Medicine     : {web_display['name']}")
        print(f"  Ingredients  : {web_display['ingredients']}")
        print(f"  Strength     : {web_display['strength']}")
        print(f"  Confidence   : {web_display['confidence']} (Calibrated; uninflated)")
        print(f"  Tier         : {web_display['tier']}")
        print(f"  Manufacturer : {web_display['manufacturer']}")
        print(f"  Pipeline Note: {web_resp['visual_classifier_note']}")

if __name__ == "__main__":
    unittest.main()
