"""
FastAPI Backend Unit & Integration Tests for Medinex Medicine Recognition
"""

import os
import unittest
import json
import urllib.request
import urllib.error
import uuid

BACKEND_BASE_URL = "http://127.0.0.1:8000"
TEST_IMAGE_PATH = r"C:\Users\Lenovo\Downloads\bjy2svvmn8-1\bjy2svvmn8-1\Zyrtec 20 tablets\huawei cn (27).jpg"

def post_multipart(url, file_bytes, filename, content_type="image/jpeg"):
    boundary = uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode("utf-8") + file_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
    )
    return req

class TestBackendApi(unittest.TestCase):

    def test_01_root_endpoint(self):
        """Verify GET / returns online status and valid catalog count"""
        req = urllib.request.Request(f"{BACKEND_BASE_URL}/")
        with urllib.request.urlopen(req, timeout=10) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(data.get("status"), "online")
            self.assertEqual(data.get("service"), "Medinex Medicine Recognition AI")
            self.assertGreater(data.get("classes_count", 0), 100)

    def test_02_health_endpoint(self):
        """Verify GET /api/health returns healthy status"""
        req = urllib.request.Request(f"{BACKEND_BASE_URL}/api/health")
        with urllib.request.urlopen(req, timeout=10) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))
            self.assertEqual(data.get("status"), "healthy")
            self.assertTrue(data.get("active"))
            self.assertGreater(data.get("catalog_size", 0), 100)

    def test_03_recognize_valid_image(self):
        """Verify POST /api/medicine/recognize identifies Zyrtec correctly with honest confidence and tiers"""
        if not os.path.exists(TEST_IMAGE_PATH):
            raise unittest.SkipTest(f"Test image not found at {TEST_IMAGE_PATH}")

        with open(TEST_IMAGE_PATH, "rb") as f:
            img_bytes = f.read()

        req = post_multipart(f"{BACKEND_BASE_URL}/api/medicine/recognize", img_bytes, "test_pkg.jpg")
        with urllib.request.urlopen(req, timeout=30) as resp:
            self.assertEqual(resp.status, 200)
            data = json.loads(resp.read().decode("utf-8"))

            self.assertEqual(data.get("status"), "success")
            self.assertEqual(data.get("uncertainty_status"), "confident")
            self.assertIn("visual_classifier_note", data)
            self.assertIn("2.17%", data["visual_classifier_note"])

            medicines = data.get("detected_medicines", [])
            self.assertGreater(len(medicines), 0)

            top = medicines[0]
            self.assertEqual(top.get("name"), "Zyrtec")
            self.assertEqual(top.get("strength"), "10mg")
            self.assertIn("cetirizine", top.get("active_ingredients", []))
            self.assertLessEqual(top.get("confidence", 0), 0.95)
            self.assertNotEqual(top.get("confidence"), 0.99, "Should not claim artificial 99% confidence")
            self.assertNotIn("AI Verified", top.get("verification_tier", ""))
            self.assertNotIn("Verified Manufacturer", top.get("manufacturer", ""))

    def test_04_reject_invalid_file_type(self):
        """Verify POST /api/medicine/recognize rejects non-image files with 400 Bad Request"""
        invalid_bytes = b"This is a plain text file, not an image."
        req = post_multipart(f"{BACKEND_BASE_URL}/api/medicine/recognize", invalid_bytes, "document.txt", content_type="text/plain")
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                self.fail("Expected HTTP 400 for text/plain file")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 400)
            err_body = json.loads(e.read().decode("utf-8"))
            self.assertIn("Invalid file type", err_body.get("detail", ""))

    def test_05_reject_empty_file(self):
        """Verify POST /api/medicine/recognize rejects 0-byte upload with 400 Bad Request"""
        empty_bytes = b""
        req = post_multipart(f"{BACKEND_BASE_URL}/api/medicine/recognize", empty_bytes, "empty.jpg", content_type="image/jpeg")
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                self.fail("Expected HTTP 400 for empty upload")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 400)
            err_body = json.loads(e.read().decode("utf-8"))
            self.assertIn("empty", err_body.get("detail", "").lower())

if __name__ == "__main__":
    unittest.main()
