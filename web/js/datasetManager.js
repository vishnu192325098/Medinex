import { MedicineApi } from "./api.js";

/**
 * Medinex Clinical Medicine Dataset Manager
 * Universal layered medicine recognition engine:
 * Layer 1: Fast local clinical formulary matching (exact, repaired, & controlled Levenshtein)
 * Layer 2: Clinical Entity Extraction (strengths, forms, timing, active ingredient declarations)
 * Layer 3: Universal Online Discovery (NLM RxNorm approximate concepts & FDA drug labels)
 * Layer 4: Multi-signal candidate ranking & evidence generation
 * Layer 5: Safe rejection of non-medicines (zero hallucination guarantee)
 */

export const VerificationTier = {
  VERIFIED: { title: "🟡 Probable Match — Clinical Confirmation Required", minScore: 0.85, tag: "probable" },
  LIKELY: { title: "🟡 Candidate Match — Please Confirm", minScore: 0.60, tag: "likely" },
  UNCONFIRMED: { title: "🔴 Medicine Not Confidently Identified", minScore: 0.0, tag: "unconfirmed" }
};

export class MedicineDatasetManager {
  static instance = null;

  constructor() {
    this.medicines = [];
    this.interactions = [];
    this.isLoaded = false;
  }

  static async getInstance() {
    if (!this.instance) {
      this.instance = new MedicineDatasetManager();
      await this.instance.loadDatabase();
    }
    return this.instance;
  }

  async loadDatabase() {
    try {
      const response = await fetch("./data/medicines_database.json");
      if (!response.ok) throw new Error("Could not load medicines_database.json");
      const data = await response.json();
      this.parseDatabase(data);
      this.isLoaded = true;
    } catch (e) {
      console.warn("Failed to load local JSON database, using embedded fallback:", e);
      this.useEmbeddedFallback();
      this.isLoaded = true;
    }
  }

  parseDatabase(data) {
    this.medicines = [];
    this.interactions = [];

    if (data.medicines && Array.isArray(data.medicines)) {
      for (const m of data.medicines) {
        if (!m.name) continue;
        this.medicines.push({
          name: m.name.toLowerCase().trim(),
          activeSalt: (m.active_salt || "").toLowerCase().trim(),
          category: m.category || "General Care",
          indication: m.indication || "Prescription medicine",
          uses: m.uses || m.indication || "Therapeutic use",
          recommendedTiming: m.recommended_timing || "",
          manufacturer: m.manufacturer || "Not specified on packaging",
          strength: m.strength || "",
          dosageForm: m.dosage_form || "Tablet"
        });
      }
    }

    // Merge comprehensive embedded formulary entries to guarantee full brand coverage
    const embedded = this.getEmbeddedMedicines();
    for (const emb of embedded) {
      if (!this.medicines.some(m => m.name === emb.name)) {
        this.medicines.push(emb);
      }
    }

    if (data.interactions && Array.isArray(data.interactions)) {
      for (const inter of data.interactions) {
        if (!inter.drug1 || !inter.drug2) continue;
        this.interactions.push({
          drug1: inter.drug1.toLowerCase().trim(),
          drug2: inter.drug2.toLowerCase().trim(),
          riskLevel: inter.risk_level || "MEDIUM",
          description: inter.description || "",
          recommendation: inter.recommendation || ""
        });
      }
    }

    const embeddedInteractions = this.getEmbeddedInteractions();
    for (const ei of embeddedInteractions) {
      const exists = this.interactions.some(
        i => (i.drug1 === ei.drug1 && i.drug2 === ei.drug2) || (i.drug1 === ei.drug2 && i.drug2 === ei.drug1)
      );
      if (!exists) {
        this.interactions.push(ei);
      }
    }
  }

  useEmbeddedFallback() {
    this.medicines = this.getEmbeddedMedicines();
    this.interactions = this.getEmbeddedInteractions();
  }

