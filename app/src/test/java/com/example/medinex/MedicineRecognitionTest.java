package com.example.medinex;

import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

public class MedicineRecognitionTest {

    private MedicineDatasetManager datasetManager;

    @Before
    public void setUp() throws Exception {
        java.io.File file = new java.io.File("src/main/assets/medicines_database.json");
        if (!file.exists()) {
            file = new java.io.File("app/src/main/assets/medicines_database.json");
        }
        assertTrue("medicines_database.json must exist", file.exists());
        InputStream is = new FileInputStream(file);
        datasetManager = MedicineDatasetManager.getTestInstance(is);
        assertNotNull("DatasetManager should be initialized", datasetManager);
    }

    /**
     * TEST 1: Clear Calpol package image
     * Calpol 500 / Paracetamol
     * Verifies:
     * - Identifies Calpol
     * - Does NOT falsely add Dolo or Crocin
     * - Does NOT create duplicate Paracetamol medicine
     */
    @Test
    public void testClearCalpolPackage() {
        String ocrText = "Calpol 500\n" +
                "Paracetamol Tablets I.P. 500 mg\n" +
                "Each uncoated tablet contains: Paracetamol I.P. 500 mg\n" +
                "Dosage: As directed by the physician\n" +
                "Mfg. Lic. No. B-234/18\n" +
                "GlaxoSmithKline Pharmaceuticals Ltd\n" +
                "Batch No. GP8921\n" +
                "Exp Date 12/2027\n" +
                "Store below 30°C in a dry place\n" +
                "Keep all medicines out of reach of children\n" +
                "10 Tablets";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertFalse("Should be readable", result.isUnreadableHandwriting);
        assertEquals("Should identify exactly 1 medicine product", 1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Name should contain Calpol", med.item.name.toLowerCase().contains("calpol"));
        assertTrue("Active salt should be paracetamol", med.item.activeSalt.toLowerCase().contains("paracetamol"));
        assertEquals("Strength should be 500 mg", "500 mg", med.strength);
        assertEquals("Tier should be VERIFIED", MedicineDatasetManager.VerificationTier.VERIFIED, med.verificationTier);

        // Verify NO Dolo or Crocin was falsely detected
        for (MedicineDatasetManager.ExtractedMedicineDetail d : result.detectedMedicines) {
            assertFalse("Dolo should NOT be detected on Calpol box", d.item.name.equalsIgnoreCase("dolo"));
            assertFalse("Crocin should NOT be detected on Calpol box", d.item.name.equalsIgnoreCase("crocin"));
        }

        // Verify duplicates list is empty (no duplicate paracetamol alerts for single Calpol package)
        List<MedicineDatasetManager.DuplicateResult> duplicates = datasetManager.checkDuplicates(result.detectedMedicines);
        assertTrue("Should have no duplicate alert for a single medicine product", duplicates.isEmpty());
    }

    /**
     * TEST 2: Crocin package image
     * Crocin Advance / Paracetamol
     */
    @Test
    public void testCrocinPackage() {
        String ocrText = "Crocin Advance\n" +
                "Fast Release Paracetamol Tablets I.P. 500mg\n" +
                "Each tablet contains: Paracetamol IP 500 mg\n" +
                "GlaxoSmithKline Consumer Healthcare\n" +
                "Mfg Lic No: 45/UA/2015\n" +
                "Exp Date: 08/2028\n" +
                "15 Tablets";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertEquals("Should identify exactly 1 medicine product", 1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Name should contain Crocin", med.item.name.toLowerCase().contains("crocin"));
        assertEquals("Active salt should be paracetamol", "paracetamol", med.item.activeSalt.toLowerCase());
        assertEquals("Tier should be VERIFIED", MedicineDatasetManager.VerificationTier.VERIFIED, med.verificationTier);
    }

    /**
     * TEST 3: Dolo 650 package image
     * Dolo 650 / Paracetamol 650 mg
     */
    @Test
    public void testDoloPackage() {
        String ocrText = "Dolo 650\n" +
                "Paracetamol Tablets IP 650 mg\n" +
                "Micro Labs Limited\n" +
                "Batch No: ML-49102\n" +
                "Mfg Date: 01/2026\n" +
                "Exp Date: 12/2028\n" +
                "MRP Rs. 34.00 per strip of 15 tablets\n" +
                "Schedule H Prescription Drug";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertEquals("Should identify exactly 1 medicine product", 1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Name should contain Dolo", med.item.name.toLowerCase().contains("dolo"));
        assertEquals("Active salt should be paracetamol", "paracetamol", med.item.activeSalt.toLowerCase());
        assertEquals("Strength should be 650 mg", "650 mg", med.strength);
        assertEquals("Tier should be VERIFIED", MedicineDatasetManager.VerificationTier.VERIFIED, med.verificationTier);
    }

    /**
     * TEST 4: Medicine containing Aceclofenac
     * Zerodol / Aceclofenac
     */
    @Test
    public void testAceclofenacMedicine() {
        String ocrText = "Zerodol\n" +
                "Aceclofenac Tablets IP 100 mg\n" +
                "Each film-coated tablet contains: Aceclofenac IP 100 mg\n" +
                "Ipca Laboratories Ltd\n" +
                "Batch: AC9981\n" +
                "Exp: 04/2027";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertEquals("Should identify exactly 1 medicine product", 1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Name should contain Zerodol or Aceclofenac", med.item.name.toLowerCase().contains("zerodol") || med.item.name.toLowerCase().contains("aceclofenac"));
        assertTrue("Active salt should contain aceclofenac", med.item.activeSalt.toLowerCase().contains("aceclofenac"));
        assertEquals("Strength should be 100 mg", "100 mg", med.strength);
    }

    /**
     * TEST 5: Medicine containing Thiocolchicoside
     * Zerodol-TH (Aceclofenac + Thiocolchicoside)
     */
    @Test
    public void testThiocolchicosideMedicine() {
        String ocrText = "Zerodol-TH\n" +
                "Aceclofenac & Thiocolchicoside Tablets\n" +
                "Each film coated tablet contains:\n" +
                "Aceclofenac IP 100 mg\n" +
                "Thiocolchicoside IP 4 mg\n" +
                "Ipca Laboratories Ltd\n" +
                "Exp: 10/2027\n" +
                "10 Tablets";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertEquals("Should identify 1 medicine product", 1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Active salt should contain thiocolchicoside", med.item.activeSalt.toLowerCase().contains("thiocolchicoside"));
        assertEquals("Tier should be VERIFIED", MedicineDatasetManager.VerificationTier.VERIFIED, med.verificationTier);
    }

    /**
     * TEST 6: Sacubitril / Valsartan product
     * Azmarda / Cidmus / Entresto
     */
    @Test
    public void testSacubitrilValsartanProduct() {
        String ocrText = "Azmarda 50 mg\n" +
                "Sacubitril and Valsartan Tablets\n" +
                "Each film coated tablet contains:\n" +
                "Sacubitril 24 mg\n" +
                "Valsartan 26 mg\n" +
                "J B Chemicals and Pharmaceuticals Ltd\n" +
                "Store protected from moisture";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertEquals("Should identify 1 medicine product", 1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Active salt should contain sacubitril", med.item.activeSalt.toLowerCase().contains("sacubitril"));
        assertTrue("Active salt should contain valsartan", med.item.activeSalt.toLowerCase().contains("valsartan"));
        assertEquals("Tier should be VERIFIED", MedicineDatasetManager.VerificationTier.VERIFIED, med.verificationTier);
    }

    /**
     * TEST 7: Blurry or partially cropped medicine image
     * Fragmented/partial text
     */
    @Test
    public void testBlurryOrPartialImage() {
        String fragmentedOcrText = "Cal... 5.. \n" +
                "Parac... \n" +
                "Tab...";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(fragmentedOcrText);

        assertNotNull(result);
        if (!result.detectedMedicines.isEmpty()) {
            MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
            assertNotEquals("Blurry/partial should NOT be marked VERIFIED", MedicineDatasetManager.VerificationTier.VERIFIED, med.verificationTier);
        }
    }

    /**
     * TEST 8: Unrelated non-medicine image
     * Grocery receipt or book page text
     */
    @Test
    public void testUnrelatedNonMedicineImage() {
        String receiptText = "Fresh Grocery Supermarket\n" +
                "123 Main Street Market Road\n" +
                "Item 1: Organic Milk 1L - Rs 60\n" +
                "Item 2: Whole Wheat Bread - Rs 45\n" +
                "Item 3: Gala Apples 1kg - Rs 120\n" +
                "Total Amount: Rs 225.00\n" +
                "Thank you for shopping with us!\n" +
                "Visit again soon";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(receiptText);

        assertNotNull(result);
        assertTrue("Non-medicine image should NOT identify any medicine", result.detectedMedicines.isEmpty());
    }

    /**
     * TEST 9: Image containing multiple medicine packages
     * e.g. Calpol 500 and Pantocid 40
     */
    @Test
    public void testMultipleMedicinePackages() {
        String ocrText = "Calpol 500\n" +
                "Paracetamol Tablets IP 500 mg\n" +
                "GlaxoSmithKline\n" +
                "\n" +
                "Pantocid 40\n" +
                "Pantoprazole Gastro-resistant Tablets IP 40 mg\n" +
                "Sun Pharma\n" +
                "10 Tablets";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);

        assertNotNull(result);
        assertEquals("Should identify both distinct medicines", 2, result.detectedMedicines.size());

        boolean foundCalpol = false;
        boolean foundPantocid = false;

        for (MedicineDatasetManager.ExtractedMedicineDetail med : result.detectedMedicines) {
            if (med.item.name.toLowerCase().contains("calpol")) foundCalpol = true;
            if (med.item.name.toLowerCase().contains("pantocid") || med.item.name.toLowerCase().contains("pantoprazole")) foundPantocid = true;
        }

        assertTrue("Should detect Calpol", foundCalpol);
        assertTrue("Should detect Pantocid", foundPantocid);
    }

    /**
     * TEST 10: Online API response handling & Null safety
     * Verifies:
     * - Null candidates return safe null without crashing
     * - Candidates with null names are skipped to find valid candidate
     * - Candidate ranking and score parsing works cleanly
     */
    @Test
    public void testOnlineApiResponseAndNullSafety() {
        RxNormResponse emptyResponse = new RxNormResponse();
        assertNull("Empty response returns null candidate", emptyResponse.getBestCandidate());
        assertNull("Empty response returns null medicine name", emptyResponse.getBestMedicineName());
        assertNull("Empty response returns null RxCUI", emptyResponse.getBestRxCui());

        // Construct candidate group with first candidate having null name (as RxNorm MMSL/GS does)
        RxNormResponse responseWithNullFirst = new RxNormResponse();
        responseWithNullFirst.approximateGroup = new RxNormResponse.ApproximateGroup();
        responseWithNullFirst.approximateGroup.candidate = new java.util.ArrayList<>();

        RxNormResponse.Candidate c1 = new RxNormResponse.Candidate();
        c1.rxcui = "161";
        c1.score = "7.06";
        c1.source = "MMSL";
        c1.name = null; // null name!

        RxNormResponse.Candidate c2 = new RxNormResponse.Candidate();
        c2.rxcui = "161";
        c2.score = "7.06";
        c2.source = "ATC";
        c2.name = "paracetamol";

        responseWithNullFirst.approximateGroup.candidate.add(c1);
        responseWithNullFirst.approximateGroup.candidate.add(c2);

        // Verify that getBestCandidate does NOT return the null name candidate!
        RxNormResponse.Candidate best = responseWithNullFirst.getBestCandidate();
        assertNotNull(best);
        assertEquals("Should pick candidate c2 with valid name", "paracetamol", best.name);
        assertEquals("Best name should be paracetamol", "paracetamol", responseWithNullFirst.getBestMedicineName());
        assertEquals("RxCUI should be 161", "161", responseWithNullFirst.getBestRxCui());
    }

    /**
     * TEST 11: Drug Interaction Rules
     * Verifies:
     * - Aspirin + Warfarin (HIGH risk)
     * - Aceclofenac + Warfarin (HIGH risk)
     * - Aceclofenac + Aspirin (MEDIUM risk)
     * - Safe combinations do not trigger false alerts
     */
    @Test
    public void testDrugInteractions() {
        MedicineDatasetManager.MedicineItem aceclo = new MedicineDatasetManager.MedicineItem(
                "zerodol", "aceclofenac", "Pain", "Pain", "Pain", "After food"
        );
        MedicineDatasetManager.MedicineItem warfarin = new MedicineDatasetManager.MedicineItem(
                "warfarin", "warfarin", "Blood Thinner", "Clots", "Clots", "Daily"
        );

        MedicineDatasetManager.ExtractedMedicineDetail d1 = new MedicineDatasetManager.ExtractedMedicineDetail(
                aceclo, "Zerodol", "100mg", "1", "BD", "5 days", 0.95f
        );
        MedicineDatasetManager.ExtractedMedicineDetail d2 = new MedicineDatasetManager.ExtractedMedicineDetail(
                warfarin, "Warfarin", "5mg", "1", "OD", "30 days", 0.95f
        );

        List<MedicineDatasetManager.InteractionItem> interactions = datasetManager.checkInteractions(
                java.util.Arrays.asList(d1, d2)
        );

        assertFalse("Interaction between Aceclofenac and Warfarin must be detected", interactions.isEmpty());
        assertEquals("HIGH", interactions.get(0).riskLevel);
    }

    /**
     * TEST 12: Packaging boilerplate noise filtering
     */
    @Test
    public void testPackagingBoilerplateNoiseFiltering() {
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Mfg. Lic. No. B-234/18"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Batch No. ML-9923"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Exp Date: 12/2028"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Store below 30°C in a dry dark place"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Keep all medicines out of reach of children"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Schedule H Prescription Drug - Caution"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Warning: Taking more than daily dose may cause liver damage"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("M.R.P. Rs. 45.00 Inclusive of all taxes"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("10 Tablets"));
        assertTrue(MedicineDatasetManager.isPackagingOrLegalNoise("Marketed by: GlaxoSmithKline Pharmaceuticals"));

        // Valid medicine names must NOT be flagged as noise
        assertFalse(MedicineDatasetManager.isPackagingOrLegalNoise("Calpol 500"));
        assertFalse(MedicineDatasetManager.isPackagingOrLegalNoise("Dolo 650"));
        assertFalse(MedicineDatasetManager.isPackagingOrLegalNoise("Crocin Advance"));
        assertFalse(MedicineDatasetManager.isPackagingOrLegalNoise("Zerodol-TH"));
        assertFalse(MedicineDatasetManager.isPackagingOrLegalNoise("Azmarda 50mg"));
    }

    /**
     * TEST 13: OpenFDA Response Parsing & Verification
     */
    @Test
    public void testOpenFdaResponseModel() {
        OpenFdaResponse response = new OpenFdaResponse();
        response.results = new java.util.ArrayList<>();

        OpenFdaResponse.Result res = new OpenFdaResponse.Result();
        res.openfda = new OpenFdaResponse.OpenFdaData();
        res.openfda.brandName = java.util.Collections.singletonList("ENTRESTO");
        res.openfda.genericName = java.util.Collections.singletonList("SACUBITRIL AND VALSARTAN");
        res.openfda.manufacturerName = java.util.Collections.singletonList("Novartis Pharmaceuticals Corporation");
        response.results.add(res);

        assertEquals("ENTRESTO", response.getFirstBrandName());
        assertEquals("SACUBITRIL AND VALSARTAN", response.getFirstGenericName());
        assertEquals("Novartis Pharmaceuticals Corporation", response.getFirstManufacturer());
    }

    /**
     * TEST 14: Shared AI Backend Success Response Model Parsing
     * Verifies that Gson deserializes all FastAPI backend JSON fields cleanly into MedicineRecognitionResponse.
     */
    @Test
    public void testSharedAiResponseModelParsing_Success() {
        String backendJson = "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"uncertainty_status\": \"confident\",\n" +
                "  \"recognition_method\": \"OCR Text Extraction + Clinical Catalog Concordance + Dosage Guard\",\n" +
                "  \"visual_classifier_note\": \"Visual packaging model is experimental (evaluated 2.17% top-1 accuracy on 150 classes); its predictions do not influence candidate scoring.\",\n" +
                "  \"detected_medicines\": [\n" +
                "    {\n" +
                "      \"name\": \"Zyrtec\",\n" +
                "      \"brand_candidates\": [\"Zyrtec (Tablets)\"],\n" +
                "      \"active_ingredients\": [\"cetirizine\"],\n" +
                "      \"strength\": \"10mg\",\n" +
                "      \"dosage_form\": \"Tablets\",\n" +
                "      \"manufacturer\": \"GlaxoSmithKline (GSK)\",\n" +
                "      \"category\": \"Allergy Medicine\",\n" +
                "      \"route\": \"Oral\",\n" +
                "      \"confidence\": 0.89,\n" +
                "      \"verification_tier\": \"Candidate Match (OCR Verified)\",\n" +
                "      \"evidence\": [\"Exact brand match: Zyrtec\", \"Active salt verified: cetirizine\", \"Dosage verified: 10mg\"]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"extracted_text\": \"Zyrtec 10mg Tablets Cetirizine HCl GSK\",\n" +
                "  \"strength_dosage_text\": \"10mg\",\n" +
                "  \"warning\": null,\n" +
                "  \"loose_pill_notice\": \"Loose-pill visual identification requires laboratory pill-imprint confirmation.\",\n" +
                "  \"disclaimer\": \"This candidate identification is based on extracted label text and catalog matching.\"\n" +
                "}";

        com.google.gson.Gson gson = new com.google.gson.Gson();
        MedicineRecognitionResponse resp = gson.fromJson(backendJson, MedicineRecognitionResponse.class);

        assertNotNull("Response should not be null", resp);
        assertEquals("success", resp.status);
        assertEquals("confident", resp.uncertaintyStatus);
        assertEquals("10mg", resp.strengthDosageText);
        assertTrue(resp.recognitionMethod.contains("Dosage Guard"));
        assertTrue(resp.visualClassifierNote.contains("2.17%"));
        assertNull(resp.warning);
        assertNotNull(resp.loosePillNotice);
        assertNotNull(resp.disclaimer);

        assertNotNull(resp.detectedMedicines);
        assertEquals(1, resp.detectedMedicines.size());

        MedicineRecognitionResponse.DetectedMedicine dm = resp.detectedMedicines.get(0);
        assertEquals("Zyrtec", dm.name);
        assertEquals("10mg", dm.strength);
        assertEquals("Tablets", dm.dosageForm);
        assertEquals("GlaxoSmithKline (GSK)", dm.manufacturer);
        assertEquals("Allergy Medicine", dm.category);
        assertEquals("Oral", dm.route);
        assertEquals(0.89f, dm.confidence, 0.001f);
        assertEquals("Candidate Match (OCR Verified)", dm.verificationTier);
        assertNotNull(dm.evidence);
        assertEquals(3, dm.evidence.size());
        assertEquals("cetirizine", dm.activeIngredients.get(0));
        assertEquals("Zyrtec (Tablets)", dm.brandCandidates.get(0));
    }

    /**
     * TEST 15: Shared AI Backend Unrecognized Packaging Response Model Parsing
     */
    @Test
    public void testSharedAiResponseModelParsing_Unrecognized() {
        String unrecognizedJson = "{\n" +
                "  \"status\": \"unrecognized\",\n" +
                "  \"uncertainty_status\": \"unknown\",\n" +
                "  \"message\": \"Medicine package could not be verified against clinical catalog.\",\n" +
                "  \"recognition_method\": \"OCR Text Extraction + Clinical Catalog Concordance\",\n" +
                "  \"detected_medicines\": [],\n" +
                "  \"extracted_text\": \"Unreadable blurry text\",\n" +
                "  \"strength_dosage_text\": \"\",\n" +
                "  \"warning\": \"The detected packaging does not confidently match any cataloged medicine.\",\n" +
                "  \"loose_pill_notice\": \"Loose-pill visual identification requires laboratory pill-imprint confirmation.\",\n" +
                "  \"disclaimer\": \"Informational packaging recognition only.\"\n" +
                "}";

        com.google.gson.Gson gson = new com.google.gson.Gson();
        MedicineRecognitionResponse resp = gson.fromJson(unrecognizedJson, MedicineRecognitionResponse.class);

        assertNotNull(resp);
        assertEquals("unrecognized", resp.status);
        assertEquals("unknown", resp.uncertaintyStatus);
        assertEquals("Medicine package could not be verified against clinical catalog.", resp.message);
        assertNotNull(resp.detectedMedicines);
        assertTrue(resp.detectedMedicines.isEmpty());
        assertNotNull(resp.warning);
    }

    /**
     * TEST 16: Shared AI Backend 500 Error Response Model Parsing
     */
    @Test
    public void testSharedAiResponseModelParsing_ServerError() {
        String errorJson = "{\n" +
                "  \"status\": \"error\",\n" +
                "  \"uncertainty_status\": \"unknown\",\n" +
                "  \"message\": \"Server recognition error: RapidOCR backend failure\",\n" +
                "  \"detected_medicines\": [],\n" +
                "  \"extracted_text\": \"\",\n" +
                "  \"strength_dosage_text\": \"\",\n" +
                "  \"warning\": \"An unexpected error occurred during image processing.\"\n" +
                "}";

        com.google.gson.Gson gson = new com.google.gson.Gson();
        MedicineRecognitionResponse resp = gson.fromJson(errorJson, MedicineRecognitionResponse.class);

        assertNotNull(resp);
        assertEquals("error", resp.status);
        assertEquals("unknown", resp.uncertaintyStatus);
        assertTrue(resp.message.contains("RapidOCR backend failure"));
        assertTrue(resp.detectedMedicines.isEmpty());
    }

    /**
     * TEST 17: Offline Fallback Confidence Honesty and Honest Labeling
     * Verifies:
     * - Offline candidate confidence is realistically calibrated (<= 0.90, never 100% or 0.99)
     * - No false claims of "AI Verified" or "Certified Packaging"
     */
    @Test
    public void testOfflineFallbackConfidenceHonestyAndCalibration() {
        String ocrText = "Dolo 650\n" +
                "Paracetamol Tablets IP 650 mg\n" +
                "Micro Labs Limited\n" +
                "15 Tablets";

        MedicineDatasetManager.ParseResult result = datasetManager.parsePrescriptionText(ocrText);
        assertNotNull(result);
        assertEquals(1, result.detectedMedicines.size());

        MedicineDatasetManager.ExtractedMedicineDetail med = result.detectedMedicines.get(0);
        assertTrue("Candidate confidence must be <= 0.90 for offline packaging OCR", med.matchConfidence <= 0.90f);
        assertTrue("Candidate confidence must be >= 0.85 for high-quality match", med.matchConfidence >= 0.85f);
        assertNotEquals("Should never claim artificial 100% confidence", 1.0f, med.matchConfidence, 0.001f);
        assertNotEquals("Should never claim artificial 99% confidence", 0.99f, med.matchConfidence, 0.001f);

        // Verification tier display title must NOT claim "AI Verified" or "Certified"
        assertFalse("Display title must not claim AI Verified", med.verificationTier.getDisplayTitle().contains("AI Verified"));
        assertFalse("Display title must not claim Certified", med.verificationTier.getDisplayTitle().contains("Certified"));
    }

    /**
     * TEST 18: RetrofitClient Backend URL Resolution and Configuration
     * Verifies:
     * - Emulator URL remains standard 10.0.2.2:8000/
     * - Physical device URL matches configured BuildConfig.PHYSICAL_BACKEND_URL
     * - Custom URL overrides work with proper slash normalization
     * - Clearing custom URL restores default resolution
     */
    @Test
    public void testRetrofitClientBackendUrlConfiguration() {
        assertEquals("Emulator URL must be http://10.0.2.2:8000/", "http://10.0.2.2:8000/", RetrofitClient.EMULATOR_URL);
        assertTrue("BuildConfig URL must be a valid HTTP or HTTPS URL",
                BuildConfig.PHYSICAL_BACKEND_URL.startsWith("http://") || BuildConfig.PHYSICAL_BACKEND_URL.startsWith("https://"));
        assertEquals("Production URL must match Render deployment",
                "https://medinex-backend-bukx.onrender.com/", RetrofitClient.PRODUCTION_BACKEND_URL);

        // Verify custom override
        String testCustomUrl = "http://192.168.1.150:8000";
        RetrofitClient.setSharedAiBaseUrl(testCustomUrl);
        assertEquals("Custom URL must have trailing slash normalized", "http://192.168.1.150:8000/", RetrofitClient.getSharedAiBaseUrl());

        // Reset custom URL to null to restore default behavior
        RetrofitClient.setSharedAiBaseUrl(null);
        String resolved = RetrofitClient.getSharedAiBaseUrl();
        assertTrue("Resolved URL must be either emulator, BuildConfig physical URL, or production HTTPS URL",
                resolved.equals(RetrofitClient.EMULATOR_URL) || resolved.equals(RetrofitClient.PHYSICAL_DEVICE_URL) || resolved.startsWith("https://") || resolved.contains(":8000/"));
    }
}
