import { MedinexDB } from "./db.js";

/**
 * Medinex External Medical APIs
 * Universal multi-source medicine resolution:
 * 1. NLM RxNorm Approximate Term API (50,000+ clinical concepts)
 * 2. NLM RxNorm Concept Properties & Related Ingredients API
 * 3. US FDA Drug Label & Product API (openFDA)
 * 4. Localized Caching Layer (7-day TTL via MedinexDB)
 */
export class MedicineApi {
  static RXNORM_BASE_URL = "https://rxnav.nlm.nih.gov/";
  static OPENFDA_BASE_URL = "https://api.fda.gov/";

  // Production backend HTTPS URL (deployed on Render)
  static PRODUCTION_BACKEND_URL = "https://medinex-backend-bukx.onrender.com";
  static LOCAL_BACKEND_URL = "http://127.0.0.1:8000";
  static TIMEOUT_MS = 25000;

  /**
   * Dynamic backend URL resolution:
   * 1. Manual developer override in localStorage ("medinex_backend_url")
   * 2. Localhost or 127.0.0.1 development server -> LOCAL_BACKEND_URL
   * 3. Hosted production deployment -> PRODUCTION_BACKEND_URL
   */
  static get SHARED_BACKEND_URL() {
    try {
      if (typeof localStorage !== "undefined") {
        const override = localStorage.getItem("medinex_backend_url");
        if (override && override.trim()) {
          return override.trim().replace(/\/+$/, "");
        }
      }
    } catch (e) {
      // Ignore security errors in sandboxed iframes
    }

    if (typeof window !== "undefined" && window.location) {
      const hostname = window.location.hostname;
      if (hostname === "localhost" || hostname === "127.0.0.1" || hostname === "") {
        return this.LOCAL_BACKEND_URL;
      }
    }

    return this.PRODUCTION_BACKEND_URL;
  }

  /**
   * Helper to set or clear manual backend URL override from DevTools or UI
   */
  static setSharedBackendUrl(url) {
    if (typeof localStorage !== "undefined") {
      if (url && url.trim()) {
        localStorage.setItem("medinex_backend_url", url.trim().replace(/\/+$/, ""));
      } else {
        localStorage.removeItem("medinex_backend_url");
      }
    }
  }

  /**
   * Universal Medicine Recognition using the Shared AI Backend
   * Accepts image blob or file and returns structured clinical JSON.
   */
  static async recognizePackagingImage(imageBlob) {
    const formData = new FormData();
    formData.append("file", imageBlob, "packaging.jpg");

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 45000);