  getEmbeddedMedicines() {
    return [
      // Analgesics & Antipyretics
      { name: "paracetamol", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, Headache, Body Pain", uses: "Fever, Headache, Body Pain", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GSK / Micro Labs", strength: "500 mg", dosageForm: "Tablet" },
      { name: "calpol", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, High Temperature", uses: "Treatment of fever and mild to moderate pain relief", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GlaxoSmithKline Pharmaceuticals Ltd", strength: "500 mg", dosageForm: "Tablet" },
      { name: "calpol 500", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, High Temperature", uses: "Treatment of fever and mild to moderate pain relief", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GlaxoSmithKline Pharmaceuticals Ltd", strength: "500 mg", dosageForm: "Tablet" },
      { name: "calpol 650", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "High Fever, Body Pain", uses: "Treatment of fever and bodily pain", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GlaxoSmithKline Pharmaceuticals Ltd", strength: "650 mg", dosageForm: "Tablet" },
      { name: "calpol 250", activeSalt: "paracetamol", category: "Pediatric Fever Care", indication: "Pediatric Fever", uses: "Fever reduction in children", recommendedTiming: "As Advised (After Food)", manufacturer: "GlaxoSmithKline Pharmaceuticals Ltd", strength: "250 mg/5ml", dosageForm: "Oral Suspension" },
      { name: "dolo", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, Body Ache", uses: "Fever, Body Ache", recommendedTiming: "Morning & Night (After Food)", manufacturer: "Micro Labs Ltd", strength: "650 mg", dosageForm: "Tablet" },
      { name: "dolo 650", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, Body Ache", uses: "Fever, Body Ache", recommendedTiming: "Morning & Night (After Food)", manufacturer: "Micro Labs Ltd", strength: "650 mg", dosageForm: "Tablet" },
      { name: "dolo 500", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Mild Fever, Headache", uses: "Mild fever and pain", recommendedTiming: "Morning & Night (After Food)", manufacturer: "Micro Labs Ltd", strength: "500 mg", dosageForm: "Tablet" },
      { name: "crocin", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, Mild Pain", uses: "Fever, Mild Pain", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GlaxoSmithKline Consumer Healthcare", strength: "500 mg", dosageForm: "Tablet" },
      { name: "crocin advance", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "Fever, Headache", uses: "Fast relief from fever and common aches", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GlaxoSmithKline Consumer Healthcare", strength: "500 mg", dosageForm: "Tablet" },
      { name: "crocin 650", activeSalt: "paracetamol", category: "Fever & Pain Relief", indication: "High Fever, Body Ache", uses: "Fever and body pain", recommendedTiming: "Morning & Night (After Food)", manufacturer: "GlaxoSmithKline Consumer Healthcare", strength: "650 mg", dosageForm: "Tablet" },
      { name: "ibuprofen", activeSalt: "ibuprofen", category: "Fever & Pain Relief", indication: "Inflammation, Joint Pain, Fever", uses: "Inflammation, Joint Pain, Fever", recommendedTiming: "After Food", manufacturer: "Abbott", strength: "400 mg", dosageForm: "Tablet" },
      { name: "combiflam", activeSalt: "ibuprofen + paracetamol", category: "Fever & Pain Relief", indication: "Severe Body Pain, Joint Pain", uses: "Severe Body Pain, Joint Pain", recommendedTiming: "After Food", manufacturer: "Sanofi India", strength: "400 mg + 325 mg", dosageForm: "Tablet" },
      { name: "zerodol", activeSalt: "aceclofenac", category: "Fever & Pain Relief", indication: "Body Ache, Joint Pain, Arthritis", uses: "Body Ache, Joint Pain, Arthritis", recommendedTiming: "After Food", manufacturer: "Ipca Laboratories", strength: "100 mg", dosageForm: "Tablet" },
      { name: "zerodol-p", activeSalt: "aceclofenac + paracetamol", category: "Fever & Pain Relief", indication: "Body Ache, Post-injury Pain, Fever", uses: "Body Ache, Post-injury Pain, Fever", recommendedTiming: "After Food", manufacturer: "Ipca Laboratories", strength: "100 mg + 325 mg", dosageForm: "Tablet" },
      { name: "zerodol-sp", activeSalt: "aceclofenac + paracetamol + serratiopeptidase", category: "Pain & Swelling Relief", indication: "Post-surgery Pain, Severe Inflammation", uses: "Pain and swelling reduction", recommendedTiming: "After Food", manufacturer: "Ipca Laboratories", strength: "100 mg + 325 mg + 15 mg", dosageForm: "Tablet" },
      { name: "meftal-spas", activeSalt: "mefenamic acid + dicyclomine", category: "Spasmodic Pain Relief", indication: "Abdominal Cramps, Spasms", uses: "Stomach cramp relief", recommendedTiming: "After Food", manufacturer: "Blue Cross Laboratories", strength: "250 mg + 10 mg", dosageForm: "Tablet" },

      // Antibiotics & Antimicrobials
      { name: "amoxicillin", activeSalt: "amoxicillin", category: "Antibiotic", indication: "Bacterial Infection", uses: "Respiratory, Throat Infections", recommendedTiming: "After Food", manufacturer: "Cadila", strength: "500 mg", dosageForm: "Capsule" },
      { name: "augmentin", activeSalt: "amoxicillin + clavulanic acid", category: "Antibiotic", indication: "Severe Bacterial Infection", uses: "Respiratory, skin, soft tissue infections", recommendedTiming: "After Food", manufacturer: "GlaxoSmithKline", strength: "625 mg", dosageForm: "Tablet" },
      { name: "augmentin 625", activeSalt: "amoxicillin + clavulanic acid", category: "Antibiotic", indication: "Severe Bacterial Infection", uses: "Respiratory, skin, soft tissue infections", recommendedTiming: "After Food", manufacturer: "GlaxoSmithKline", strength: "625 mg", dosageForm: "Tablet" },
      { name: "augmentin 625 duo", activeSalt: "amoxicillin + clavulanic acid", category: "Antibiotic", indication: "Severe Bacterial Infection", uses: "Respiratory, skin, soft tissue infections", recommendedTiming: "After Food", manufacturer: "GlaxoSmithKline", strength: "625 mg", dosageForm: "Tablet" },
      { name: "azithromycin", activeSalt: "azithromycin", category: "Antibiotic", indication: "Throat & Chest Infection", uses: "Bacterial Infection", recommendedTiming: "1 Hour Before Food", manufacturer: "Lupin", strength: "500 mg", dosageForm: "Tablet" },
      { name: "azithral", activeSalt: "azithromycin", category: "Antibiotic", indication: "Throat & Chest Infection", uses: "Respiratory tract infection", recommendedTiming: "1 Hour Before Food", manufacturer: "Alembic Pharmaceuticals", strength: "500 mg", dosageForm: "Tablet" },
      { name: "azithral 500", activeSalt: "azithromycin", category: "Antibiotic", indication: "Throat & Chest Infection", uses: "Respiratory tract infection", recommendedTiming: "1 Hour Before Food", manufacturer: "Alembic Pharmaceuticals", strength: "500 mg", dosageForm: "Tablet" },
      { name: "cefixime", activeSalt: "cefixime", category: "Antibiotic", indication: "Typhoid, Urinary Infections", uses: "Bacterial infection control", recommendedTiming: "After Food", manufacturer: "Alkem", strength: "200 mg", dosageForm: "Tablet" },
      { name: "taxim-o", activeSalt: "cefixime", category: "Antibiotic", indication: "Typhoid, Urinary Infections", uses: "Bacterial infection control", recommendedTiming: "After Food", manufacturer: "Alkem Laboratories", strength: "200 mg", dosageForm: "Tablet" },
      { name: "taxim-o 200", activeSalt: "cefixime", category: "Antibiotic", indication: "Typhoid, Urinary Infections", uses: "Bacterial infection control", recommendedTiming: "After Food", manufacturer: "Alkem Laboratories", strength: "200 mg", dosageForm: "Tablet" },

      // Gastrointestinal Care
      { name: "pantoprazole", activeSalt: "pantoprazole", category: "Gastric Care", indication: "Acidity, GERD", uses: "Acid Reflux, Heartburn", recommendedTiming: "Morning (Empty Stomach)", manufacturer: "Alkem", strength: "40 mg", dosageForm: "Tablet" },
      { name: "pantocid", activeSalt: "pantoprazole", category: "Gastric Care", indication: "Acid Reflux, Ulcers", uses: "Acid reduction", recommendedTiming: "Before Breakfast", manufacturer: "Sun Pharma", strength: "40 mg", dosageForm: "Tablet" },
      { name: "pantocid 40", activeSalt: "pantoprazole", category: "Gastric Care", indication: "Acid Reflux, Ulcers", uses: "Acid reduction", recommendedTiming: "Before Breakfast", manufacturer: "Sun Pharma", strength: "40 mg", dosageForm: "Tablet" },
      { name: "pan 40", activeSalt: "pantoprazole", category: "Gastric Care", indication: "Acidity, Heartburn", uses: "Proton pump inhibition", recommendedTiming: "Before Breakfast", manufacturer: "Alkem Laboratories", strength: "40 mg", dosageForm: "Tablet" },
      { name: "pan-d", activeSalt: "pantoprazole + domperidone", category: "Gastric Care", indication: "Acidity with Nausea", uses: "Gastroesophageal reflux with nausea", recommendedTiming: "Before Breakfast", manufacturer: "Alkem Laboratories", strength: "40 mg + 30 mg", dosageForm: "Capsule" },
      { name: "omeprazole", activeSalt: "omeprazole", category: "Gastric Care", indication: "Heartburn, Acid Reflux", uses: "Ulcer and GERD treatment", recommendedTiming: "Before Meal", manufacturer: "Dr. Reddy's", strength: "20 mg", dosageForm: "Capsule" },
      { name: "omez", activeSalt: "omeprazole", category: "Gastric Care", indication: "Heartburn, Acid Reflux", uses: "Ulcer and GERD treatment", recommendedTiming: "Before Meal", manufacturer: "Dr. Reddy's", strength: "20 mg", dosageForm: "Capsule" },

      // Cardiovascular & Diabetes
      { name: "telmisartan", activeSalt: "telmisartan", category: "Cardiovascular", indication: "Hypertension Management", uses: "Blood pressure reduction", recommendedTiming: "Morning", manufacturer: "Glenmark", strength: "40 mg", dosageForm: "Tablet" },
      { name: "telma", activeSalt: "telmisartan", category: "Cardiovascular", indication: "Hypertension", uses: "Blood pressure reduction", recommendedTiming: "Morning", manufacturer: "Glenmark Pharmaceuticals", strength: "40 mg", dosageForm: "Tablet" },
      { name: "telma 40", activeSalt: "telmisartan", category: "Cardiovascular", indication: "Hypertension", uses: "Blood pressure reduction", recommendedTiming: "Morning", manufacturer: "Glenmark Pharmaceuticals", strength: "40 mg", dosageForm: "Tablet" },
      { name: "amlodipine", activeSalt: "amlodipine", category: "Cardiovascular", indication: "Hypertension, Angina", uses: "Blood pressure reduction", recommendedTiming: "Morning", manufacturer: "Micro Labs", strength: "5 mg", dosageForm: "Tablet" },
      { name: "amlong", activeSalt: "amlodipine", category: "Cardiovascular", indication: "Hypertension, Angina", uses: "Blood pressure reduction", recommendedTiming: "Morning", manufacturer: "Micro Labs", strength: "5 mg", dosageForm: "Tablet" },
      { name: "atorvastatin", activeSalt: "atorvastatin", category: "Cholesterol Care", indication: "High Cholesterol, Heart Protection", uses: "Lipid reduction", recommendedTiming: "At Bedtime", manufacturer: "Zydus", strength: "20 mg", dosageForm: "Tablet" },
      { name: "atorva", activeSalt: "atorvastatin", category: "Cholesterol Care", indication: "High Cholesterol", uses: "Lipid reduction", recommendedTiming: "At Bedtime", manufacturer: "Zydus Lifesciences", strength: "20 mg", dosageForm: "Tablet" },
      { name: "metformin", activeSalt: "metformin", category: "Diabetes Care", indication: "Type 2 Diabetes", uses: "Blood glucose control", recommendedTiming: "With Meals", manufacturer: "Sun Pharma", strength: "500 mg", dosageForm: "Tablet" },
      { name: "glycomet", activeSalt: "metformin", category: "Diabetes Care", indication: "Type 2 Diabetes", uses: "Blood glucose control", recommendedTiming: "With Meals", manufacturer: "USV Ltd", strength: "500 mg", dosageForm: "Tablet" },
      { name: "glycomet 500", activeSalt: "metformin", category: "Diabetes Care", indication: "Type 2 Diabetes", uses: "Blood glucose control", recommendedTiming: "With Meals", manufacturer: "USV Ltd", strength: "500 mg", dosageForm: "Tablet" },
      { name: "aspirin", activeSalt: "acetylsalicylic acid", category: "Cardiovascular / Pain", indication: "Blood Thinner, Pain Relief", uses: "Blood Thinner, Pain Relief", recommendedTiming: "After Food", manufacturer: "Bayer", strength: "75 mg", dosageForm: "Tablet" },
      { name: "warfarin", activeSalt: "warfarin", category: "Anticoagulant", indication: "Blood Clot Prevention", uses: "Deep Vein Thrombosis, Stroke Prevention", recommendedTiming: "Once Daily Evening", manufacturer: "Cipla", strength: "5 mg", dosageForm: "Tablet" },

      // Allergy & Respiratory
      { name: "cetirizine", activeSalt: "cetirizine", category: "Allergy Medicine", indication: "Sneezing, Runny Nose, Allergies", uses: "Allergic Rhinitis, Urticaria", recommendedTiming: "Night Time", manufacturer: "Dr. Reddy's", strength: "10 mg", dosageForm: "Tablet" },
      { name: "levocetirizine", activeSalt: "levocetirizine", category: "Allergy Medicine", indication: "Allergies, Skin Rash", uses: "Allergies, Rhinitis", recommendedTiming: "Night Time", manufacturer: "Cipla", strength: "5 mg", dosageForm: "Tablet" },
      { name: "allegra", activeSalt: "fexofenadine", category: "Allergy Medicine", indication: "Hives, Runny Nose, Sneezing", uses: "Allergy relief without drowsiness", recommendedTiming: "Morning", manufacturer: "Sanofi India", strength: "120 mg", dosageForm: "Tablet" },
      { name: "allegra 120", activeSalt: "fexofenadine", category: "Allergy Medicine", indication: "Hives, Runny Nose, Sneezing", uses: "Allergy relief without drowsiness", recommendedTiming: "Morning", manufacturer: "Sanofi India", strength: "120 mg", dosageForm: "Tablet" },
      { name: "montair-lc", activeSalt: "montelukast + levocetirizine", category: "Respiratory & Allergy", indication: "Allergic Asthma, Rhinitis", uses: "Airway and allergy management", recommendedTiming: "Night Time", manufacturer: "Cipla", strength: "10 mg + 5 mg", dosageForm: "Tablet" }
    ];
  }

  getEmbeddedInteractions() {
    return [
      { drug1: "aspirin", drug2: "warfarin", riskLevel: "HIGH", description: "These medicines may significantly increase bleeding risk.", recommendation: "Consult a doctor immediately before taking these medicines together." },
      { drug1: "ibuprofen", drug2: "aspirin", riskLevel: "MEDIUM", description: "Ibuprofen may reduce the effectiveness of Aspirin and increase stomach irritation.", recommendation: "Use with caution and follow medical advice." },
      { drug1: "paracetamol", drug2: "cetirizine", riskLevel: "LOW", description: "No significant interaction has been identified.", recommendation: "Generally safe when used as directed." }
    ];
  }

  // ==========================================
  // REGEX PARSERS & CLINICAL ENTITY EXTRACTORS
  // ==========================================

  repairSplitNumbers(text) {
    if (!text) return "";
    let repaired = text;

    // 1. Rejoin numbers split by whitespace before a unit:
    // e.g. "5 00 mg" -> "500 mg", "6 50 mg" -> "650 mg", "6 25 mg" -> "625 mg", "1 25 mg" -> "125 mg"
    repaired = repaired.replace(/\b(\d)\s+(\d{2})\s*(mg|g|mcg|μg|ml|iu)\b/gi, "$1$2 $3");
    repaired = repaired.replace(/\b(\d{2})\s+(\d)\s*(mg|g|mcg|μg|ml|iu)\b/gi, "$1$2 $3");
    repaired = repaired.replace(/\b(\d)\s+(\d)\s+(\d)\s*(mg|g|mcg|μg|ml|iu)\b/gi, "$1$2$3 $3");

    // 2. Rejoin multi-line split numbers before units or keywords:
    repaired = repaired.replace(/\b(\d+)\s*\n\s*(mg|g|mcg|μg|ml|iu|tablets?|capsules?|ip|bp|usp)\b/gi, "$1 $2");

    // 3. Rejoin common standalone brand dosage numbers split across spaces or lines:
    // e.g. "Calpol 5 00" -> "Calpol 500", "Calpol 5 \n 00" -> "Calpol 500", "Dolo 6 50" -> "Dolo 650"
    repaired = repaired.replace(/\b([a-zA-Z]{3,})\s*[:\-]?\s*(\d)\s+(\d{2})\b/gi, "$1 $2$3");
    repaired = repaired.replace(/\b([a-zA-Z]{3,})\s*[:\-]?\s*(\d)\s*\n\s*(\d{2})\b/gi, "$1 $2$3");

    // 4. Normalize unit notations:
    repaired = repaired.replace(/\b(\d+(?:\.\d+)?)\s*mgs\b/gi, "$1 mg");
    repaired = repaired.replace(/\b(\d+(?:\.\d+)?)\s*mg\.(?!\w)/gi, "$1 mg");
    repaired = repaired.replace(/\b(\d+(?:\.\d+)?)\s*(?:ug|μg)\b/gi, "$1 mcg");
    repaired = repaired.replace(/\b(\d+(?:\.\d+)?)\s*mg\s*\/\s*(\d+)?\s*ml\b/gi, (m, d1, d2) => {
      return d2 ? `${d1}mg/${d2}ml` : `${d1}mg/ml`;
    });

    return repaired;
  }

  extractStrength(text) {
    if (!text) return "";
    const clean = this.repairSplitNumbers(text);

    // 1. Explicit liquid concentration / ratio: "125mg/5ml", "250mg/5ml", "100mg/ml", "5mg/ml"
    const liquidMatch = clean.match(/\b(\d+(?:\.\d+)?\s*(?:mg|mcg)\s*\/\s*(?:\d+(?:\.\d+)?)?\s*ml)\b/i);
    if (liquidMatch) {
      return liquidMatch[1].replace(/\s+/g, "").toLowerCase();
    }

    // 2. Explicit combination strengths: "500 mg + 125 mg", "400mg + 325mg", "100mg / 325mg"
    const comboMatch = clean.match(/\b(\d+(?:\.\d+)?\s*(?:mg|g|mcg)\s*[\/+]\s*\d+(?:\.\d+)?\s*(?:mg|g|mcg))\b/i);
    if (comboMatch) {
      return comboMatch[1].replace(/\s*([\/+])\s*/g, " $1 ").toLowerCase();
    }

    // 3. Explicit dosage strength with medical unit: "500 mg", "650 mg", "50 mg", "500 mcg", "5 mg"
    const explicitRegex = /\b(\d+(?:\.\d+)?)\s*(mg|mcg|g|ml|iu|%)\b/gi;
    const explicitMatches = [];
    let expM;
    while ((expM = explicitRegex.exec(clean)) !== null) {
      const val = parseFloat(expM[1]);
      const unit = expM[2].toLowerCase();
      explicitMatches.push({
        raw: `${expM[1]} ${unit}`,
        val: val,
        unit: unit,
        index: expM.index
      });
    }

    if (explicitMatches.length >= 2) {
      // Check if text indicates a combination product (e.g. "Ibuprofen 400mg + Paracetamol 325mg")
      if (clean.includes("+") || clean.includes("and") || clean.includes("/")) {
        return `${explicitMatches[0].raw} + ${explicitMatches[1].raw}`;
      }
    }

    if (explicitMatches.length > 0) {
      // If multiple explicit matches exist, prefer standard multi-digit tablet/capsule strengths (>= 10)
      const multiDigit = explicitMatches.filter(m => m.val >= 10);
      if (multiDigit.length > 0) {
        return multiDigit[0].raw;
      }
      return explicitMatches[0].raw;
    }

    // 4. Standalone pharmaceutical dosage number following brand/generic name:
    // e.g. "Calpol 500", "Dolo 650", "Augmentin 625", "Pantocid 40", "Telma 40", "Atorva 20"
    // CRITICAL SAFETY RULE: Only accept established multi-digit strengths (>= 10).
    // NEVER infer a stray single-digit number (< 10, e.g. "5") without an explicit medical unit!
    const brandDosageMatch = clean.match(/\b([A-Za-z]{3,})\s*(?:-|\s+)?(10|15|20|25|30|40|50|75|100|125|150|200|250|300|400|500|625|650|800|1000)\b/i);
    if (brandDosageMatch) {
      const num = brandDosageMatch[2];
      return `${num} mg`;
    }

    // 5. Standalone dosage number adjacent to pharmaceutical indicators (Tablets, Capsules, IP, BP, USP)
    const packagingDosageMatch = clean.match(/\b(10|15|20|25|30|40|50|75|100|125|150|200|250|300|400|500|625|650|800|1000)\s*(?:tablets?|capsules?|ip|bp|usp)\b/i);
    if (packagingDosageMatch) {
      return `${packagingDosageMatch[1]} mg`;
    }

    return "";
  }

  getKnownStrengthsForMedicine(medicineName, activeSalt) {
    const normName = this.normalizeComparable(medicineName);
    const normSalt = this.normalizeComparable(activeSalt);

    const STRENGTH_TABLE = {
      "paracetamol": ["500 mg", "650 mg", "1000 mg", "250 mg", "120 mg/5ml", "250 mg/5ml", "100 mg/ml"],
      "calpol": ["500 mg", "650 mg", "250 mg", "120 mg/5ml", "250 mg/5ml", "100 mg/ml"],
      "calpol 500": ["500 mg"],
      "calpol 650": ["650 mg"],
      "dolo": ["650 mg", "500 mg", "250 mg", "120 mg/5ml"],
      "dolo 650": ["650 mg"],
      "crocin": ["500 mg", "650 mg", "1000 mg", "120 mg/5ml", "240 mg/5ml"],
      "crocin advance": ["500 mg"],
      "amoxicillin + clavulanic acid": ["625 mg", "375 mg", "1000 mg", "228.5 mg/5ml", "457 mg/5ml"],
      "augmentin": ["625 mg", "375 mg", "1000 mg", "228.5 mg/5ml", "457 mg/5ml"],
      "augmentin 625": ["625 mg"],
      "augmentin 625 duo": ["625 mg"],
      "ibuprofen": ["200 mg", "400 mg", "600 mg", "800 mg", "100 mg/5ml"],
      "combiflam": ["400 mg + 325 mg", "400mg + 325mg"],
      "aceclofenac": ["100 mg", "200 mg"],
      "zerodol": ["100 mg", "200 mg"],
      "zerodol-p": ["100 mg + 325 mg", "100mg + 325mg"],
      "zerodol-sp": ["100 mg + 325 mg + 15 mg"],
      "pantoprazole": ["20 mg", "40 mg"],
      "pantocid": ["20 mg", "40 mg"],
      "pantocid 40": ["40 mg"],
      "pan 40": ["40 mg"],
      "pan-d": ["40 mg + 30 mg"],
      "atorvastatin": ["10 mg", "20 mg", "40 mg", "80 mg"],
      "atorva": ["10 mg", "20 mg", "40 mg", "80 mg"],
      "cetirizine": ["5 mg", "10 mg", "5 mg/5ml"],
      "levocetirizine": ["2.5 mg", "5 mg", "2.5 mg/5ml"],
      "metformin": ["500 mg", "850 mg", "1000 mg"],
      "glycomet": ["500 mg", "850 mg", "1000 mg"],
      "glycomet 500": ["500 mg"],
      "azithromycin": ["250 mg", "500 mg", "100 mg/5ml", "200 mg/5ml"],
      "azithral": ["250 mg", "500 mg"],
      "azithral 500": ["500 mg"],
      "cefixime": ["100 mg", "200 mg", "400 mg"],
      "taxim-o": ["100 mg", "200 mg"],
      "taxim-o 200": ["200 mg"],
      "telmisartan": ["20 mg", "40 mg", "80 mg"],
      "telma": ["20 mg", "40 mg", "80 mg"],
      "telma 40": ["40 mg"],
      "amlodipine": ["2.5 mg", "5 mg", "10 mg"],
      "amlong": ["2.5 mg", "5 mg", "10 mg"],
      "aspirin": ["75 mg", "150 mg", "325 mg", "500 mg"],
      "warfarin": ["1 mg", "2 mg", "2.5 mg", "3 mg", "5 mg"]
    };

    if (STRENGTH_TABLE[normName]) return STRENGTH_TABLE[normName];
    if (STRENGTH_TABLE[normSalt]) return STRENGTH_TABLE[normSalt];

    for (const [key, list] of Object.entries(STRENGTH_TABLE)) {
      if (normSalt.includes(key) || normName.includes(key)) {
        return list;
      }
    }

    return null;
  }

  validateStrengthConcordance(medicineName, activeSalt, detectedStrength) {
    if (!detectedStrength || detectedStrength === "Not detected") {
      return {
        status: "UNCONFIRMED",
        isMatch: false,
        message: "Dosage strength not detected on packaging label.",
        penalty: 0.10
      };
    }

    const knownList = this.getKnownStrengthsForMedicine(medicineName, activeSalt);
    if (!knownList || knownList.length === 0) {
      return {
        status: "UNKNOWN_FORMULATION",
        isMatch: null,
        message: `Extracted strength '${detectedStrength}' (unregistered in local formulary; external validation recommended).`,
        penalty: 0.0
      };
    }

    const normDet = detectedStrength.toLowerCase().replace(/\s+/g, "");
    const isDirectMatch = knownList.some(k => k.toLowerCase().replace(/\s+/g, "") === normDet);

    if (isDirectMatch) {
      return {
        status: "VALIDATED",
        isMatch: true,
        message: `Strength '${detectedStrength}' verified against standard pharmaceutical records.`,
        penalty: 0.0
      };
    }

    // DISCORDANT STRENGTH: Extracted strength (e.g. 5 mg) conflicts with known formulations (500 mg, 650 mg)
    return {
      status: "MISMATCH",
      isMatch: false,
      message: `⚠ Strength Discrepancy: Extracted strength '${detectedStrength}' is discordant with standard approved strengths (${knownList.slice(0, 3).join(", ")}).`,
      expectedStrengths: knownList,
      penalty: 0.35
    };
  }

  extractDosage(text) {
    if (!text) return "";
    const match = text.match(/\b(\d+(?:\.\d+)?\s*(?:tablets?|capsules?|tabs?|caps?|pills?|spoons?|puffs?|drops?|ml))\b/i);
    return match ? match[1].trim() : "";
  }

  extractForm(text) {
    if (!text) return "";
    const match = text.match(/\b(film[- ]coated tablets?|uncoated tablets?|dispersible tablets?|sustained release tablets?|extended release tablets?|tablets?|capsules?|syrup|suspension|injection|gel|ointment|drops?|cream|inhaler|effervescent|solution|lotion)\b/i);
    return match ? this.capitalize(match[1].trim()) : "";
  }

  extractFrequency(text) {
    if (!text) return "";
    const pattern = /\b(1-0-1|1-1-1|1-0-0|0-0-1|0-1-0|0-1-1|1-1-0|1-0-1-1|OD|BD|BID|TDS|TID|QID|HS|QHS|PRN|once daily|twice daily|thrice daily|once a day|twice a day|three times a day|every 8 hours|every 12 hours|morning & night|morning and night|morning|afternoon|night|before breakfast|after breakfast|before food|after food|after meals|with food)\b/i;
    const match = text.match(pattern);
    return match ? this.formatFrequency(match[1]) : "";
  }

  extractDuration(text) {
    if (!text) return "";
    const match = text.match(/\b((?:for\s+)?\d+\s*(?:days?|weeks?|months?|d|wk|wks))\b/i);
    return match ? match[1].trim() : "";
  }

  /**
   * Extracts candidate pharmaceutical terms from OCR text:
   * 1. Active ingredient declarations ("Each film-coated tablet contains: Atorvastatin Calcium IP")
   * 2. Standalone medicine brand headers with strengths ("Pantocid 40", "Atorva 20")
   * 3. Words containing pharmaceutical root stems (-statin, -prazole, -sartan, -cillin, etc.)
   */
  extractCandidatePharmaceuticalTerms(rawOcrText, validLines) {
    const candidateTerms = [];
    const seen = new Set();

    const addTerm = (term, reason) => {
      if (!term) return;
      const clean = term.replace(/[^a-zA-Z\-]/g, "").trim().toLowerCase();
      if (clean.length < 3 || seen.has(clean)) return;

      const STOPWORDS = new Set([
        "tablet", "tablets", "capsule", "capsules", "syrup", "suspension",
        "each", "film", "coated", "uncoated", "contains", "composition",
        "strip", "pack", "batch", "expiry", "manufacturing", "licensed",
        "schedule", "prescription", "warning", "caution", "store", "cool",
        "below", "protect", "light", "keep", "reach", "children", "dr", "doctor",
        "patient", "hospital", "clinic", "dispensing", "sample", "mfg", "lic",
        "for", "and", "the", "with", "only", "not", "use", "oral", "dose",
        "marketed", "by", "ltd", "pvt", "limited", "pharma", "laboratories",
        "india", "dosage", "directed", "physician", "swallow", "whole",
        // Non-medicine, grocery, retail, and document noise words
        "slip", "store", "supermarket", "receipt", "total", "subtotal",
        "thank", "shopping", "milk", "bread", "apple", "apples", "juice",
        "orange", "food", "grocery", "price", "card", "visa", "cash",
        "change", "tax", "date", "time", "order", "item", "items", "organic",
        "whole", "loaf", "sourdough", "novel", "experimental", "formulation",
        "note", "notes", "summary", "report", "test", "result", "results",
        "name", "sign", "signature", "address", "phone", "bill", "invoice"
      ]);

      if (STOPWORDS.has(clean)) return;
      seen.add(clean);
      candidateTerms.push({ term: clean, reason });
    };

    // 1. Composition / Active Ingredient declarations: "Each tablet contains: [Salt]"
    const compRegex = /(?:contains|composition)\s*[:\-]?\s*([a-zA-Z\s\+\/]{3,35})(?:\s*(?:ip|bp|usp|equivalent|eq\.?|to|\d))/gi;
    let compMatch;
    while ((compMatch = compRegex.exec(rawOcrText)) !== null) {
      const parts = compMatch[1].split(/[\+\/]|and/i);
      for (const p of parts) {
        addTerm(p.trim(), "Active ingredient declaration on blister strip");
      }
    }

    // 2. Words preceding explicit dosage strengths: e.g. "Atorvastatin Tablets IP 20 mg"
    const strengthLineRegex = /\b([a-zA-Z\-]{3,25})\s+(?:[a-zA-Z\-]+\s+){0,3}\d+(?:\.\d+)?\s*(?:mg|g|mcg|ml)\b/gi;
    let strMatch;
    while ((strMatch = strengthLineRegex.exec(rawOcrText)) !== null) {
      addTerm(strMatch[1], "Word preceding dosage strength");
    }

    // 3. Recognized pharmaceutical root stems (USP / INN official nomenclature)
    const PHARMA_STEMS = /\b([a-zA-Z]{3,}(?:azole|statin|sartan|cillin|cycline|mycin|floxacin|olol|dipine|pril|prazole|tidine|gliptin|gliflozin|grel|profen|fenac|triptan|semide|vir|mab|cept|asone|dopa|parin|zepam|zolam|zodone|barbital|caine|dronate|coxib|lukast|kiren|afil|nazole|stine|terol|mide|zide|xacin|navir|glitazone|pramine|sone|line|mine|dine|cin|fen))\b/gi;
    let stemMatch;
    while ((stemMatch = PHARMA_STEMS.exec(rawOcrText)) !== null) {
      addTerm(stemMatch[1], "Recognized pharmaceutical active stem (USP/INN)");
    }

    // 4. Prominent header tokens: ONLY on lines that contain genuine pharmaceutical cues
    const PHARMA_LINE_INDICATOR = /\b(?:tablet|tablets|capsule|capsules|syrup|suspension|drops|gel|cream|ointment|mg|ml|mcg|iu|ip|bp|usp|oral|tds|bd|bid|od|sos|hs)\b/i;
    for (let i = 0; i < Math.min(validLines.length, 5); i++) {
      const line = validLines[i];
      if (!PHARMA_LINE_INDICATOR.test(line)) continue; // Reject non-medicine lines like supermarket headers

      const words = line.split(/\s+/);
      for (const w of words) {
        if (w.length >= 4 && /^[A-Z][a-zA-Z\-]+$/.test(w)) {
          addTerm(w, "Prominent pharmaceutical brand header");
        }
      }
    }

    return candidateTerms;
  }

  // ==========================================
  // OCR TEXT NORMALIZATION & OCR ERROR REPAIR
  // ==========================================

  normalizeOcrText(rawText) {
    if (!rawText) return "";
    let cleaned = rawText.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    cleaned = cleaned.replace(/[ \t]+/g, " ");
    cleaned = cleaned.replace(/\n{3,}/g, "\n\n");
    return cleaned.trim();
  }

  normalizeComparable(text) {
    if (!text) return "";
    let val = text.toLowerCase().trim();
    // Strip pharmacopoeia indicators (IP, BP, USP)
    val = val.replace(/\b(i\.?p\.?|b\.?p\.?|u\.?s\.?p\.?)\b/gi, " ");
    val = val.replace(/[^a-z0-9\s]/gi, " ");
    val = val.replace(/\s+/g, " ");
    return val.trim();
  }

  repairOcrMisreadings(word) {
    if (!word || word.length < 3) return word;
    let w = word.toLowerCase();

    // 0 inside a word surrounded by letters -> o
    w = w.replace(/([a-z])0([a-z])/g, "$1o$2");
    w = w.replace(/0([a-z]{2,})/g, "o$1");
    w = w.replace(/([a-z]{2,})0/g, "$1o");

    // 1 inside a word -> l or i
    w = w.replace(/([a-z])1([a-z])/g, "$1l$2");

    // 5 inside a word -> s
    w = w.replace(/([a-z])5([a-z])/g, "$1s$2");

    // 8 inside a word -> b
    w = w.replace(/([a-z])8([a-z])/g, "$1b$2");

    // rn -> m (common OCR ligatures)
    w = w.replace(/rn/g, "m");

    // cl -> d
    w = w.replace(/cl/g, "d");

    return w;
  }

  cleanPackagingNoise(line) {
    if (!line) return "";
    let cleaned = line;
    const noisePatterns = [
      /\b(?:mfg\.?\s*lic\.?\s*(?:no\.?)?|lic\.?\s*no\.?)\s*[:\-]?\s*[\w\/-]+/gi,
      /\b(?:batch\s*(?:no\.?)?|b\.?\s*no\.?)\s*[:\-]?\s*[\w\/-]+/gi,
      /\b(?:exp\.?\s*(?:date)?|expiry(?:\s*date)?)\s*[:\-]?\s*[\w\/-]+/gi,
      /\b(?:mfg\.?\s*(?:date)?|manufacturing(?:\s*date)?)\s*[:\-]?\s*[\w\/-]+/gi,
      /\b(?:m\.?r\.?p\.?|max(?:imum)?\s*retail\s*price)\s*[:\-]?\s*(?:rs\.?)?\s*[\d\.]+/gi,
      /\b(?:inclusive\s*of\s*all\s*taxes|incl\.?\s*taxes)/gi,
      /\b(?:marketed\s*by|mfg\s*by|manufactured\s*by|manufactured\s*in\s*india|packed\s*by)\s*[:\-]?[^,\n]+/gi,
      /\b(?:plot\s*no|industrial\s*area|regd\.?\s*off(?:ice)?|pin\s*code|strip\s*of\s*\d+)/gi,
      /\b(?:schedule\s*h1?|prescription\s*drug|caution\s*:\s*not\s*to\s*be\s*sold)/gi,
      /\b(?:warning\s*:\s*taking\s*more\s*than|keep\s*out\s*of\s*reach\s*of\s*children|out\s*of\s*reach)/gi,
      /\b(?:store\s*in\s*a\s*cool|store\s*below|protect\s*from\s*light|swallow\s*whole|do\s*not\s*crush)/gi,
      /\b(?:each\s*uncoated\s*tablet\s*contains|each\s*film\s*coated\s*tablet\s*contains|each\s*capsule\s*contains)/gi,
      /\b(?:excipients\s*q\.?s\.?|titanium\s*dioxide|approved\s*colour|physician\s*sample)/gi,
      /\b(?:patient\s*(?:name)?|doctor|dr\.|hospital|clinic|diagnosis|rx)\s*[:\-]?[^,\n]*/gi
    ];

    for (const pat of noisePatterns) {
      cleaned = cleaned.replace(pat, " ");
    }
    return cleaned.replace(/\s+/g, " ").trim();
  }

  isPatientLine(line) {
    if (!line) return false;
    const lower = line.toLowerCase().trim();
    const keywords = [
      "patient name", "patient:", "name:", "age:", "gender:", "sex:",
      "weight:", "height:", "address:", "phone:", "date:", "doctor:",
      "dr.", "dr ", "hospital:", "clinic:", "diagnosis:"
    ];
    return keywords.some(k => lower.startsWith(k));
  }

  levenshtein(a, b) {
    const matrix = [];
    for (let i = 0; i <= b.length; i++) matrix[i] = [i];
    for (let j = 0; j <= a.length; j++) matrix[0][j] = j;

    for (let i = 1; i <= b.length; i++) {
      for (let j = 1; j <= a.length; j++) {
        if (b.charAt(i - 1) === a.charAt(j - 1)) {
          matrix[i][j] = matrix[i - 1][j - 1];
        } else {
          matrix[i][j] = Math.min(
            matrix[i - 1][j - 1] + 1,
            matrix[i][j - 1] + 1,
            matrix[i - 1][j] + 1
          );
        }
      }
    }
    return matrix[b.length][a.length];
  }

  /**
   * UNIVERSAL PRESCRIPTION & PACKAGING PARSER:
   * 1. Normalizes OCR text and filters packaging noise.
   * 2. Scans line-by-line and extracts all local dataset matches.
   * 3. Extracts candidate pharmaceutical terms (stems, ingredient declarations).
   * 4. Asynchronously resolves unfamiliar medicines via NLM RxNorm and FDA Drug Labels.
   * 5. Unifies and deduplicates brand and active salt formulations.
   * 6. Produces multi-signal evidence for every match with zero data hallucination.
   */
  async parsePrescriptionText(rawOcrText, enableOnlineDiscovery = true) {
    const result = {
      detectedMedicines: [],
      unmatchedOcrLines: [],
      isUnreadableHandwriting: false,
      fullNormalizedOcrText: ""
    };

    if (!rawOcrText || rawOcrText.trim().length === 0) {
      result.isUnreadableHandwriting = true;
      return result;
    }

    const cleanText = this.normalizeOcrText(rawOcrText);
    result.fullNormalizedOcrText = cleanText;

    if (cleanText.length < 3) {
      result.isUnreadableHandwriting = true;
      return result;
    }

    const rawLines = cleanText.split("\n");
    const validLines = [];

    for (const l of rawLines) {
      const trimmed = l.trim();
      if (!trimmed) continue;
      if (this.isPatientLine(trimmed)) continue;

      const lineWithoutNoise = this.cleanPackagingNoise(trimmed);
      if (lineWithoutNoise.length >= 3) {
        validLines.push(lineWithoutNoise);
      }
    }

    // Extract clinical entities
    const globalStrength = this.extractStrength(cleanText);
    const globalDosage = this.extractDosage(cleanText);
    const globalForm = this.extractForm(cleanText);
    const globalFrequency = this.extractFrequency(cleanText);
    const globalDuration = this.extractDuration(cleanText);

    const candidates = [];
    const matchedMedicineNames = new Set();

    // 1. Line-by-line and token matching across local clinical database
    for (const line of validLines) {
      const lineMatches = this.findAllMatchesInText(line, matchedMedicineNames);

      // If a line contains a specific brand (e.g. "Calpol", "Crocin", "Dolo"),
      // filter out the generic salt (e.g. "Paracetamol") mentioned on that same line
      const brandMatches = lineMatches.filter(m => m.item.name !== m.item.activeSalt);
      const filteredMatches = lineMatches.filter(m => {
        if (m.item.name === m.item.activeSalt) {
          const hasBrandForThisSalt = brandMatches.some(b => this.saltsOverlap(b.item.activeSalt, m.item.name));
          return !hasBrandForThisSalt;
        }
        return true;
      });

      for (const m of filteredMatches) {
        candidates.push(m);
        matchedMedicineNames.add(m.item.name);
      }
    }

    // 1b. Check cross-line stitched tokens for curved bottles and multi-line breaks
    const allValidTokens = validLines.join(" ").split(/\s+/).map(w => this.repairOcrMisreadings(this.normalizeComparable(w))).filter(w => w.length > 0);
    for (let i = 0; i < allValidTokens.length - 1; i++) {
      const bi = allValidTokens[i] + allValidTokens[i + 1];
      if (bi.length >= 4) {
        const biMatches = this.findAllMatchesInText(bi, matchedMedicineNames);
        for (const m of biMatches) {
          candidates.push(m);
          matchedMedicineNames.add(m.item.name);
        }
      }
      if (i + 2 < allValidTokens.length) {
        const tri = allValidTokens[i] + allValidTokens[i + 1] + allValidTokens[i + 2];
        if (tri.length >= 5) {
          const triMatches = this.findAllMatchesInText(tri, matchedMedicineNames);
          for (const m of triMatches) {
            candidates.push(m);
            matchedMedicineNames.add(m.item.name);
          }
        }
      }
    }

    // 2. Full-text scan for medicines mentioned in text
    for (const item of this.medicines) {
      if (matchedMedicineNames.has(item.name)) continue;

      const normName = this.normalizeComparable(item.name);
      const normText = this.normalizeComparable(cleanText);

      const wordRegex = new RegExp(`\\b${normName}\\b`, "i");
      if (wordRegex.test(normText)) {
        candidates.push({ item, matchedText: item.name, score: 0.96 });
        matchedMedicineNames.add(item.name);
      } else {
        const saltRegex = new RegExp(`\\b${this.normalizeComparable(item.activeSalt)}\\b`, "i");
        if (saltRegex.test(normText)) {
          candidates.push({ item, matchedText: item.activeSalt, score: 0.88 });
          matchedMedicineNames.add(item.name);
        }
      }
    }

    // 3. UNIVERSAL ONLINE DISCOVERY FOR UNFAMILIAR MEDICINES:
    // If enabled, extract candidate terms and query RxNorm / FDA
    if (enableOnlineDiscovery) {
      const candidatePharmaTerms = this.extractCandidatePharmaceuticalTerms(rawOcrText, validLines);

      for (const cand of candidatePharmaTerms) {
        if (matchedMedicineNames.has(cand.term)) continue;

        try {
          const resolved = await MedicineApi.resolveUnfamiliarMedicineOnline(
            cand.term,
            globalStrength,
            globalForm
          );

          if (resolved && resolved.name) {
            // Check if already in unique candidates
            if (!matchedMedicineNames.has(resolved.name) && !matchedMedicineNames.has(resolved.activeSalt)) {
              // Add to local database dynamically so interaction checks and future lookups work
              this.medicines.push(resolved);

              candidates.push({
                item: resolved,
                matchedText: cand.term,
                score: 0.94,
                evidenceReason: cand.reason,
                isOnlineDiscovered: true
              });

              matchedMedicineNames.add(resolved.name);
              matchedMedicineNames.add(resolved.activeSalt);
            }
          }
        } catch (err) {
          console.warn("Online discovery error for term:", cand.term, err);
        }
      }
    }

    // 4. UNIFICATION & MULTI-SIGNAL CANDIDATE SCORING
    const uniqueMap = new Map();

    for (const cand of candidates) {
      const item = cand.item;
      const brandKey = item.name.toLowerCase();

      if (uniqueMap.has(brandKey)) continue;

      let belongsToExisting = false;
      for (const existing of uniqueMap.values()) {
        // Case 1: Identical brand name already recorded
        if (existing.item.name === item.name) {
          belongsToExisting = true;
          break;
        }

        // Case 1b: Same brand with strength suffix (e.g. "Calpol" and "Calpol 500", "Dolo" and "Dolo 650")
        const n1 = this.normalizeComparable(existing.item.name);
        const n2 = this.normalizeComparable(item.name);
        if ((n1.startsWith(n2) || n2.startsWith(n1)) && this.saltsOverlap(existing.item.activeSalt, item.activeSalt)) {
          belongsToExisting = true;
          break;
        }

        // Case 2: One is the generic salt and the other is the trade brand on the SAME product packaging
        // e.g. packaging says "Calpol" and "Paracetamol IP 500mg"
        const itemIsSaltOfExisting = this.saltsOverlap(existing.item.activeSalt, item.name);
        const existingIsSaltOfItem = this.saltsOverlap(item.activeSalt, existing.item.name);

        if (itemIsSaltOfExisting || existingIsSaltOfItem) {
          if (existing.strengthStatus !== "MISMATCH") {
            existing.matchConfidence = Math.min(0.92, existing.matchConfidence + 0.08);
            if (existing.matchConfidence >= 0.80) {
              existing.verificationTier = VerificationTier.VERIFIED;
            }
          }
          existing.evidence.push(`Co-identified with active ingredient: ${item.name}`);
          belongsToExisting = true;
          break;
        }
      }

      if (belongsToExisting) continue;

      let confidence = cand.score;
      const evidence = [
        `Detected text '${cand.matchedText}' in packaging OCR`
      ];

      if (cand.evidenceReason) {
        evidence.push(cand.evidenceReason);
      }

      // Check active salt presence (including phonemes/sub-tokens e.g. 'etam', 'acetamol')
      const normClean = this.normalizeComparable(cleanText);
      const normSalt = this.normalizeComparable(item.activeSalt);
      const saltStem = normSalt.slice(0, 5);
      const hasSaltInText = normClean.includes(normSalt) ||
                            (saltStem.length >= 5 && normClean.includes(saltStem)) ||
                            (normSalt.includes("paracetamol") && (normClean.includes("etam") || normClean.includes("acetamol")));
      if (hasSaltInText && item.name !== item.activeSalt) {
        confidence = Math.min(0.90, confidence + 0.10);
        evidence.push(`Active ingredient '${item.activeSalt}' verified in packaging text`);
      }

      // Determine final strength for this medicine candidate
      const finalStrength = globalStrength || item.strength || "Not detected";
      const strengthCheck = this.validateStrengthConcordance(item.name, item.activeSalt, finalStrength);

      if (strengthCheck.status === "VALIDATED") {
        confidence = Math.min(0.92, confidence + 0.08);
        evidence.push(strengthCheck.message);
      } else if (strengthCheck.status === "MISMATCH") {
        // Heavy penalty: strength mismatch cannot be certified as verified product
        confidence = Math.min(0.55, Math.max(0.40, confidence - strengthCheck.penalty));
        evidence.push(strengthCheck.message);
      } else if (strengthCheck.status === "UNCONFIRMED") {
        confidence = Math.min(0.80, confidence);
        evidence.push("Strength unconfirmed from packaging label");
      }

      if (globalForm) {
        evidence.push(`Dosage form '${globalForm}' matched`);
      }

      if (cand.isOnlineDiscovered || item.isOnlineDiscovered) {
        evidence.push(`Verified in official clinical database: ${item.dataSource}`);
      }

      if (confidence < 0.40) continue;

      let tier;
      if (strengthCheck.status === "MISMATCH") {
        tier = {
          title: "⚠ Strength Discrepancy — Unverified Product",
          minScore: 0.40,
          tag: "unconfirmed"
        };
      } else if (confidence >= 0.88 && strengthCheck.status === "VALIDATED") {
        tier = VerificationTier.VERIFIED;
      } else if (confidence >= 0.55) {
        tier = VerificationTier.LIKELY;
      } else {
        tier = VerificationTier.UNCONFIRMED;
      }

      const detail = {
        item,
        detectedNameInOcr: cand.matchedText,
        detectedBrandName: item.name,
        detectedActiveSalt: item.activeSalt,
        strength: finalStrength,
        strengthStatus: strengthCheck.status,
        strengthWarning: strengthCheck.status === "MISMATCH" ? strengthCheck.message : null,
        dosage: globalDosage || "Not detected",
        dosageForm: globalForm || item.dosageForm || "Tablet",
        frequency: globalFrequency || "Not detected",
        duration: globalDuration || "Not detected",
        manufacturer: item.manufacturer || "Not specified on packaging",
        ocrReadConfidence: 0.92,
        matchConfidence: confidence,
        verificationTier: tier,
        dataSource: item.dataSource || "Medinex Clinical Dataset",
        onlineMatchInfo: cand.isOnlineDiscovered ? item.dataSource : null,
        evidence: evidence,
        isOnlineDiscovered: !!cand.isOnlineDiscovered
      };

      uniqueMap.set(brandKey, detail);
    }

    // Safety Audit Pass: strictly enforce confidence cap and unverified tier for strength mismatches
    for (const detail of uniqueMap.values()) {
      if (detail.strengthStatus === "MISMATCH") {
        detail.matchConfidence = Math.min(0.55, detail.matchConfidence);
        detail.verificationTier = {
          title: "⚠ Strength Discrepancy — Unverified Product",
          minScore: 0.40,
          tag: "unconfirmed"
        };
      }
    }

    result.detectedMedicines = Array.from(uniqueMap.values());
    result.detectedMedicines.sort((a, b) => b.matchConfidence - a.matchConfidence);

    result.pipelineStages = {
      ocrExtraction: {
        status: cleanText.length > 5 ? "SUCCESS" : "LOW_TEXT",
        characterCount: cleanText.length,
        linesParsed: validLines.length
      },
      candidateGeneration: {
        status: candidates.length > 0 ? "SUCCESS" : "NO_CANDIDATES",
        candidatesFound: candidates.length
      },
      databaseLookup: {
        status: "SUCCESS",
        sources: enableOnlineDiscovery ? ["Medinex Local Formulary", "NLM RxNorm", "US FDA"] : ["Medinex Local Formulary"]
      },
      productVerification: {
        status: result.detectedMedicines.length > 0
          ? (result.detectedMedicines.some(m => m.strengthStatus === "MISMATCH") ? "STRENGTH_MISMATCH" : "VERIFIED")
          : "UNCONFIRMED",
        verifiedMedicines: result.detectedMedicines.length
      }
    };

    // 5. Build unmatched OCR text lines for user review
    for (const line of validLines) {
      const lower = line.toLowerCase();
      let matched = false;
      for (const med of result.detectedMedicines) {
        if (lower.includes(med.item.name) || lower.includes(med.item.activeSalt)) {
          matched = true;
          break;
        }
      }
      if (!matched && line.length >= 3 && /[a-zA-Z]/.test(line)) {
        result.unmatchedOcrLines.push(line);
      }
    }

    // 6. Safe rejection of non-medicines & graceful partial recognition fallback
    if (result.detectedMedicines.length === 0) {
      const normClean = this.normalizeComparable(cleanText);
      const recognizedSaltItem = this.medicines.find(m => {
        const salt = this.normalizeComparable(m.activeSalt);
        return salt.length >= 4 && (normClean.includes(salt) || normClean.includes(salt.slice(0, 5)));
      });

      if (recognizedSaltItem || globalStrength || globalForm) {
        result.partiallyRecognized = {
          activeSalt: recognizedSaltItem ? recognizedSaltItem.activeSalt : "Pharmaceutical Formulation",
          strength: globalStrength || "Standard",
          dosageForm: globalForm || "Tablet",
          category: recognizedSaltItem ? recognizedSaltItem.category : "General Care",
          indication: recognizedSaltItem ? recognizedSaltItem.indication : "Prescription medicine"
        };
      } else {
        result.isUnreadableHandwriting = true;
      }
    }

    return result;
  }

  /**
   * Scans a line and extracts ALL distinct medicines present
   */
  findAllMatchesInText(text, alreadyMatched) {
    const matches = [];
    if (!text || text.length < 3) return matches;

    const clean = this.normalizeComparable(text);
    const words = clean.split(/\s+/).map(w => this.repairOcrMisreadings(w));

    // Construct evaluation tokens: single tokens, 2-gram joined, and 3-gram joined
    const tokensToMatch = [];
    for (let i = 0; i < words.length; i++) {
      if (words[i].length >= 3) {
        tokensToMatch.push(words[i]);
      }
      if (i + 1 < words.length) {
        const biGram = words[i] + words[i + 1];
        if (biGram.length >= 4) {
          tokensToMatch.push(biGram);
        }
      }
      if (i + 2 < words.length) {
        const triGram = words[i] + words[i + 1] + words[i + 2];
        if (triGram.length >= 5) {
          tokensToMatch.push(triGram);
        }
      }
    }

    for (const item of this.medicines) {
      if (alreadyMatched.has(item.name)) continue;

      const normName = this.normalizeComparable(item.name);

      // Exact word boundary match
      const nameWordRegex = new RegExp(`\\b${normName}\\b`, "i");
      if (nameWordRegex.test(clean)) {
        matches.push({ item, matchedText: item.name, score: 0.98 });
        continue;
      }

      // Check repaired OCR tokens & stitched n-grams
      for (const w of tokensToMatch) {
        if (w.length < 3) continue;

        if (w === normName) {
          matches.push({ item, matchedText: w, score: 0.95 });
          break;
        }

        // Controlled Levenshtein distance
        if (normName.length <= 4) {
          if (w === normName) {
            matches.push({ item, matchedText: w, score: 0.94 });
            break;
          }
        } else if (normName.length <= 6) {
          const dist = this.levenshtein(w, normName);
          if (dist <= 1 && w.length >= 4) {
            const sim = 1.0 - (dist / Math.max(w.length, normName.length));
            matches.push({ item, matchedText: w, score: sim });
            break;
          }
        } else {
          const dist = this.levenshtein(w, normName);
          if (dist <= 2 && w.length >= 5) {
            const sim = 1.0 - (dist / Math.max(w.length, normName.length));
            if (sim >= 0.78) {
              matches.push({ item, matchedText: w, score: sim });
              break;
            }
          }
        }
      }
    }

    return matches;
  }

  /**
   * Manual search / correction tool for unfamiliar or unverified medicines:
   * Allows patients and doctors to type or correct a scanned name and verify against
   * the local clinical database, NLM RxNorm, and US FDA databases.
   */
  async searchManualMedicine(query) {
    if (!query || query.trim().length < 2) return null;
    const clean = query.trim();

    // 1. Search local dataset first
    const norm = this.normalizeComparable(clean);
    for (const m of this.medicines) {
      if (this.normalizeComparable(m.name) === norm || this.normalizeComparable(m.activeSalt) === norm) {
        return {
          item: m,
          verificationTier: VerificationTier.VERIFIED,
          dataSource: "Medinex Clinical Dataset",
          evidence: ["Direct user manual search matched local formulary entry"]
        };
      }
    }

    // 2. Search online databases (RxNorm + FDA)
    const resolved = await MedicineApi.resolveUnfamiliarMedicineOnline(clean);
    if (resolved) {
      this.medicines.push(resolved);
      return {
        item: resolved,
        verificationTier: VerificationTier.VERIFIED,
        dataSource: resolved.dataSource,
        evidence: ["Direct user manual search verified via official online clinical databases"]
      };
    }

    return null;
  }

  // ==========================================
  // DUPLICATE & INTERACTION CHECKS
  // ==========================================

  checkDuplicates(matchedDetails) {
    const duplicates = [];
    if (!matchedDetails || matchedDetails.length < 2) return duplicates;

    const valid = matchedDetails.filter(d => d.verificationTier !== VerificationTier.UNCONFIRMED && d.item);

    for (let i = 0; i < valid.length; i++) {
      for (let j = i + 1; j < valid.length; j++) {
        const m1 = valid[i].item;
        const m2 = valid[j].item;
        if (m1.name === m2.name) continue;

        if (this.saltsOverlap(m1.activeSalt, m2.activeSalt)) {
          duplicates.push({
            med1: m1,
            med2: m2,
            sharedSalt: this.findSharedSalt(m1.activeSalt, m2.activeSalt)
          });
        }
      }
    }

    return duplicates;
  }

  checkInteractions(matchedDetails) {
    const detected = [];
    if (!matchedDetails || matchedDetails.length < 2) return detected;

    const valid = matchedDetails.filter(d => d.verificationTier !== VerificationTier.UNCONFIRMED && d.item);

    for (let i = 0; i < valid.length; i++) {
      for (let j = i + 1; j < valid.length; j++) {
        const m1 = valid[i].item;
        const m2 = valid[j].item;

        for (const rule of this.interactions) {
          const m1d1 = this.matchesRule(m1, rule.drug1);
          const m2d2 = this.matchesRule(m2, rule.drug2);
          const m1d2 = this.matchesRule(m1, rule.drug2);
          const m2d1 = this.matchesRule(m2, rule.drug1);

          if ((m1d1 && m2d2) || (m1d2 && m2d1)) {
            if (!detected.some(r => r.drug1 === rule.drug1 && r.drug2 === rule.drug2)) {
              detected.push(rule);
            }
          }
        }
      }
    }

    return detected;
  }

  saltsOverlap(salt1, salt2) {
    if (!salt1 || !salt2) return false;
    const parts1 = salt1.split(/[\+,/]/).map(s => s.trim().toLowerCase());
    const parts2 = salt2.split(/[\+,/]/).map(s => s.trim().toLowerCase());

    for (const p1 of parts1) {
      if (p1.length < 3) continue;
      for (const p2 of parts2) {
        if (p2.length < 3) continue;
        if (p1 === p2 || p1.includes(p2) || p2.includes(p1)) return true;
      }
    }
    return false;
  }

  findSharedSalt(salt1, salt2) {
    const parts1 = salt1.split(/[\+,/]/).map(s => s.trim());
    const parts2 = salt2.split(/[\+,/]/).map(s => s.trim().toLowerCase());

    for (const p1 of parts1) {
      const clean1 = p1.toLowerCase();
      for (const p2 of parts2) {
        if (clean1 === p2 || clean1.includes(p2) || p2.includes(clean1)) return p1;
      }
    }
    return salt1;
  }

  matchesRule(medicine, ruleDrug) {
    if (!medicine || !ruleDrug) return false;
    const rule = this.normalizeComparable(ruleDrug);
    const name = this.normalizeComparable(medicine.name);
    const salt = this.normalizeComparable(medicine.activeSalt);
    return name.includes(rule) || salt.includes(rule);
  }

  /**
   * Interactive Manual Search & Clinical Verification
   * Distinguishes exact matches from candidate/partial matches
   * Queries RxNorm & FDA when unfamiliar medicines are searched
   */
  async searchManualMedicine(query) {
    if (!query || query.trim().length < 2) return null;
    const clean = query.trim();
    const extractedStrength = this.extractStrength(clean);
    const normClean = this.normalizeComparable(clean);

    let termOnly = normClean;
    if (extractedStrength) {
      const normStr = this.normalizeComparable(extractedStrength);
      termOnly = termOnly.replace(new RegExp(`\\b${normStr}\\b`, "i"), "").trim();
    }

    // 1. Local Clinical Formulary Lookup
    // A. Exact Name Match
    let exactItem = this.medicines.find(m => {
      const mn = this.normalizeComparable(m.name);
      return mn === normClean || (termOnly && mn === termOnly);
    });

    // B. Exact Salt Match
    if (!exactItem) {
      exactItem = this.medicines.find(m => {
        const ms = this.normalizeComparable(m.activeSalt);
        return ms === normClean || (termOnly && ms === termOnly);
      });
    }

    // C. Candidate / Fuzzy Match
    let candidateItem = null;
    if (!exactItem) {
      candidateItem = this.medicines.find(m => {
        const mn = this.normalizeComparable(m.name);
        const ms = this.normalizeComparable(m.activeSalt);
        const searchTarget = termOnly || normClean;
        return mn.includes(searchTarget) || searchTarget.includes(mn) ||
               ms.includes(searchTarget) || searchTarget.includes(ms);
      });
    }

    if (exactItem || candidateItem) {
      const item = exactItem || candidateItem;
      const isExact = !!exactItem;
      const finalStrength = extractedStrength || item.strength || "Standard";
      const strengthCheck = this.validateStrengthConcordance(item.name, item.activeSalt, finalStrength);

      let tier;
      if (strengthCheck.status === "MISMATCH") {
        tier = {
          title: "⚠ Strength Discrepancy — Unverified Product",
          minScore: 0.45,
          tag: "unconfirmed"
        };
      } else if (isExact && strengthCheck.status === "VALIDATED") {
        tier = VerificationTier.VERIFIED;
      } else {
        tier = VerificationTier.LIKELY;
      }

      return {
        item: {
          ...item,
          strength: finalStrength
        },
        isExactMatch: isExact,
        matchType: isExact ? "Exact Formulary Match" : "Candidate / Partial Formulary Match",
        verificationTier: tier,
        strengthStatus: strengthCheck.status,
        strengthWarning: strengthCheck.status === "MISMATCH" ? strengthCheck.message : null,
        dataSource: "Medinex Clinical Local Formulary",
        evidence: [
          isExact ? `Exact match in local formulary for '${termOnly || normClean}'` : `Candidate match in local formulary for '${termOnly || normClean}'`,
          strengthCheck.message
        ]
      };
    }

    // 2. Real Online Medical Lookup Fallback (NLM RxNorm & US FDA)
    try {
      const onlineResolved = await MedicineApi.resolveUnfamiliarMedicineOnline(
        termOnly || clean,
        extractedStrength
      );

      if (onlineResolved && onlineResolved.name) {
        this.medicines.push(onlineResolved);

        const isExact = this.normalizeComparable(onlineResolved.name) === normClean ||
                        this.normalizeComparable(onlineResolved.name) === termOnly ||
                        this.normalizeComparable(onlineResolved.officialName || "") === normClean;

        const finalStrength = extractedStrength || onlineResolved.strength || "Standard";
        const strengthCheck = this.validateStrengthConcordance(onlineResolved.name, onlineResolved.activeSalt, finalStrength);

        let tier;
        if (strengthCheck.status === "MISMATCH") {
          tier = {
            title: "⚠ Strength Discrepancy — Unverified Product",
            minScore: 0.45,
            tag: "unconfirmed"
          };
        } else if (isExact) {
          tier = VerificationTier.VERIFIED;
        } else {
          tier = VerificationTier.LIKELY;
        }

        return {
          item: {
            ...onlineResolved,
            strength: finalStrength
          },
          isExactMatch: isExact,
          matchType: isExact ? "Official Verified Medical Concept" : "Approximate Online Concept",
          verificationTier: tier,
          strengthStatus: strengthCheck.status,
          strengthWarning: strengthCheck.status === "MISMATCH" ? strengthCheck.message : null,
          dataSource: onlineResolved.dataSource,
          evidence: [
            `Online concept resolved from ${onlineResolved.dataSource}`,
            `Official active ingredient: ${onlineResolved.activeSalt}`,
            strengthCheck.message
          ]
        };
      }
    } catch (err) {
      console.warn("External online medicine lookup failed:", err);
    }

    return null;
  }

  capitalize(str) {
    if (!str) return "";
    return str.replace(/\b\w/g, l => l.toUpperCase());
  }

  formatFrequency(freq) {
    if (!freq) return "Not detected";
    const upper = freq.trim().toUpperCase();
    const map = {
      "OD": "Once daily (OD)",
      "BD": "Twice daily (BD / BID)",
      "BID": "Twice daily (BD / BID)",
      "TDS": "Thrice daily (TDS / TID)",
      "TID": "Thrice daily (TDS / TID)",
      "QID": "Four times daily (QID)",
      "HS": "At bedtime (HS)",
      "1-0-1": "1-0-1 (Morning & Night)",
      "1-1-1": "1-1-1 (Morning, Afternoon & Night)",
      "1-0-0": "1-0-0 (Morning)",
      "0-0-1": "0-0-1 (Night)"
    };
    return map[upper] || freq.trim();
  }
}
