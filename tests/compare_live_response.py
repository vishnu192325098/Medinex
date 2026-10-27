"""
Script to capture and display raw API response and compare it against Web and Android UI fields.
"""
import json
import urllib.request
import uuid

IMAGE_PATH = r"C:\Users\Lenovo\Downloads\bjy2svvmn8-1\bjy2svvmn8-1\Zyrtec 20 tablets\huawei cn (27).jpg"

with open(IMAGE_PATH, "rb") as f:
    img_bytes = f.read()

boundary = uuid.uuid4().hex
body = (
    f"--{boundary}\r\n"
    f'Content-Disposition: form-data; name="file"; filename="zyrtec.jpg"\r\n'
    f"Content-Type: image/jpeg\r\n\r\n"
).encode("utf-8") + img_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

req = urllib.request.Request(
    "http://127.0.0.1:8000/api/medicine/recognize",
    data=body,
    headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
)

with urllib.request.urlopen(req, timeout=30) as r:
    raw = json.loads(r.read().decode("utf-8"))

print("=== RAW FASTAPI RESPONSE ===")
print(json.dumps(raw, indent=2))

med0 = raw["detected_medicines"][0] if raw.get("detected_medicines") else {}

print("\n=== ANDROID CLIENT UI RENDER (UploadActivity.renderSharedAiResult) ===")
print(f"Header Title      : MEDINEX SHARED AI MEDICINE RECOGNITION REPORT")
print(f"Engine            : FastAPI + RapidOCR + Clinical Dosage Guard")
print(f"Method            : {raw.get('recognition_method')}")
print(f"Visual Model Note : {raw.get('visual_classifier_note')}")
status_badge = "[STATUS: PROBABLE MATCH - Candidate Identified]" if raw.get("uncertainty_status") == "confident" else "[STATUS: UNCERTAIN - Clinical Verification Advised]"
print(f"Status Badge      : {status_badge}")
print(f"Verified Strength : {raw.get('strength_dosage_text')}")
print(f"Product           : {med0.get('name')}")
print(f"Active Salts      : {', '.join(med0.get('active_ingredients', []))}")
print(f"Strength          : {med0.get('strength')}")
print(f"Dosage Form       : {med0.get('dosage_form')}")
print(f"Manufacturer      : {med0.get('manufacturer', 'Not specified on package')}")
print(f"Confidence        : {round(med0.get('confidence', 0) * 100)}%")
print(f"Verification Tier : {med0.get('verification_tier')}")

print("\n=== WEBSITE UI RENDER (renderSharedAiRecognitionResult) ===")
print(f"Recognition Pipeline: {raw.get('recognition_method')}")
print(f"Visual Model Note   : {raw.get('visual_classifier_note')}")
web_tier_badge = f"Probable Match ({round(med0.get('confidence', 0) * 100)}%)" if raw.get("uncertainty_status") == "confident" else "Candidate Match (Clinical Verification Advised)"
print(f"Tier Badge (Pill)   : {web_tier_badge}")
print(f"Medicine Title      : {med0.get('name')}")
print(f"Active Ingredient   : {' + '.join(med0.get('active_ingredients', []))}")
print(f"Strength            : {med0.get('strength')}")
print(f"Dosage Form         : {med0.get('dosage_form')}")
print(f"Manufacturer        : {med0.get('manufacturer', 'Not specified on packaging')}")
print(f"Confidence          : {round(med0.get('confidence', 0) * 100)}%")
print(f"Verification Tier   : {med0.get('verification_tier')}")
