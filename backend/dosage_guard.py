"""
Medinex Clinical Dosage & Strength Guard
Ensures exact extraction and prevents dangerous dosage truncation (e.g. 500 mg vs 5 mg).
"""

import re
from typing import List, Optional, Tuple

# Strict regex with word boundaries to prevent substring confusion (e.g. '500 mg' vs '5 mg')
STRENGTH_REGEX = re.compile(
    r"\b(\d+(?:\.\d+)?)\s*(mg|g|mcg|μg|ml|iu|%)\s*(?:[\/+]\s*(\d+(?:\.\d+)?)\s*(mg|g|mcg|μg|ml|iu|%))?\b",
    re.IGNORECASE
)

# Individual tablet/unit strength boundary
STRENGTH_TOKEN_REGEX = re.compile(
    r"\b(\d+(?:\.\d+)?\s*(?:mg|g|mcg|μg|ml|iu|%)(?:\s*[\/+]\s*\d+(?:\.\d+)?\s*(?:mg|g|mcg|μg|ml|iu|%))?)\b",
    re.IGNORECASE
)

DOSAGE_FORM_REGEX = re.compile(
    r"\b(tablets?|caplets?|capsules?|syrup|suspension|cream|ointment|gel|oral drops?|drops?|sachets?|vial|injection|spray|inhaler|effervescent)\b",
    re.IGNORECASE
)

def normalize_strength_token(token: str) -> str:
    """Normalize whitespace and capitalization for strength tokens: '500  MG' -> '500 mg'."""
    if not token:
        return ""
    clean = token.strip().lower()
    clean = re.sub(r"\s+", " ", clean)
    clean = re.sub(r"(\d)\s+([a-z%])", r"\1 \2", clean)
    clean = re.sub(r"([a-z])\s+([\/+])", r"\1 \2", clean)
    clean = re.sub(r"([\/+])\s+(\d)", r"\1 \2", clean)
    return clean

def extract_all_strengths(text: str) -> List[str]:
    """Extract all readable strength expressions from text."""
    if not text:
        return []
    matches = STRENGTH_TOKEN_REGEX.findall(text)
    seen = set()
    results = []
    for m in matches:
        norm = normalize_strength_token(m)
        if norm and norm not in seen:
            seen.add(norm)
            results.append(norm)
    return results

def extract_dosage_form(text: str) -> Optional[str]:
    """Extract printed dosage form (tablets, caplets, syrup, etc.)."""
    if not text:
        return None
    # Normalize common OCR joined words like film-coatedtablets
    clean = re.sub(r"(coated)(tablets?)", r"\1 \2", text, flags=re.IGNORECASE).replace("-", " ")
    match = DOSAGE_FORM_REGEX.search(clean)
    if match:
        return match.group(1).capitalize()
    return None

def verify_dosage_consistency(detected_strength: str, candidate_expected_strength: str) -> Tuple[bool, str]:
    """
    Check if detected strength matches candidate package catalog strength.
    Strictly forbids guessing or conversion between distinct amounts (e.g. 500 mg vs 5 mg)
    or incompatible physical units (e.g. 10 mg solid tablets vs 10 ml liquid drops).
    """
    if not detected_strength or not candidate_expected_strength:
        return True, "neutral"

    norm_det = normalize_strength_token(detected_strength)
    norm_exp = normalize_strength_token(candidate_expected_strength)

    if norm_det == norm_exp:
        return True, "exact_match"

    # Extract primary numeric components
    det_nums = re.findall(r"\b\d+(?:\.\d+)?\b", norm_det)
    exp_nums = re.findall(r"\b\d+(?:\.\d+)?\b", norm_exp)

    # Check for direct discrepancy between 500 and 5
    if "500" in det_nums and "5" in exp_nums and "500" not in exp_nums:
        return False, "critical_discrepancy_500_vs_5"
    if "5" in det_nums and "500" in exp_nums and "5" not in exp_nums:
        return False, "critical_discrepancy_5_vs_500"

    # Check for unit compatibility
    det_units = re.findall(r"[a-z%]+", norm_det)
    exp_units = re.findall(r"[a-z%]+", norm_exp)

    if det_nums == exp_nums and det_units == exp_units:
        return True, "normalized_match"

    # Incompatible physical forms (e.g. solid mg vs liquid ml)
    solid_units = {"mg", "g", "mcg", "μg"}
    liquid_units = {"ml", "l"}
    if (set(det_units) & solid_units and set(exp_units) & liquid_units) or \
       (set(det_units) & liquid_units and set(exp_units) & solid_units):
        return False, f"unit_incompatibility_{norm_det}_vs_{norm_exp}"

    # If numbers differ significantly, flag discrepancy
    if det_nums and exp_nums and det_nums != exp_nums:
        return False, f"strength_mismatch_{norm_det}_vs_{norm_exp}"

    return True, "partial_evidence"
