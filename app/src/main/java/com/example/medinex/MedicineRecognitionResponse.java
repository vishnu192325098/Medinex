package com.example.medinex;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MedicineRecognitionResponse {

    @SerializedName("status")
    public String status;

    @SerializedName("uncertainty_status")
    public String uncertaintyStatus;

    @SerializedName("message")
    public String message;

    @SerializedName("detected_medicines")
    public List<DetectedMedicine> detectedMedicines;

    @SerializedName("extracted_text")
    public String extractedText;

    @SerializedName("strength_dosage_text")
    public String strengthDosageText;

    @SerializedName("recognition_method")
    public String recognitionMethod;

    @SerializedName("visual_classifier_note")
    public String visualClassifierNote;

    @SerializedName("warning")
    public String warning;

    @SerializedName("loose_pill_notice")
    public String loosePillNotice;

    @SerializedName("disclaimer")
    public String disclaimer;

    public static class DetectedMedicine {
        @SerializedName("name")
        public String name;

        @SerializedName("brand_candidates")
        public List<String> brandCandidates;

        @SerializedName("active_ingredients")
        public List<String> activeIngredients;

        @SerializedName("strength")
        public String strength;

        @SerializedName("dosage_form")
        public String dosageForm;

        @SerializedName("manufacturer")
        public String manufacturer;

        @SerializedName("category")
        public String category;

        @SerializedName("route")
        public String route;

        @SerializedName("confidence")
        public float confidence;

        @SerializedName("verification_tier")
        public String verificationTier;

        @SerializedName("evidence")
        public List<String> evidence;
    }
}
