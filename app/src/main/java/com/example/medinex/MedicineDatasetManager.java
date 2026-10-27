package com.example.medinex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MedicineDatasetManager {

    private static final String TAG = "MedicineDatasetManager";
    private static final String SQLITE_DB_NAME = "medinex_medicine_dataset.db";

    public enum VerificationTier {
        VERIFIED("🟡 Probable Match — Clinical Confirmation Required"),
        LIKELY("🟡 Candidate Match — Please Confirm"),
        UNCONFIRMED("🔴 Medicine Not Confidently Identified");

        private final String displayTitle;

        VerificationTier(String displayTitle) {
            this.displayTitle = displayTitle;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }
    }

    // ============================================================
    // DATA MODELS
    // ============================================================

    public static class MedicineItem {
        public String name;
        public String activeSalt;
        public String category;
        public String indication;
        public String uses;
        public String recommendedTiming;
        public String manufacturer;
        public String strength;
        public String dosageForm;

        public MedicineItem(String name, String activeSalt, String category,
                            String indication, String uses, String recommendedTiming) {
            this(name, activeSalt, category, indication, uses, recommendedTiming, null, null, null);
        }

        public MedicineItem(String name, String activeSalt, String category,
                            String indication, String uses, String recommendedTiming,
                            String manufacturer, String strength, String dosageForm) {
            this.name = name;
            this.activeSalt = activeSalt;
            this.category = category != null && !category.isEmpty() ? category : "General Care";
            this.indication = indication != null && !indication.isEmpty() ? indication : "Prescription medicine";
            this.uses = uses != null && !uses.isEmpty() ? uses : this.indication;
            this.recommendedTiming = recommendedTiming != null ? recommendedTiming : "";
            this.manufacturer = manufacturer != null && !manufacturer.isEmpty() ? manufacturer : "Not specified on package";
            this.strength = strength != null ? strength : "";
            this.dosageForm = dosageForm != null ? dosageForm : "Tablet";
        }
    }

    public static class InteractionItem {
        public String drug1;
        public String drug2;
        public String riskLevel;
        public String description;
        public String recommendation;

        public InteractionItem(String drug1, String drug2, String riskLevel,
                               String description, String recommendation) {
            this.drug1 = drug1;
            this.drug2 = drug2;
            this.riskLevel = riskLevel;
            this.description = description;
            this.recommendation = recommendation;
        }
    }

    public static class DuplicateResult {
        public MedicineItem med1;
        public MedicineItem med2;
        public String sharedSalt;

        public DuplicateResult(MedicineItem med1, MedicineItem med2, String sharedSalt) {
            this.med1 = med1;
            this.med2 = med2;
            this.sharedSalt = sharedSalt;
        }
    }

    public static class ExtractedMedicineDetail {
        public MedicineItem item;
        public String detectedNameInOcr;
        public String detectedBrandName;
        public String detectedActiveSalt;
        public String strength;
        public String dosage;
        public String dosageForm;
        public String frequency;
        public String duration;
        public String manufacturer;
        public float matchConfidence;
        public VerificationTier verificationTier;
        public String dataSource;
        public String onlineMatchInfo;

        public ExtractedMedicineDetail(MedicineItem item, String detectedNameInOcr,
                                       String strength, String dosage,
                                       String frequency, String duration,
                                       float matchConfidence) {
            this(item, detectedNameInOcr, strength, dosage, frequency, duration, matchConfidence,
                    matchConfidence >= 0.85f ? VerificationTier.VERIFIED :
                            (matchConfidence >= 0.60f ? VerificationTier.LIKELY : VerificationTier.UNCONFIRMED),
                    "Medinex Clinical Dataset");
        }

        public ExtractedMedicineDetail(MedicineItem item, String detectedNameInOcr,
                                       String strength, String dosage,
                                       String frequency, String duration,
                                       float matchConfidence,
                                       VerificationTier verificationTier,
                                       String dataSource) {
            this.item = item;
            this.detectedNameInOcr = detectedNameInOcr;
            this.detectedBrandName = item != null ? item.name : detectedNameInOcr;
            this.detectedActiveSalt = item != null ? item.activeSalt : "Unknown";

            this.strength = isEmpty(strength) ? (item != null && !isEmpty(item.strength) ? item.strength : "Not detected") : strength.trim();
            this.dosage = isEmpty(dosage) ? "Not detected" : dosage.trim();
            this.dosageForm = item != null && !isEmpty(item.dosageForm) ? item.dosageForm : "Tablet / Capsule";
            this.frequency = isEmpty(frequency) ? "Not detected" : frequency.trim();
            this.duration = isEmpty(duration) ? "Not detected" : duration.trim();
            this.manufacturer = item != null && !isEmpty(item.manufacturer) ? item.manufacturer : "Information not available";
            this.matchConfidence = matchConfidence;
            this.verificationTier = verificationTier;
            this.dataSource = dataSource;
            this.onlineMatchInfo = null;
        }
    }

    public static class ParseResult {
        public List<ExtractedMedicineDetail> detectedMedicines = new ArrayList<>();
        public List<String> unmatchedOcrLines = new ArrayList<>();
        public boolean isUnreadableHandwriting = false;
        public String fullNormalizedOcrText = "";
        public String qualityNotice = null;
    }

    private static class MatchCandidate {
        MedicineItem item;
        String matchedText;
        float score;
        boolean isBrand;

        MatchCandidate(MedicineItem item, String matchedText, float score, boolean isBrand) {
            this.item = item;
            this.matchedText = matchedText;
            this.score = score;
            this.isBrand = isBrand;
        }
    }

    // ============================================================
    // SINGLETON & STORAGE
    // ============================================================

    private static MedicineDatasetManager instance;
    private final Context context;

    private final List<MedicineItem> inMemoryMedicines = new ArrayList<>();
    private final List<InteractionItem> interactions = new ArrayList<>();
    private SQLiteDatabase sqliteDatabase = null;
    private boolean isSqliteReady = false;

    private MedicineDatasetManager(Context context) {
        this.context = context.getApplicationContext();
        loadJsonDatabase(this.context);
        initSqliteDatabaseAsync();
    }

    MedicineDatasetManager() {
        this.context = null;
    }

    public static synchronized MedicineDatasetManager getTestInstance(InputStream jsonStream) {
        MedicineDatasetManager mgr = new MedicineDatasetManager();
        try {
            byte[] buffer = new byte[jsonStream.available()];
            int read = jsonStream.read(buffer);
            String jsonStr = new String(buffer, 0, read, StandardCharsets.UTF_8);
            mgr.loadFromJsonString(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Error loading test database", e);
        }
        return mgr;
    }

    public static synchronized MedicineDatasetManager getInstance(Context context) {
        if (instance == null) {
            instance = new MedicineDatasetManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadJsonDatabase(Context ctx) {
        try (InputStream is = ctx.getAssets().open("medicines_database.json")) {
            byte[] buffer = new byte[is.available()];
            int read = is.read(buffer);
            String jsonStr = new String(buffer, 0, read, StandardCharsets.UTF_8);
            loadFromJsonString(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Error loading medicines_database.json", e);
        }
    }

    public void loadFromJsonString(String jsonStr) {
        inMemoryMedicines.clear();
        interactions.clear();

        if (jsonStr == null || jsonStr.trim().isEmpty()) return;

        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();

            if (root.has("medicines") && root.get("medicines").isJsonArray()) {
                JsonArray medsArray = root.getAsJsonArray("medicines");
                for (JsonElement el : medsArray) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();

                    String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                    String activeSalt = obj.has("active_salt") && !obj.get("active_salt").isJsonNull() ? obj.get("active_salt").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                    String category = obj.has("category") && !obj.get("category").isJsonNull() ? obj.get("category").getAsString().trim() : "General Care";
                    String indication = obj.has("indication") && !obj.get("indication").isJsonNull() ? obj.get("indication").getAsString().trim() : "";
                    String uses = obj.has("uses") && !obj.get("uses").isJsonNull() ? obj.get("uses").getAsString().trim() : indication;
                    String timing = obj.has("recommended_timing") && !obj.get("recommended_timing").isJsonNull() ? obj.get("recommended_timing").getAsString().trim() : "";
                    String manufacturer = obj.has("manufacturer") && !obj.get("manufacturer").isJsonNull() ? obj.get("manufacturer").getAsString().trim() : "";
                    String strength = obj.has("strength") && !obj.get("strength").isJsonNull() ? obj.get("strength").getAsString().trim() : "";
                    String dosageForm = obj.has("dosage_form") && !obj.get("dosage_form").isJsonNull() ? obj.get("dosage_form").getAsString().trim() : "Tablet";

                    if (!name.isEmpty()) {
                        inMemoryMedicines.add(new MedicineItem(
                                name, activeSalt, category, indication, uses, timing,
                                manufacturer, strength, dosageForm
                        ));
                    }
                }
            }

            if (root.has("interactions") && root.get("interactions").isJsonArray()) {
                JsonArray interArray = root.getAsJsonArray("interactions");
                for (JsonElement el : interArray) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();

                    String drug1 = obj.has("drug1") && !obj.get("drug1").isJsonNull() ? obj.get("drug1").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                    String drug2 = obj.has("drug2") && !obj.get("drug2").isJsonNull() ? obj.get("drug2").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                    String riskLevel = obj.has("risk_level") && !obj.get("risk_level").isJsonNull() ? obj.get("risk_level").getAsString().trim() : "MEDIUM";
                    String description = obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString().trim() : "";
                    String recommendation = obj.has("recommendation") && !obj.get("recommendation").isJsonNull() ? obj.get("recommendation").getAsString().trim() : "";

                    if (!drug1.isEmpty() && !drug2.isEmpty()) {
                        interactions.add(new InteractionItem(
                                drug1, drug2, riskLevel, description, recommendation
                        ));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing json database: " + e.getMessage());
        }
    }

    private void initSqliteDatabaseAsync() {
        AsyncTask.execute(() -> {
            try {
                File dbFile = context.getDatabasePath(SQLITE_DB_NAME);
                if (!dbFile.exists() || dbFile.length() < 10000000) {
                    if (dbFile.getParentFile() != null) {
                        dbFile.getParentFile().mkdirs();
                    }
                    try (InputStream is = context.getAssets().open(SQLITE_DB_NAME);
                         OutputStream os = new FileOutputStream(dbFile)) {
                        byte[] buffer = new byte[64 * 1024];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                        os.flush();
                    }
                }

                if (dbFile.exists() && dbFile.length() > 10000000) {
                    sqliteDatabase = SQLiteDatabase.openDatabase(
                            dbFile.getAbsolutePath(),
                            null,
                            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS
                    );
                    isSqliteReady = true;
                    Log.d(TAG, "SQLite medicine dataset initialized with 250,000+ records.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not open SQLite database; in-memory catalog active.", e);
            }
        });
    }

    // ============================================================
    // REGEX PATTERNS FOR NOISE & DETAILS
    // ============================================================

    private final Pattern STRENGTH_PATTERN = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?\\s*(?:mg|g|mcg|μg|ml|iu|mgs)(?:\\s*[\\/+]\\s*\\d+(?:\\.\\d+)?\\s*(?:mg|g|mcg|μg|ml|iu))?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern DOSAGE_PATTERN = Pattern.compile(
            "\\b(\\d+(?:\\.\\d+)?\\s*(?:tablets?|capsules?|tabs?|caps?|pills?|spoons?|puffs?|drops?|ml))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern DOSAGE_FORM_PATTERN = Pattern.compile(
            "\\b(tablets?|capsules?|syrup|suspension|injection|gel|ointment|drops?|cream|inhaler|effervescent)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern FREQUENCY_PATTERN = Pattern.compile(
            "\\b(" +
                    "1-0-1|1-1-1|1-0-0|0-0-1|0-1-0|0-1-1|1-1-0|" +
                    "1-0-1-1|0-0-0|" +
                    "OD|BD|BID|TDS|TID|QID|HS|QHS|PRN|" +
                    "once daily|twice daily|thrice daily|" +
                    "once a day|twice a day|three times a day|" +
                    "every 8 hours|every 12 hours|" +
                    "morning & night|morning and night|" +
                    "morning|afternoon|night|" +
                    "before breakfast|after breakfast|" +
                    "before food|after food|after meals|with food" +
                    ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Pattern DURATION_PATTERN = Pattern.compile(
            "\\b((?:for\\s+)?\\d+\\s*(?:days?|weeks?|months?|d|wk|wks))\\b",
            Pattern.CASE_INSENSITIVE
    );

    // ============================================================
    // MAIN OCR PARSER & MEDICINE EXTRACTION PIPELINE
    // ============================================================

    public ParseResult parsePrescriptionText(String rawOcrText) {
        ParseResult result = new ParseResult();

        if (rawOcrText == null || rawOcrText.trim().isEmpty()) {
            result.isUnreadableHandwriting = true;
            return result;
        }

        String cleanText = normalizeOcrText(rawOcrText);
        result.fullNormalizedOcrText = cleanText;

        if (cleanText.length() < 3) {
            result.isUnreadableHandwriting = true;
            return result;
        }

        String[] rawLines = cleanText.split("\\n");
        List<String> validCandidateLines = new ArrayList<>();

        // 1. Separate relevant lines from packaging boilerplate / legal noise
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (isPackagingOrLegalNoise(trimmed) || isPatientInformationLine(trimmed)) {
                // Ignore packaging boilerplate so it does not pollute unmatched lines
                continue;
            }
            validCandidateLines.add(trimmed);
        }

        // Global strength, dosage form, frequency, duration
        String globalStrength = extractPattern(cleanText, STRENGTH_PATTERN);
        String globalDosage = extractPattern(cleanText, DOSAGE_PATTERN);
        String globalForm = extractPattern(cleanText, DOSAGE_FORM_PATTERN);
        String globalFrequency = extractPattern(cleanText, FREQUENCY_PATTERN);
        String globalDuration = extractPattern(cleanText, DURATION_PATTERN);

        // 2. Identify brand candidates & generic ingredient candidates
        List<MatchCandidate> detectedCandidates = new ArrayList<>();
        Set<String> matchedTokensInOcr = new HashSet<>();

        for (String candidateLine : validCandidateLines) {
            MatchCandidate match = findMedicineMatchInText(candidateLine, matchedTokensInOcr);
            if (match != null && match.score >= 0.75f) {
                detectedCandidates.add(match);
                matchedTokensInOcr.add(match.item.name);
            }
        }

        // Also search full OCR text if lines split multi-word brand names
        if (detectedCandidates.isEmpty()) {
            for (MedicineItem item : inMemoryMedicines) {
                if (containsMedicineText(cleanText, item.name)) {
                    detectedCandidates.add(new MatchCandidate(item, item.name, 0.88f, true));
                    matchedTokensInOcr.add(item.name);
                    break;
                }
            }
        }

        // 3. Fallback: Search in SQLite database (250,000+ catalog) if local in-memory was not matched
        if (detectedCandidates.isEmpty() && isSqliteReady && sqliteDatabase != null) {
            for (String candidateLine : validCandidateLines) {
                MedicineItem dbItem = searchInSqlite(candidateLine);
                if (dbItem != null) {
                    detectedCandidates.add(new MatchCandidate(dbItem, candidateLine, 0.90f, true));
                    break;
                }
            }
        }

        // 4. Fallback: Check if generic active salt is mentioned without brand (e.g., "Paracetamol Tablets IP")
        if (detectedCandidates.isEmpty()) {
            MatchCandidate genericMatch = findGenericIngredientMatch(cleanText);
            if (genericMatch != null) {
                detectedCandidates.add(genericMatch);
            }
        }

        // 5. UNIFICATION & DEDUPLICATION:
        // Do NOT separate brand and ingredient into duplicate medicine items.
        // If a candidate is Calpol (Brand), its ingredient is Paracetamol.
        // If "Paracetamol" also appears in OCR, confirm Calpol's confidence, but do NOT add Paracetamol as a second medicine!
        // Do NOT add other brands (Dolo, Crocin) that share Paracetamol!
        Map<String, ExtractedMedicineDetail> uniqueMedicinesMap = new HashMap<>();

        for (MatchCandidate candidate : detectedCandidates) {
            MedicineItem item = candidate.item;
            String brandKey = item.name.toLowerCase(Locale.ROOT);

            // Check if already represented
            if (uniqueMedicinesMap.containsKey(brandKey)) {
                continue;
            }

            // Check if this is an ingredient that already belongs to an existing detected brand
            boolean belongsToExistingBrand = false;
            for (ExtractedMedicineDetail existing : uniqueMedicinesMap.values()) {
                if (saltsOverlap(existing.item.activeSalt, item.name) ||
                        saltsOverlap(existing.item.activeSalt, item.activeSalt)) {
                    // Ingredient matches already detected brand - calibrate confidence of that brand
                    existing.matchConfidence = Math.min(0.90f, existing.matchConfidence + 0.05f);
                    if (existing.matchConfidence >= 0.85f) {
                        existing.verificationTier = VerificationTier.VERIFIED;
                    }
                    belongsToExistingBrand = true;
                    break;
                }
            }

            if (belongsToExistingBrand) {
                continue;
            }

            // Extract line-specific or global attributes
            String strength = !isEmpty(globalStrength) ? globalStrength : item.strength;
            String form = !isEmpty(globalForm) ? capitalize(globalForm) : item.dosageForm;

            // Confidence calculation (calibrated realistic scoring for offline candidates, max 0.90)
            float confidence = Math.min(candidate.score, 0.90f);
            if (containsMedicineText(cleanText, item.activeSalt) && !item.name.equals(item.activeSalt)) {
                confidence = Math.min(0.88f, confidence + 0.08f);
            }
            if (!isEmpty(strength) && !strength.equals("Not detected") && cleanText.contains(strength.replaceAll("\\s+", ""))) {
                confidence = Math.min(0.90f, confidence + 0.05f);
            }

            VerificationTier tier;
            if (confidence >= 0.85f) {
                tier = VerificationTier.VERIFIED;
            } else if (confidence >= 0.60f) {
                tier = VerificationTier.LIKELY;
            } else {
                tier = VerificationTier.UNCONFIRMED;
            }

            ExtractedMedicineDetail detail = new ExtractedMedicineDetail(
                    item,
                    candidate.matchedText,
                    strength,
                    globalDosage,
                    formatFrequency(globalFrequency),
                    globalDuration,
                    confidence,
                    tier,
                    isSqliteReady ? "Medinex Indian Drug Index + NLM RxNorm" : "Medinex Clinical Dataset"
            );
            detail.dosageForm = form;

            uniqueMedicinesMap.put(brandKey, detail);
        }

        result.detectedMedicines.addAll(uniqueMedicinesMap.values());

        // Sort by confidence descending
        Collections.sort(result.detectedMedicines, (a, b) -> Float.compare(b.matchConfidence, a.matchConfidence));

        // 6. Build clean unmatched lines (ignoring noise)
        for (String line : validCandidateLines) {
            String trimmed = line.trim();
            boolean matched = false;
            for (ExtractedMedicineDetail med : result.detectedMedicines) {
                if (containsMedicineText(trimmed, med.item.name) ||
                        containsMedicineText(trimmed, med.item.activeSalt) ||
                        trimmed.equalsIgnoreCase(med.item.name) ||
                        trimmed.equalsIgnoreCase(med.item.activeSalt)) {
                    matched = true;
                    break;
                }
            }
            if (!matched && trimmed.length() >= 3 && !isNumericOrSymbolicOnly(trimmed)) {
                result.unmatchedOcrLines.add(trimmed);
            }
        }

        // Handwriting / unreadable check
        if (result.detectedMedicines.isEmpty()) {
            int alphaCount = 0;
            for (char c : cleanText.toCharArray()) {
                if (Character.isLetter(c)) alphaCount++;
            }
            if (alphaCount < 6 || result.unmatchedOcrLines.isEmpty()) {
                result.isUnreadableHandwriting = true;
            }
        }

        return result;
    }

    // ============================================================
    // CANDIDATE MATCHING & SEARCH
    // ============================================================

    private MatchCandidate findMedicineMatchInText(String text, Set<String> alreadyMatched) {
        if (text == null || text.trim().isEmpty()) return null;

        String cleanText = normalizeComparableText(text);
        if (cleanText.length() < 3) return null;

        MatchCandidate bestCandidate = null;

        for (MedicineItem item : inMemoryMedicines) {
            if (alreadyMatched.contains(item.name)) continue;

            String normName = normalizeComparableText(item.name);
            String normSalt = normalizeComparableText(item.activeSalt);

            // 1. Exact match (perfect match wins immediately, calibrated at 0.90 max for offline packaging OCR)
            if (!normName.isEmpty() && cleanText.equals(normName)) {
                return new MatchCandidate(item, item.name, 0.90f, true);
            }

            // 2. Contained brand match (prioritize longer/more specific brand names)
            if (!normName.isEmpty() && containsComparable(cleanText, normName)) {
                float brandScore = 0.86f + 0.04f * ((float) normName.length() / Math.max(normName.length(), cleanText.length()));
                if (bestCandidate == null || brandScore > bestCandidate.score) {
                    bestCandidate = new MatchCandidate(item, item.name, brandScore, true);
                }
            }

            // 3. Exact or contained salt match
            if (!normSalt.isEmpty() && containsComparable(cleanText, normSalt)) {
                float saltScore = 0.90f + 0.03f * ((float) normSalt.length() / Math.max(normSalt.length(), cleanText.length()));
                if (bestCandidate == null || saltScore > bestCandidate.score) {
                    bestCandidate = new MatchCandidate(item, item.activeSalt, saltScore, false);
                }
            }

            // 3. Word-by-word fuzzy comparison for OCR typos
            String[] tokens = cleanText.split("\\s+");
            for (String token : tokens) {
                if (token.length() < 4) continue;

                float nameSim = fuzzySimilarity(token, normName);
                if (nameSim >= 0.86f && (bestCandidate == null || nameSim > bestCandidate.score)) {
                    bestCandidate = new MatchCandidate(item, token, nameSim, true);
                }

                float saltSim = fuzzySimilarity(token, normSalt);
                if (saltSim >= 0.88f && (bestCandidate == null || saltSim > bestCandidate.score)) {
                    bestCandidate = new MatchCandidate(item, token, saltSim, false);
                }
            }
        }

        return bestCandidate;
    }

    private MatchCandidate findGenericIngredientMatch(String fullText) {
        String lower = fullText.toLowerCase(Locale.ROOT);

        // Check common active generic ingredients
        String[] genericSalts = {
                "paracetamol", "acetaminophen", "aceclofenac", "thiocolchicoside",
                "sacubitril", "valsartan", "ibuprofen", "diclofenac", "cetirizine",
                "levocetirizine", "pantoprazole", "omeprazole", "metformin",
                "azithromycin", "amoxicillin", "atorvastatin"
        };

        for (String salt : genericSalts) {
            if (containsMedicineText(lower, salt)) {
                for (MedicineItem item : inMemoryMedicines) {
                    if (item.activeSalt.contains(salt)) {
                        return new MatchCandidate(item, salt, 0.84f, false);
                    }
                }
            }
        }

        return null;
    }

    private MedicineItem searchInSqlite(String queryText) {
        if (!isSqliteReady || sqliteDatabase == null) return null;

        String normalized = normalizeComparableText(queryText);
        if (normalized.length() < 3) return null;

        Cursor cursor = null;
        try {
            // Check exact prefix match on normalized_name
            String sql = "SELECT name, manufacturer, composition, category, uses FROM medicines WHERE normalized_name LIKE ? LIMIT 1";
            cursor = sqliteDatabase.rawQuery(sql, new String[]{normalized + "%"});

            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                String manufacturer = cursor.getString(1);
                String composition = cursor.getString(2);
                String category = cursor.getString(3);
                String uses = cursor.getString(4);

                return new MedicineItem(
                        name,
                        composition != null ? composition : "Standard Active Composition",
                        category != null ? category : "Clinical Medicine",
                        uses != null ? uses : "As directed by physician",
                        uses != null ? uses : "Therapeutic use",
                        "As directed by physician",
                        manufacturer,
                        "",
                        "Tablet"
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "SQLite lookup error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return null;
    }

    // ============================================================
    // NOISE & PACKAGING FILTERS
    // ============================================================

    public static boolean isPackagingOrLegalNoise(String line) {
        if (line == null) return true;
        String lower = line.toLowerCase(Locale.ROOT).trim();

        if (lower.length() < 2) return true;

        String[] noiseKeywords = {
                "mfg. lic", "mfg lic", "lic. no", "lic no", "batch no", "batch",
                "b. no", "b.no", "exp. date", "exp date", "expiry", "m.r.p", "mrp",
                "inclusive of all taxes", "net content", "net vol", "marketed by",
                "mfg by", "manufactured by", "manufactured in india", "packed by",
                "plot no", "industrial area", "regd. off", "regd office", "pin code",
                "schedule h", "schedule h1", "schedule g", "prescription drug",
                "caution: not to be sold", "caution : not to be sold",
                "warning: taking more than", "warning: to be sold", "keep out of reach",
                "out of reach", "reach of children",
                "store in a cool", "store below", "protect from light", "protect from moisture",
                "swallow whole", "do not crush", "physician sample", "sample not for sale",
                "excipients q.s", "excipients: q.s", "titanium dioxide", "approved colour",
                "colours used", "for therapeutic use", "for oral use only",
                "shake well before use", "10 tablets", "15 tablets", "strip of 10",
                "each film coated", "each uncoated tablet", "each hard gelatin"
        };

        for (String keyword : noiseKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isPatientInformationLine(String line) {
        if (line == null) return false;
        String text = line.toLowerCase(Locale.ROOT).trim();

        String[] keywords = {
                "patient name", "patient:", "name:", "age:", "gender:", "sex:",
                "weight:", "height:", "address:", "phone:", "mobile:", "date:",
                "doctor:", "dr.", "dr ", "hospital:", "clinic:", "clinical description",
                "diagnosis:", "advice:", "patient id", "registration no", "reg no", "rx:"
        };

        for (String keyword : keywords) {
            if (text.startsWith(keyword) || text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean isNumericOrSymbolicOnly(String text) {
        if (text == null) return true;
        String clean = text.replaceAll("[^a-zA-Z]", "");
        return clean.length() < 2;
    }

    // ============================================================
    // DUPLICATE & INTERACTION CHECKS (SAFE FOR UNCONFIRMED)
    // ============================================================

    public List<DuplicateResult> checkDuplicates(List<ExtractedMedicineDetail> matchedDetails) {
        List<DuplicateResult> duplicates = new ArrayList<>();
        if (matchedDetails == null || matchedDetails.size() < 2) return duplicates;

        // Filter only CONFIRMED or LIKELY medicines (do not alert on unconfirmed candidates)
        List<MedicineItem> validMedicines = new ArrayList<>();
        for (ExtractedMedicineDetail detail : matchedDetails) {
            if (detail != null && detail.verificationTier != VerificationTier.UNCONFIRMED && detail.item != null) {
                validMedicines.add(detail.item);
            }
        }

        for (int i = 0; i < validMedicines.size(); i++) {
            for (int j = i + 1; j < validMedicines.size(); j++) {
                MedicineItem m1 = validMedicines.get(i);
                MedicineItem m2 = validMedicines.get(j);

                if (m1.name.equalsIgnoreCase(m2.name)) continue;

                if (saltsOverlap(m1.activeSalt, m2.activeSalt)) {
                    duplicates.add(new DuplicateResult(m1, m2, findSharedSalt(m1.activeSalt, m2.activeSalt)));
                }
            }
        }

        return duplicates;
    }

    public List<InteractionItem> checkInteractions(List<ExtractedMedicineDetail> matchedDetails) {
        List<InteractionItem> detectedInteractions = new ArrayList<>();
        if (matchedDetails == null || matchedDetails.size() < 2) return detectedInteractions;

        // Filter only CONFIRMED or LIKELY medicines
        List<MedicineItem> validMedicines = new ArrayList<>();
        for (ExtractedMedicineDetail detail : matchedDetails) {
            if (detail != null && detail.verificationTier != VerificationTier.UNCONFIRMED && detail.item != null) {
                validMedicines.add(detail.item);
            }
        }

        for (int i = 0; i < validMedicines.size(); i++) {
            for (int j = i + 1; j < validMedicines.size(); j++) {
                MedicineItem m1 = validMedicines.get(i);
                MedicineItem m2 = validMedicines.get(j);

                for (InteractionItem rule : interactions) {
                    boolean m1D1 = matchesRule(m1, rule.drug1);
                    boolean m2D2 = matchesRule(m2, rule.drug2);
                    boolean m1D2 = matchesRule(m1, rule.drug2);
                    boolean m2D1 = matchesRule(m2, rule.drug1);

                    if ((m1D1 && m2D2) || (m1D2 && m2D1)) {
                        if (!containsInteraction(detectedInteractions, rule)) {
                            detectedInteractions.add(rule);
                        }
                    }
                }
            }
        }

        return detectedInteractions;
    }

    private boolean saltsOverlap(String salt1, String salt2) {
        if (salt1 == null || salt2 == null || salt1.isEmpty() || salt2.isEmpty()) return false;

        String[] parts1 = salt1.split("[\\+,/]");
        String[] parts2 = salt2.split("[\\+,/]");

        for (String p1 : parts1) {
            String clean1 = p1.trim().toLowerCase(Locale.ROOT);
            if (clean1.length() < 3) continue;

            for (String p2 : parts2) {
                String clean2 = p2.trim().toLowerCase(Locale.ROOT);
                if (clean2.length() < 3) continue;

                if (clean1.equals(clean2) || clean1.contains(clean2) || clean2.contains(clean1)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String findSharedSalt(String salt1, String salt2) {
        String[] parts1 = salt1.split("[\\+,/]");
        String[] parts2 = salt2.split("[\\+,/]");

        for (String p1 : parts1) {
            String clean1 = p1.trim().toLowerCase(Locale.ROOT);
            for (String p2 : parts2) {
                String clean2 = p2.trim().toLowerCase(Locale.ROOT);
                if (clean1.equalsIgnoreCase(clean2) || clean1.contains(clean2)) {
                    return p1.trim();
                }
            }
        }
        return salt1;
    }

    private boolean matchesRule(MedicineItem medicine, String ruleDrug) {
        if (medicine == null || ruleDrug == null) return false;

        String rule = normalizeComparableText(ruleDrug);
        String name = normalizeComparableText(medicine.name);
        String salt = normalizeComparableText(medicine.activeSalt);

        return containsComparable(name, rule) || containsComparable(salt, rule);
    }

    private boolean containsInteraction(List<InteractionItem> list, InteractionItem target) {
        for (InteractionItem item : list) {
            boolean same = item.drug1.equals(target.drug1) && item.drug2.equals(target.drug2);
            boolean reverse = item.drug1.equals(target.drug2) && item.drug2.equals(target.drug1);
            if (same || reverse) return true;
        }
        return false;
    }

    // ============================================================
    // NORMALIZATION & FUZZY COMPARISON
    // ============================================================

    public String normalizeOcrText(String rawText) {
        if (rawText == null) return "";
        String cleaned = rawText.replace("\r\n", "\n").replace("\r", "\n");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    public String normalizeComparableText(String text) {
        if (text == null) return "";
        String val = text.toLowerCase(Locale.ROOT).trim();

        // Strip pharmacopoeia indicators (IP, BP, USP)
        val = val.replaceAll("\\b(i\\.?p\\.?|b\\.?p\\.?|u\\.?s\\.?p\\.?)\\b", " ");
        // Strip punctuation
        val = val.replaceAll("[^a-z0-9\\s]", " ");
        val = val.replaceAll("\\s+", " ");
        return val.trim();
    }

    private boolean containsMedicineText(String text, String target) {
        if (text == null || target == null) return false;
        String a = normalizeComparableText(text);
        String b = normalizeComparableText(target);
        if (a.isEmpty() || b.isEmpty()) return false;
        return containsComparable(a, b);
    }

    private boolean containsComparable(String text, String target) {
        if (text.equals(target)) return true;
        return (" " + text + " ").contains(" " + target + " ");
    }

    public float fuzzySimilarity(String input, String target) {
        if (input == null || target == null || input.isEmpty() || target.isEmpty()) return 0f;
        String a = normalizeComparableText(input);
        String b = normalizeComparableText(target);
        if (a.equals(b)) return 1.0f;
        if (a.length() < 3 || b.length() < 3) return 0f;

        int distance = computeLevenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0f - ((float) distance / (float) maxLen);
    }

    private int computeLevenshteinDistance(String lhs, String rhs) {
        int len0 = lhs.length() + 1;
        int len1 = rhs.length() + 1;
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        for (int i = 0; i < len0; i++) cost[i] = i;

        for (int j = 1; j < len1; j++) {
            newcost[0] = j;
            for (int i = 1; i < len0; i++) {
                int match = lhs.charAt(i - 1) == rhs.charAt(j - 1) ? 0 : 1;
                int costReplace = cost[i - 1] + match;
                int costInsert = cost[i] + 1;
                int costDelete = newcost[i - 1] + 1;
                newcost[i] = Math.min(Math.min(costInsert, costDelete), costReplace);
            }
            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }
        return cost[len0 - 1];
    }

    private String extractPattern(String text, Pattern pattern) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String formatFrequency(String freq) {
        if (freq == null || freq.trim().isEmpty()) return "Not detected";
        String upper = freq.trim().toUpperCase(Locale.ROOT);
        switch (upper) {
            case "OD": return "Once daily (OD)";
            case "BD":
            case "BID": return "Twice daily (BD / BID)";
            case "TDS":
            case "TID": return "Thrice daily (TDS / TID)";
            case "QID": return "Four times daily (QID)";
            case "HS":
            case "QHS": return "At bedtime (HS)";
            case "1-0-1": return "1-0-1 (Morning & Night)";
            case "1-1-1": return "1-1-1 (Morning, Afternoon & Night)";
            case "1-0-0": return "1-0-0 (Morning)";
            case "0-0-1": return "0-0-1 (Night)";
            default: return freq.trim();
        }
    }

    private String capitalize(String value) {
        if (value == null || value.trim().isEmpty()) return "Unknown";
        String[] words = value.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