    try {
      const response = await fetch(`${this.SHARED_BACKEND_URL}/api/medicine/recognize`, {
        method: "POST",
        body: formData,
        signal: controller.signal
      });
      clearTimeout(timeout);
      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }
      return await response.json();
    } catch (err) {
      clearTimeout(timeout);
      console.warn("Shared recognition API error or server unreachable:", err);
      throw err;
    }
  }

  /**
   * Approximate Term Search in NLM RxNorm
   */
  static async searchRxNorm(term, maxEntries = 5) {
    if (!term || term.trim().length < 2) return null;
    const cleanTerm = term.trim();

    // Check cache first
    const cacheKey = `rxnorm_${cleanTerm.toLowerCase()}`;
    const cached = MedinexDB.getMedicineCache(cacheKey);
    if (cached) return cached;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.TIMEOUT_MS);

    try {
      const url = `${this.RXNORM_BASE_URL}REST/approximateTerm.json?term=${encodeURIComponent(cleanTerm)}&maxEntries=${maxEntries}`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) return null;
      const data = await response.json();
      const parsed = this.parseRxNormResponse(data, cleanTerm);
      if (parsed) {
        MedinexDB.setMedicineCache(cacheKey, parsed);
      }
      return parsed;
    } catch (err) {
      clearTimeout(timeout);
      console.warn("RxNorm search API request failed or timed out:", err.message);
      return null;
    }
  }

  static isReasonableOnlineMatch(query, onlineName) {
    if (!query || !onlineName) return false;
    const q = query.toLowerCase().trim();
    const o = onlineName.toLowerCase().trim();

    // Reject known conflicting terms
    if (q === "dolo" && (o.startsWith("no dolo") || o.includes("nodolo"))) return false;

    // Exact equality
    if (o === q) return true;

    // Tokenize by word boundary
    const STOPWORDS = new Set(["non", "the", "and", "for", "with", "tablet", "tablets", "capsule", "capsules", "syrup", "oral", "mg", "ml", "usp", "ip", "bp", "dose", "pack", "hydrochloride", "sodium", "potassium"]);
    const qWords = q.split(/[^a-z0-9]+/).filter(w => w.length >= 3 && !STOPWORDS.has(w));
    const oWords = o.split(/[^a-z0-9]+/).filter(w => w.length >= 3 && !STOPWORDS.has(w));

    if (qWords.length === 0 || oWords.length === 0) return false;

    // Check token overlap
    for (const qw of qWords) {
      for (const ow of oWords) {
        if (qw === ow) return true;
        // Strong prefix match only for longer roots (>= 4 chars)
        if (qw.length >= 4 && ow.length >= 4) {
          if (ow.startsWith(qw) || qw.startsWith(ow)) return true;
        }
      }
    }

    return false;
  }

  static parseRxNormResponse(json, queryTerm) {
    if (!json || !json.approximateGroup || !json.approximateGroup.candidate) return null;
    const candidates = json.approximateGroup.candidate;
    if (!Array.isArray(candidates) || candidates.length === 0) return null;

    const q = (queryTerm || "").toLowerCase().trim();

    // Look for candidate whose name is a reasonable match for query term
    for (const c of candidates) {
      if (c && c.name && c.name.trim()) {
        if (this.isReasonableOnlineMatch(q, c.name)) {
          return c;
        }
      }
    }

    return null;
  }

  /**
   * RxNorm Concept Properties
   */
  static async getRxNormProperties(rxcui) {
    if (!rxcui) return null;
    const cacheKey = `rxnorm_prop_${rxcui}`;
    const cached = MedinexDB.getMedicineCache(cacheKey);
    if (cached) return cached;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.TIMEOUT_MS);

    try {
      const url = `${this.RXNORM_BASE_URL}REST/rxcui/${encodeURIComponent(rxcui)}/properties.json`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) return null;
      const data = await response.json();
      const props = data?.properties || null;
      if (props) {
        MedinexDB.setMedicineCache(cacheKey, props);
      }
      return props;
    } catch (err) {
      clearTimeout(timeout);
      return null;
    }
  }

  /**
   * Retrieves active generic ingredients (IN) for an RxNorm concept
   */
  static async getRxNormIngredients(rxcui) {
    if (!rxcui) return [];
    const cacheKey = `rxnorm_ingr_${rxcui}`;
    const cached = MedinexDB.getMedicineCache(cacheKey);
    if (cached) return cached;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.TIMEOUT_MS);

    try {
      const url = `${this.RXNORM_BASE_URL}REST/rxcui/${encodeURIComponent(rxcui)}/related.json?tty=IN+PIN`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) return [];
      const data = await response.json();
      const conceptGroup = data?.relatedGroup?.conceptGroup || [];
      const ingredients = [];

      for (const group of conceptGroup) {
        if (group.conceptProperties) {
          for (const cp of group.conceptProperties) {
            if (cp.name && !ingredients.includes(cp.name)) {
              ingredients.push(cp.name);
            }
          }
        }
      }

      if (ingredients.length > 0) {
        MedinexDB.setMedicineCache(cacheKey, ingredients);
      }
      return ingredients;
    } catch (err) {
      clearTimeout(timeout);
      return [];
    }
  }

  /**
   * FDA Drug Label API Search (openFDA)
   * Searches by brand or generic name
   */
  static async searchOpenFda(term, limit = 1) {
    if (!term || term.trim().length < 2) return null;
    const cleanTerm = term.trim();
    const cacheKey = `openfda_${cleanTerm.toLowerCase()}`;
    const cached = MedinexDB.getMedicineCache(cacheKey);
    if (cached) return cached;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.TIMEOUT_MS);

    try {
      // Query generic name, brand name, or substance
      const query = `openfda.generic_name:"${cleanTerm}"+openfda.brand_name:"${cleanTerm}"`;
      const url = `${this.OPENFDA_BASE_URL}drug/label.json?search=${encodeURIComponent(query)}&limit=${limit}`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) {
        // Fallback to simpler single-field query
        return await this.searchOpenFdaFallback(cleanTerm, limit);
      }
      const data = await response.json();
      if (data?.results?.length > 0) {
        const item = data.results[0];
        const openfda = item.openfda || {};
        const result = {
          genericName: openfda.generic_name?.[0] || null,
          brandName: openfda.brand_name?.[0] || null,
          manufacturer: openfda.manufacturer_name?.[0] || null,
          substanceName: openfda.substance_name?.[0] || null,
          indications: item.indications_and_usage?.[0] || item.description?.[0] || null,
          dosageForm: openfda.dosage_form?.[0] || null,
          productType: openfda.product_type?.[0] || null
        };
        MedinexDB.setMedicineCache(cacheKey, result);
        return result;
      }
      return null;
    } catch (err) {
      clearTimeout(timeout);
      return null;
    }
  }

  static async searchOpenFdaFallback(term, limit = 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);
    try {
      const url = `${this.OPENFDA_BASE_URL}drug/label.json?search=openfda.substance_name:"${encodeURIComponent(term)}"&limit=${limit}`;
      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeout);
      if (!response.ok) return null;
      const data = await response.json();
      if (data?.results?.length > 0) {
        const item = data.results[0];
        const openfda = item.openfda || {};
        return {
          genericName: openfda.generic_name?.[0] || null,
          brandName: openfda.brand_name?.[0] || null,
          manufacturer: openfda.manufacturer_name?.[0] || null,
          substanceName: openfda.substance_name?.[0] || null,
          indications: item.indications_and_usage?.[0] || null
        };
      }
      return null;
    } catch (e) {
      clearTimeout(timeout);
      return null;
    }
  }

  /**
   * UNIVERSAL RESOLUTION ENGINE:
   * Resolves an unfamiliar medicine token by querying RxNorm, concept properties,
   * active ingredients, and OpenFDA labeling data.
   */
  static async resolveUnfamiliarMedicineOnline(candidateName, detectedStrength = "", detectedForm = "") {
    if (!candidateName || candidateName.trim().length < 3) return null;
    const query = candidateName.trim();
    const cacheKey = `resolved_${query.toLowerCase()}_${detectedStrength.toLowerCase()}`;
    const cached = MedinexDB.getMedicineCache(cacheKey);
    if (cached) return cached;

    // 1. Search RxNorm concept
    const rxNormCandidate = await this.searchRxNorm(query, 5);

    if (rxNormCandidate && rxNormCandidate.rxcui) {
      const rxcui = rxNormCandidate.rxcui;
      const [props, ingredients, fdaData] = await Promise.all([
        this.getRxNormProperties(rxcui),
        this.getRxNormIngredients(rxcui),
        this.searchOpenFda(query, 1)
      ]);

      const officialName = props?.name || rxNormCandidate.name || query;
      const activeSalt = ingredients.length > 0
        ? ingredients.join(" + ").toLowerCase()
        : (fdaData?.genericName || query).toLowerCase();

      const resolved = {
        name: query.toLowerCase(),
        officialName: officialName,
        activeSalt: activeSalt,
        rxcui: rxcui,
        category: fdaData?.productType || "Prescription Medicine",
        indication: fdaData?.indications ? this.summarizeIndication(fdaData.indications) : "Clinical therapeutic medication",
        uses: fdaData?.indications ? this.summarizeIndication(fdaData.indications) : `Active therapeutic formulation (${activeSalt})`,
        manufacturer: fdaData?.manufacturer || "Not specified on packaging",
        strength: detectedStrength || this.extractStrengthFromConcept(officialName) || "",
        dosageForm: detectedForm || fdaData?.dosageForm || "Tablet",
        recommendedTiming: "As directed by prescribing physician",
        dataSource: `NLM RxNorm Concept [RxCUI: ${rxcui}] & FDA Drug Label`,
        isOnlineDiscovered: true
      };

      MedinexDB.setMedicineCache(cacheKey, resolved);
      return resolved;
    }

    // 2. If RxNorm didn't return an exact concept, try OpenFDA directly
    const fdaData = await this.searchOpenFda(query, 1);
    if (fdaData && (fdaData.genericName || fdaData.brandName)) {
      const activeSalt = (fdaData.genericName || fdaData.substanceName || query).toLowerCase();
      const resolved = {
        name: query.toLowerCase(),
        officialName: fdaData.brandName || query,
        activeSalt: activeSalt,
        rxcui: "N/A",
        category: "FDA Approved Drug Formulation",
        indication: fdaData.indications ? this.summarizeIndication(fdaData.indications) : "Prescription medicine",
        uses: fdaData.indications ? this.summarizeIndication(fdaData.indications) : `Therapeutic agent: ${activeSalt}`,
        manufacturer: fdaData.manufacturer || "Not specified on packaging",
        strength: detectedStrength || "",
        dosageForm: detectedForm || fdaData.dosageForm || "Tablet",
        recommendedTiming: "As directed by prescribing physician",
        dataSource: "US FDA Drug Label Database",
        isOnlineDiscovered: true
      };

      MedinexDB.setMedicineCache(cacheKey, resolved);
      return resolved;
    }

    return null;
  }

  static extractStrengthFromConcept(conceptName) {
    if (!conceptName) return "";
    const m = conceptName.match(/\b(\d+(?:\.\d+)?\s*(?:mg|mcg|g|ml|iu|%)(?:\s*\/\s*\d+(?:\.\d+)?\s*(?:mg|ml))?)\b/i);
    return m ? m[1] : "";
  }

  static summarizeIndication(text) {
    if (!text) return "Prescription medicine";
    // Take first complete sentence or up to 160 characters
    const clean = text.replace(/INDICATIONS AND USAGE:?/i, "").trim();
    const periodIdx = clean.indexOf(". ");
    if (periodIdx > 20 && periodIdx < 200) {
      return clean.slice(0, periodIdx + 1).trim();
    }
    return clean.slice(0, 160).trim() + "...";
  }
}
