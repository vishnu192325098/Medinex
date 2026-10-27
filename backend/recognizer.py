"""
Medinex Multi-Modal Medicine Packaging Recognizer
Combines Trained Packaging Classifier + Class Prototypes (OOD Detection) + RapidOCR + Clinical Dosage Guard.
"""

import os
import json
import re
from typing import Dict, Any, List, Optional
import cv2
import numpy as np
import joblib
from rapidocr_onnxruntime import RapidOCR

import logging

from dosage_guard import (
    extract_all_strengths,
    extract_dosage_form,
    verify_dosage_consistency,
    normalize_strength_token
)

logger = logging.getLogger("medinex_recognizer")

MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")
CATALOG_PATH = os.path.join(os.path.dirname(__file__), "medicine_catalog.json")

# Memory Safety: Maximum pixel dimension for OCR inference.
# Clamping to 1024px preserves sub-millimeter typography legibility while
# reducing ONNX runtime intermediate tensor memory from >400MB to <35MB,
# strictly preventing Out-Of-Memory (OOM) 502 crashes on 512MB hosting tiers.
MAX_OCR_DIMENSION = 1024

class MedicineRecognizer:
    _instance = None

    def __init__(self):
        self.ocr = RapidOCR()
        self.catalog = self._load_catalog()
        self.visual_model = self._load_visual_model()
        self.label_mapping = self._load_label_mapping()
        self.prototypes = self._load_prototypes()

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = MedicineRecognizer()
        return cls._instance

    def _load_catalog(self) -> List[Dict[str, Any]]:
        if os.path.exists(CATALOG_PATH):
            with open(CATALOG_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        return []

    def _load_visual_model(self):
        model_path = os.path.join(MODELS_DIR, "packaging_classifier.joblib")
        if os.path.exists(model_path):
            try:
                return joblib.load(model_path)
            except Exception as e:
                print(f"Warning: Could not load packaging_classifier.joblib: {e}")
        return None

    def _load_label_mapping(self) -> Dict[str, Any]:
        mapping_path = os.path.join(MODELS_DIR, "class_label_mapping.json")
        if os.path.exists(mapping_path):
            try:
                with open(mapping_path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                print(f"Warning: Could not load class_label_mapping.json: {e}")
        return {}

    def _load_prototypes(self) -> Dict[int, np.ndarray]:
        proto_path = os.path.join(MODELS_DIR, "class_prototypes.json")
        if os.path.exists(proto_path):
            try:
                with open(proto_path, "r", encoding="utf-8") as f:
                    raw = json.load(f)
                    return {int(k): np.array(v, dtype=np.float32) for k, v in raw.items()}
            except Exception as e:
                print(f"Warning: Could not load class_prototypes.json: {e}")
        return {}

    def extract_visual_features(self, img_bgr: np.ndarray) -> np.ndarray:
        """Extract multi-scale visual features matching Stage 2 training representation."""
        resized = cv2.resize(img_bgr, (160, 160))

        # 1. Color distribution (HSV 2D Histogram)
        hsv = cv2.cvtColor(resized, cv2.COLOR_BGR2HSV)
        hsv_hist = cv2.calcHist([hsv], [0, 1], None, [18, 8], [0, 180, 0, 256]).flatten()
        hsv_hist = hsv_hist / (np.sum(hsv_hist) + 1e-7)

        # 2. Lab color space for lighting-invariant distribution
        lab = cv2.cvtColor(resized, cv2.COLOR_BGR2LAB)
        l_hist = cv2.calcHist([lab], [0], None, [16], [0, 256]).flatten()
        a_hist = cv2.calcHist([lab], [1], None, [8], [0, 256]).flatten()
        b_hist = cv2.calcHist([lab], [2], None, [8], [0, 256]).flatten()
        lab_hist = np.hstack([l_hist, a_hist, b_hist])
        lab_hist = lab_hist / (np.sum(lab_hist) + 1e-7)

        # 3. Spatial Color Layout (20x20 spatial grid in Lab space)
        spatial_grid = cv2.resize(lab, (20, 20)).astype(np.float32).flatten() / 255.0

        # 4. Gradient Texture & Edge Orientation (Sobel gradients across 4x4 spatial cells)
        gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
        sobelx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
        sobely = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
        mag, ang = cv2.cartToPolar(sobelx, sobely, angleInDegrees=True)
        grad_feats = []
        cell_h, cell_w = 40, 40
        for cy in range(4):
            for cx in range(4):
                cell_mag = mag[cy*cell_h:(cy+1)*cell_h, cx*cell_w:(cx+1)*cell_w]
                cell_ang = ang[cy*cell_h:(cy+1)*cell_h, cx*cell_w:(cx+1)*cell_w]
                hist, _ = np.histogram(cell_ang, bins=8, range=(0, 360), weights=cell_mag)
                hist = hist / (np.sum(hist) + 1e-7)
                grad_feats.extend(hist)
        grad_feats = np.array(grad_feats, dtype=np.float32)

        full_vector = np.hstack([hsv_hist, lab_hist, spatial_grid, grad_feats])
        norm = np.linalg.norm(full_vector)
        if norm > 0:
            full_vector = full_vector / norm
        return full_vector

    def assess_image_quality(self, image_np: np.ndarray) -> Dict[str, Any]:
        """Assess blur, brightness, contrast, and validity of packaging photo."""
        if image_np is None or image_np.size == 0:
            return {"valid": False, "reason": "Empty image buffer"}

        if len(image_np.shape) == 3:
            gray = cv2.cvtColor(image_np, cv2.COLOR_BGR2GRAY)
        else:
            gray = image_np

        h, w = gray.shape
        if h < 80 or w < 80:
            return {"valid": False, "reason": "Image resolution too small"}

        mean_brightness = float(np.mean(gray))
        if mean_brightness < 18:
            return {"valid": False, "reason": "Image too dark to read packaging text"}
        if mean_brightness > 248:
            return {"valid": False, "reason": "Image washed out by glare or flash"}

        laplacian_var = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        is_blurry = laplacian_var < 25.0

        return {
            "valid": True,
            "width": w,
            "height": h,
            "mean_brightness": round(mean_brightness, 1),
            "laplacian_var": round(laplacian_var, 1),
            "is_blurry": is_blurry
        }

    def recognize_image(self, image_bytes: bytes) -> Dict[str, Any]:
        """
        Process medicine packaging image and return unified clinical JSON.
        Multi-modal: Visual Features + Trained Classifier + Prototype Distance + RapidOCR + Strict Dosage Guard.
        """
        # 1. Decode image
        nparr = np.frombuffer(image_bytes, np.uint8)
        img_np = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img_np is None:
            logger.warning("Failed to decode image buffer with cv2.imdecode.")
            return {
                "status": "error",
                "uncertainty_status": "unknown",
                "message": "Invalid or unreadable image file format.",
                "detected_medicines": [],
                "extracted_text": "",
                "strength_dosage_text": "",
                "warning": "The image file could not be decoded. Please upload a valid JPG or PNG file.",
                "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
                "disclaimer": "This identification is for informational packaging recognition only and is separate from professional medical advice."
            }

        # Memory Safety Guard: Downscale oversized images so max dimension <= MAX_OCR_DIMENSION.
        # High-resolution mobile uploads (e.g. 2448x3264 or 4000x3000) cause RapidOCR/ONNX to
        # allocate huge intermediate tensor feature maps (>400MB RAM), tripping the 512MB cgroup OOM
        # limit on cloud instances and triggering HTTP 502 Bad Gateway.
        # Clamping to 1024px preserves sub-millimeter medicine typography while keeping RAM <35MB.
        orig_h, orig_w = img_np.shape[:2]
        max_dim = max(orig_h, orig_w)
        if max_dim > MAX_OCR_DIMENSION:
            scale = float(MAX_OCR_DIMENSION) / float(max_dim)
            new_w = max(1, int(round(orig_w * scale)))
            new_h = max(1, int(round(orig_h * scale)))
            img_np = cv2.resize(img_np, (new_w, new_h), interpolation=cv2.INTER_AREA)
            logger.info(f"Memory Guard: Downscaled image from {orig_w}x{orig_h} to {new_w}x{new_h} (scale={scale:.3f})")
        else:
            logger.info(f"Memory Guard: Image dimensions within budget ({orig_w}x{orig_h})")

        # 2. Quality assessment
        quality = self.assess_image_quality(img_np)
        if not quality["valid"]:
            return {
                "status": "uncertain",
                "uncertainty_status": "unknown",
                "message": quality["reason"],
                "detected_medicines": [],
                "extracted_text": "",
                "strength_dosage_text": "",
                "warning": f"Image quality check failed: {quality['reason']}. Please ensure good lighting and clear focus.",
                "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
                "disclaimer": "This identification is for informational packaging recognition only and is separate from professional medical advice."
            }

        # 3. Visual Feature Extraction & OOD Prototype Matching
        visual_candidates_scores = {}
        max_proto_similarity = 0.0

        try:
            feat_vec = self.extract_visual_features(img_np)

            # Measure prototype cosine similarities
            if self.prototypes:
                for lbl, proto_vec in self.prototypes.items():
                    sim = float(np.dot(feat_vec, proto_vec))
                    if sim > max_proto_similarity:
                        max_proto_similarity = sim

            # Predict probabilities if trained visual model is loaded
            if self.visual_model is not None and self.label_mapping:
                probs = self.visual_model.predict_proba([feat_vec])[0]
                top_indices = np.argsort(probs)[::-1][:10]
                label_info = self.label_mapping.get("label_to_info", {})

                for rank, idx in enumerate(top_indices):
                    info = label_info.get(str(idx))
                    if info:
                        f_name = info["class_folder"]
                        c_idx = info.get("class_index")
                        entry = {
                            "prob": float(probs[idx]),
                            "rank": rank + 1,
                            "medicine_name": info["medicine_name"],
                            "class_index": c_idx,
                            "class_folder": f_name
                        }
                        visual_candidates_scores[f_name] = entry
                        if c_idx is not None:
                            visual_candidates_scores[c_idx] = entry
        except Exception as e:
            logger.warning(f"Visual feature extraction error: {e}")

        # 4. Decoupled OCR execution with defensive fault tolerance & multi-pass enhancement
        ocr_result = None
        try:
            ocr_result, _ = self.ocr(img_np)
        except Exception as ocr_err:
            logger.error(f"RapidOCR execution encountered an error: {str(ocr_err)}", exc_info=True)
            ocr_result = None

        extracted_lines = []
        raw_tokens = []
        ocr_confidences = []

        def _collect_tokens(raw_res):
            lines, tokens, confs = [], [], []
            if raw_res:
                for item in raw_res:
                    t = item[1].strip()
                    c = float(item[2])
                    if t:
                        lines.append(t)
                        tokens.append(t.lower())
                        confs.append(c)
            return lines, tokens, confs

        extracted_lines, raw_tokens, ocr_confidences = _collect_tokens(ocr_result)
        combined_ocr_text = "\n".join(extracted_lines)
        full_lower_text = " ".join(raw_tokens)

        # Multi-Pass Enhancement: If low text extracted, try CLAHE contrast enhancement
        if len(full_lower_text) < 10:
            try:
                lab = cv2.cvtColor(img_np, cv2.COLOR_BGR2LAB)
                l_chan, a_chan, b_chan = cv2.split(lab)
                clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
                enhanced = cv2.cvtColor(cv2.merge((clahe.apply(l_chan), a_chan, b_chan)), cv2.COLOR_LAB2BGR)
                clahe_res, _ = self.ocr(enhanced)
                c_lines, c_tokens, c_confs = _collect_tokens(clahe_res)
                if len(" ".join(c_tokens)) > len(full_lower_text):
                    extracted_lines, raw_tokens, ocr_confidences = c_lines, c_tokens, c_confs
                    combined_ocr_text = "\n".join(extracted_lines)
                    full_lower_text = " ".join(raw_tokens)
                    logger.info("CLAHE contrast enhancement improved OCR text extraction.")
            except Exception as e:
                logger.debug(f"CLAHE pass skipped: {e}")

        # If still low text, try 90-degree clockwise orientation
        if len(full_lower_text) < 10:
            try:
                rot_img = cv2.rotate(img_np, cv2.ROTATE_90_CLOCKWISE)
                rot_res, _ = self.ocr(rot_img)
                r_lines, r_tokens, r_confs = _collect_tokens(rot_res)
                if len(" ".join(r_tokens)) > len(full_lower_text):
                    extracted_lines, raw_tokens, ocr_confidences = r_lines, r_tokens, r_confs
                    combined_ocr_text = "\n".join(extracted_lines)
                    full_lower_text = " ".join(raw_tokens)
                    logger.info("90-degree rotated orientation pass improved OCR text extraction.")
            except Exception as e:
                logger.debug(f"Rotation pass skipped: {e}")

        # 5. Extract strengths & dosage form from OCR text
        detected_strengths = extract_all_strengths(combined_ocr_text)
        detected_form = extract_dosage_form(combined_ocr_text)
        primary_strength = detected_strengths[0] if detected_strengths else ""
        squashed_ocr_text = re.sub(r"[^a-z0-9]", "", full_lower_text)

        # 6. Reject unsupported, unrelated, or unreadable images (Strict Anti-Guessing Guard)
        has_text = len(extracted_lines) > 0 and len(full_lower_text) >= 4
        is_visual_supported = max_proto_similarity >= 0.35

        if not has_text and not is_visual_supported:
            return {
                "status": "unrecognized",
                "uncertainty_status": "unknown",
                "message": "Unsupported or unrelated image. No medicine packaging or text detected.",
                "detected_medicines": [],
                "extracted_text": combined_ocr_text,
                "strength_dosage_text": "",
                "warning": "The submitted image does not appear to be a recognized medicine package. Automatic guessing is strictly forbidden.",
                "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
                "disclaimer": "This identification is for informational packaging recognition only and is separate from professional medical advice."
            }

        if not has_text:
            return {
                "status": "unreadable",
                "uncertainty_status": "unknown",
                "message": "No readable pharmaceutical label text detected.",
                "detected_medicines": [],
                "extracted_text": "",
                "strength_dosage_text": "",
                "warning": "Insufficient label evidence. Please align the packaging flat under clear lighting and avoid glare.",
                "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
                "disclaimer": "This identification is for informational packaging recognition only and is separate from professional medical advice."
            }

        # 7. Score candidates against catalog with Multi-Modal Late Fusion & Robust Matching
        scored_candidates = []
        critical_discrepancy_detected = False
        discrepancy_message = None

        salt_strip_regex = re.compile(
            r"\b(hcl|hydrochloride|sodium|potassium|maleate|tartrate|succinate|fumarate|monohydrate|hydrate|phosphate|dipropionate|valerate|acetate|citrate)\b",
            re.IGNORECASE
        )

        for med in self.catalog:
            med_id = med.get("id")
            brand = med["brand_name"].lower().strip()
            salt = med["active_salt"].lower().strip()
            exp_strength = med["strength"].lower().strip()
            aliases = med.get("aliases", [brand])
            f_name = med.get("class_folder") or med.get("raw_folder_name", "")

            score = 0.0
            evidence = []

            # A. Robust Brand Matching (exact, hyphen/space flexible, squashed)
            brand_matched = False
            if re.search(r"\b" + re.escape(brand) + r"\b", full_lower_text):
                score += 0.50
                evidence.append(f"Exact brand match: '{med['brand_name']}'")
                brand_matched = True
            else:
                try:
                    flex_pat = r"\b" + re.sub(r"[-\s]+", r"[-\\s]*", re.escape(brand)) + r"\b"
                    if re.search(flex_pat, full_lower_text):
                        score += 0.50
                        evidence.append(f"Brand match: '{med['brand_name']}'")
                        brand_matched = True
                except Exception:
                    pass

            if not brand_matched:
                b_squash = re.sub(r"[^a-z0-9]", "", brand)
                if len(b_squash) >= 4 and b_squash in squashed_ocr_text:
                    score += 0.45
                    evidence.append(f"Brand match (normalized): '{med['brand_name']}'")
                    brand_matched = True
                elif any(re.search(r"\b" + re.escape(al.lower().strip()) + r"\b", full_lower_text) for al in aliases):
                    score += 0.35
                    evidence.append(f"Alias match for: '{med['brand_name']}'")
                    brand_matched = True
                elif any(len(re.sub(r"[^a-z0-9]", "", al.lower())) >= 4 and re.sub(r"[^a-z0-9]", "", al.lower()) in squashed_ocr_text for al in aliases):
                    score += 0.35
                    evidence.append(f"Alias match (normalized): '{med['brand_name']}'")
                    brand_matched = True

            # B. Active Ingredient Matching (with counter-ion / salt-form stripping)
            if salt and len(salt) > 3:
                salt_parts = [s.strip() for s in salt.split("+")]
                salt_hits = 0
                for sp in salt_parts:
                    sp_core = salt_strip_regex.sub("", sp).strip()
                    sp_squash = re.sub(r"[^a-z0-9]", "", sp_core if sp_core else sp)
                    if re.search(r"\b" + re.escape(sp) + r"\b", full_lower_text):
                        salt_hits += 1
                    elif sp_core and re.search(r"\b" + re.escape(sp_core) + r"\b", full_lower_text):
                        salt_hits += 1
                    elif len(sp_squash) >= 4 and sp_squash in squashed_ocr_text:
                        salt_hits += 1

                if salt_hits == len(salt_parts):
                    score += 0.30
                    evidence.append(f"Active ingredient confirmed: '{med['active_salt']}'")
                elif salt_hits > 0:
                    score += 0.15
                    evidence.append(f"Partial ingredient: '{med['active_salt']}'")

            # B. Visual Model Signals (Informational only: weight=0.0 due to 2.17% baseline test accuracy)
            vis_info = visual_candidates_scores.get(med_id) or visual_candidates_scores.get(f_name)
            if vis_info:
                vis_prob = vis_info["prob"]
                rank = vis_info["rank"]
                evidence.append(
                    f"Visual model signal (experimental, unweighted): rank #{rank} (prob {vis_prob:.2f})"
                )

            # Prototype similarity check for this specific class (informational only: weight=0.0)
            proto_idx = (med_id - 1) if med_id is not None else None
            if proto_idx is not None and proto_idx in self.prototypes:
                proto_sim = float(np.dot(feat_vec, self.prototypes[proto_idx]))
                if proto_sim > 0.70:
                    evidence.append(f"Prototype cosine similarity (experimental, unweighted): {proto_sim:.2f}")

            # C. Strict Dosage Guard Consistency Verification across all detected strengths
            strength_consistent = True
            matched_strength = primary_strength
            if detected_strengths and exp_strength:
                any_match = False
                for ds in detected_strengths:
                    is_consistent, status_msg = verify_dosage_consistency(ds, exp_strength)
                    if is_consistent and status_msg in ["exact_match", "normalized_match"]:
                        any_match = True
                        matched_strength = ds
                        break

                if any_match:
                    score += 0.20
                    evidence.append(f"Dosage verified: {matched_strength}")
                elif primary_strength:
                    is_consistent, status_msg = verify_dosage_consistency(primary_strength, exp_strength)
                    if not is_consistent:
                        if not brand_matched or "500_vs_5" in status_msg or "unit_incompatibility" in status_msg:
                            strength_consistent = False
                            critical_discrepancy_detected = True
                            discrepancy_message = (
                                f"CRITICAL DOSAGE DISCREPANCY: Packaging text indicates '{primary_strength}' "
                                f"but candidate catalog requires '{exp_strength}'. Automatic conversion rejected."
                            )
                            score -= 0.60
                            evidence.append(discrepancy_message)

            # Dosage form consistency check
            if detected_form and med["dosage_form"]:
                if detected_form.lower() in med["dosage_form"].lower():
                    score += 0.05

            # Detected manufacturer from OCR label if present
            manufacturer = med.get("manufacturer") or "Not specified on packaging"
            if "gsk" in full_lower_text and "zyrtec" in brand:
                manufacturer = "GlaxoSmithKline (GSK)"
            elif "pfizer" in full_lower_text:
                manufacturer = "Pfizer"
            elif "sanofi" in full_lower_text:
                manufacturer = "Sanofi"
            elif "novartis" in full_lower_text:
                manufacturer = "Novartis"
            elif "bayer" in full_lower_text:
                manufacturer = "Bayer"

            # Brand Precedence Rule: If packaging has an exact/confirmed brand name printed on it,
            # prioritize this candidate over ingredient-only matches that have zero brand match
            if brand_matched:
                score += 0.10

            if (score > 0.35 or brand_matched) and strength_consistent:
                # Realistic confidence cap at 0.95 (never 0.99 or 1.0)
                calibrated_conf = round(min(max(score, 0.10), 0.95), 2)
                # Honest verification tier: Candidate Match based on OCR label matching (never 'Verified' or 'AI Verified')
                v_tier = "Candidate Match (OCR Verified)" if calibrated_conf >= 0.60 else "Candidate Match (Unconfirmed)"

                scored_candidates.append({
                    "name": med["brand_name"],
                    "brand_candidates": [f"{med['brand_name']} ({med['dosage_form']})"],
                    "active_ingredients": [med["active_salt"]] if med["active_salt"] else [],
                    "strength": primary_strength or med["strength"] or "Standard Dose",
                    "dosage_form": med["dosage_form"],
                    "manufacturer": manufacturer,
                    "category": med["category"],
                    "route": med["route"],
                    "confidence": calibrated_conf,
                    "verification_tier": v_tier,
                    "evidence": evidence
                })

        # Sort by confidence descending
        scored_candidates.sort(key=lambda x: -x["confidence"])

        # 8. Out-of-Distribution or Unrecognized Packaging Handling
        if len(scored_candidates) == 0:
            warning_text = discrepancy_message if critical_discrepancy_detected else (
                "The detected packaging does not confidently match any cataloged medicine. "
                "In accordance with clinical safety, dosages and names are never guessed."
            )
            return {
                "status": "unrecognized",
                "uncertainty_status": "unknown",
                "message": "Medicine package could not be verified against clinical catalog.",
                "recognition_method": "OCR Text Extraction + Clinical Catalog Concordance",
                "visual_classifier_note": "Visual packaging model is experimental (evaluated 2.17% top-1 accuracy on 150 classes); its predictions do not influence candidate scoring.",
                "detected_medicines": [],
                "extracted_text": combined_ocr_text,
                "strength_dosage_text": primary_strength,
                "warning": warning_text,
                "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
                "disclaimer": "This candidate identification is based on extracted label text and catalog matching. It does not constitute certified physical packaging authentication."
            }

        top_cand = scored_candidates[0]

        # Determine Uncertainty Status based on top candidate
        top_has_discrepancy = any("CRITICAL DOSAGE DISCREPANCY" in str(ev) for ev in top_cand.get("evidence", []))
        if top_has_discrepancy:
            uncertainty_status = "uncertain"
            warning = discrepancy_message
        elif top_cand["confidence"] >= 0.65:
            uncertainty_status = "confident"
            warning = None
        else:
            uncertainty_status = "uncertain"
            warning = f"Moderate match confidence ({int(top_cand['confidence'] * 100)}%). Verification against physical package label recommended."

        return {
            "status": "success",
            "uncertainty_status": uncertainty_status,
            "recognition_method": "OCR Text Extraction + Clinical Catalog Concordance + Dosage Guard",
            "visual_classifier_note": "Visual packaging model is experimental (evaluated 2.17% top-1 accuracy on 150 classes); its predictions do not influence candidate scoring.",
            "detected_medicines": scored_candidates[:3],
            "extracted_text": combined_ocr_text,
            "strength_dosage_text": primary_strength or top_cand["strength"],
            "warning": warning,
            "loose_pill_notice": "Loose-pill visual identification requires laboratory pill-imprint confirmation. Please scan the original box or blister strip.",
            "disclaimer": "This candidate identification is based on extracted label text and catalog matching. It does not constitute certified physical packaging authentication. Always verify with a licensed healthcare professional."
        }
