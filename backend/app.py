"""
Medinex Shared AI Medicine Recognition API (FastAPI)
Universal backend serving Android Java Application & Medinex Web Client.
"""

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import uvicorn
import logging
import os
from starlette.concurrency import run_in_threadpool

from recognizer import MedicineRecognizer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("medinex_api")

app = FastAPI(
    title="Medinex Medicine Recognition AI",
    description="Unified clinical packaging recognition and dosage validation API for Medinex Android and Web.",
    version="1.0.0"
)

# Enable CORS for browser website and external client access.
# allow_credentials is set to False because the recognition endpoint is stateless
# and does not use session cookies; this adheres strictly to W3C CORS standards
# when using allow_origins=["*"].
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize recognizer singleton at startup
recognizer = MedicineRecognizer.get_instance()

@app.get("/")
def root():
    return {
        "service": "Medinex Medicine Recognition AI",
        "status": "online",
        "endpoint": "POST /api/medicine/recognize",
        "classes_count": len(recognizer.catalog)
    }

@app.get("/api/health")
def health():
    return {
        "status": "healthy",
        "catalog_size": len(recognizer.catalog),
        "engine": "RapidOCR + Clinical Guard Engine",
        "active": True
    }

@app.post("/api/medicine/recognize")
async def recognize_medicine(file: UploadFile = File(...)):
    """
    Accepts a medicine packaging image (multipart upload) and returns structured
    identification, OCR text, confirmed strength, confidence, and uncertainty status.
    """
    if not file:
        raise HTTPException(status_code=400, detail="No image file provided in upload.")

    content_type = file.content_type or ""
    if not (content_type.startswith("image/") or file.filename.lower().endswith((".jpg", ".jpeg", ".png", ".webp"))):
        raise HTTPException(status_code=400, detail="Invalid file type. Please upload a JPEG, PNG, or WebP image.")

    MAX_FILE_SIZE = 15 * 1024 * 1024  # 15 MB payload guard
    try:
        image_bytes = await file.read()
        if len(image_bytes) == 0:
            raise HTTPException(status_code=400, detail="Uploaded file is empty.")
        if len(image_bytes) > MAX_FILE_SIZE:
            raise HTTPException(status_code=413, detail="Uploaded file exceeds 15MB limit. Please upload a compressed or standard image.")

        logger.info(f"Received packaging image: name='{file.filename}', size={len(image_bytes)} bytes, content_type='{content_type}'")
        # Run clinical packaging recognition pipeline in worker threadpool to prevent event-loop stalls
        result = await run_in_threadpool(recognizer.recognize_image, image_bytes)
        logger.info(f"Recognition pipeline complete: status='{result.get('status')}', detected={len(result.get('detected_medicines', []))}")
        return JSONResponse(status_code=200, content=result)

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing medicine recognition: {str(e)}", exc_info=True)
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "uncertainty_status": "unknown",
                "message": f"Server recognition error: {str(e)}",
                "detected_medicines": [],
                "extracted_text": "",
                "strength_dosage_text": "",
                "warning": "An unexpected error occurred during image processing.",
                "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
                "disclaimer": "This identification is for informational packaging recognition only and is separate from professional medical advice."
            }
        )

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("app:app", host="0.0.0.0", port=port, timeout_keep_alive=30, reload=False)
