package com.example.medinex;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class UploadActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;

    Button selectImageBtn, scanBtn;
    ImageView prescriptionImage;
    TextView resultText;

    Uri imageUri;
    Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        selectImageBtn = findViewById(R.id.selectImageBtn);
        scanBtn = findViewById(R.id.scanBtn);
        prescriptionImage = findViewById(R.id.prescriptionImage);
        resultText = findViewById(R.id.resultText);

        selectImageBtn.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);

        });

        scanBtn.setOnClickListener(v -> {

            if (imageUri == null) {

                Toast.makeText(
                        UploadActivity.this,
                        "Please select an image first",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            try {

                InputImage image =
                        InputImage.fromBitmap(bitmap, 0);

                TextRecognition.getClient(
                                TextRecognizerOptions.DEFAULT_OPTIONS)
                        .process(image)

                        .addOnSuccessListener(visionText -> {

                            String detectedText =
                                    visionText.getText().toLowerCase();

                            String result =
                                    "📋 MEDINEX PRESCRIPTION ANALYSIS\n\n";

                            int medicineCount = 0;
                            boolean duplicateDetected = false;

                            // Duplicate Detection

                            if ((detectedText.contains("dolo") &&
                                    detectedText.contains("paracetamol"))
                                    ||
                                    (detectedText.contains("crocin") &&
                                            detectedText.contains("paracetamol"))) {

                                duplicateDetected = true;

                                result +=
                                        "⚠ DUPLICATE MEDICINE ALERT\n\n" +
                                                "• Dolo / Crocin\n" +
                                                "• Paracetamol\n\n" +
                                                "Reason: Same active ingredient\n\n";
                            }

                            // Fever & Pain

                            if (detectedText.contains("paracetamol")
                                    || detectedText.contains("dolo")
                                    || detectedText.contains("crocin")
                                    || detectedText.contains("calpol")
                                    || detectedText.contains("ibuprofen")
                                    || detectedText.contains("combiflam")
                                    || detectedText.contains("zerodol")
                                    || detectedText.contains("diclofenac")
                                    || detectedText.contains("aceclofenac")
                                    || detectedText.contains("naproxen")) {

                                medicineCount++;

                                result +=
                                        "💊 Fever & Pain Relief\n" +
                                                "• Fever\n" +
                                                "• Headache\n" +
                                                "• Body Pain\n\n";
                            }

                            // Allergy

                            if (detectedText.contains("cetirizine")
                                    || detectedText.contains("levocetirizine")
                                    || detectedText.contains("montek")
                                    || detectedText.contains("fexofenadine")
                                    || detectedText.contains("montair")
                                    || detectedText.contains("montek lc")
                                    || detectedText.contains("allegra")) {

                                medicineCount++;

                                result +=
                                        "🤧 Allergy Medicine\n" +
                                                "• Sneezing\n" +
                                                "• Runny Nose\n" +
                                                "• Skin Allergy\n\n";
                            }

                            // Antibiotics

                            if (detectedText.contains("azithromycin")
                                    || detectedText.contains("amoxicillin")
                                    || detectedText.contains("augmentin")
                                    || detectedText.contains("cefixime")
                                    || detectedText.contains("monocef")
                                    || detectedText.contains("ofloxacin")
                                    || detectedText.contains("ciprofloxacin")) {

                                medicineCount++;

                                result +=
                                        "🦠 Antibiotic Medicine\n" +
                                                "• Infection Control\n" +
                                                "• Bacterial Treatment\n\n";
                            }

                            // Diabetes

                            if (detectedText.contains("metformin")
                                    || detectedText.contains("glycomet")
                                    || detectedText.contains("glimepiride")) {

                                medicineCount++;

                                result +=
                                        "🩸 Diabetes Medicine\n" +
                                                "• Blood Sugar Control\n\n";
                            }

                            // Stomach Care

                            if (detectedText.contains("pantoprazole")
                                    || detectedText.contains("pantocid")
                                    || detectedText.contains("omeprazole")
                                    || detectedText.contains("rabeprazole")
                                    || detectedText.contains("pan-d")
                                    || detectedText.contains("rantac")
                                    || detectedText.contains("ranitidine")) {

                                medicineCount++;

                                result +=
                                        "🫄 Stomach Care Medicine\n" +
                                                "• Acidity\n" +
                                                "• Gastric Problems\n\n";
                            }
                            // Medicine Interaction Checker

                            boolean interactionDetected = false;

                            if (detectedText.contains("azithromycin")
                                    && detectedText.contains("levocetirizine")) {

                                interactionDetected = true;

                                result +=
                                        "⚠ MEDICINE INTERACTION CHECK\n\n" +

                                                "Medicines:\n" +
                                                "• Azithromycin\n" +
                                                "• Levocetirizine\n\n" +

                                                "Interaction Level:\n" +
                                                "MODERATE\n\n" +

                                                "Possible Effects:\n" +
                                                "• Drowsiness\n" +
                                                "• Dizziness\n\n" +

                                                "Recommendation:\n" +
                                                "Avoid driving after taking both medicines.\n\n";
                            }

                            // No category found
                            // Blood Pressure Medicines

                            if (detectedText.contains("amlodipine")
                                    || detectedText.contains("telma")
                                    || detectedText.contains("telmisartan")
                                    || detectedText.contains("losartan")) {

                                medicineCount++;

                                result +=
                                        "❤️ Blood Pressure Medicine\n" +
                                                "• Hypertension Control\n" +
                                                "• Heart Protection\n\n";
                            }
                            // Cholesterol Medicines

                            if (detectedText.contains("atorvastatin")
                                    || detectedText.contains("rosuvastatin")) {

                                medicineCount++;

                                result +=
                                        "🫀 Cholesterol Medicine\n" +
                                                "• Cholesterol Control\n" +
                                                "• Heart Health Support\n\n";
                            }

                            if (medicineCount == 0) {

                                result +=
                                        "⚠ No medicine category identified.\n\n" +
                                                "Please consult a healthcare professional.\n\n";
                            }

                            result +=
                                    "\n📊 PRESCRIPTION SUMMARY\n\n" +
                                            "Medicine Categories Detected: " +
                                            medicineCount + "\n";

                            if (duplicateDetected || interactionDetected) {

                                result +=
                                        "Duplicate Medicines: " +
                                                (duplicateDetected ? "YES" : "NO") + "\n" +

                                                "Interaction Alert: " +
                                                (interactionDetected ? "YES" : "NO") + "\n" +

                                                "Risk Level: MEDIUM\n";
                            }
                            else {
                                result += "\n🚨 EMERGENCY RISK ASSESSMENT\n\n";

                                if (duplicateDetected && interactionDetected) {

                                    result +=
                                            "Risk Status: HIGH RISK\n\n" +

                                                    "Reason:\n" +
                                                    "• Duplicate Medicine Detected\n" +
                                                    "• Drug Interaction Detected\n\n" +

                                                    "Recommendation:\n" +
                                                    "Consult doctor immediately before taking medicines.\n";
                                }
                                else if (duplicateDetected || interactionDetected) {

                                    result +=
                                            "Risk Status: MODERATE RISK\n\n" +

                                                    "Reason:\n" +
                                                    "Potential medicine safety issue detected.\n\n" +

                                                    "Recommendation:\n" +
                                                    "Verify medicines with doctor or pharmacist.\n";
                                }
                                else {

                                    result +=
                                            "Risk Status: LOW RISK\n\n" +

                                                    "Recommendation:\n" +
                                                    "No major medicine safety concerns detected.\n";
                                }

                                result +=
                                        "Duplicate Medicines: NO\n" +
                                                "Interaction Alert: NO\n" +
                                                "Risk Level: LOW\n";
                            }
                            result +=
                                    "\n🕒 SMART REMINDER SUGGESTIONS\n\n";

                            if (detectedText.contains("pantocid")
                                    || detectedText.contains("pantoprazole")) {

                                result +=
                                        "Pantocid → Before Breakfast\n";
                            }

                            if (detectedText.contains("levocetirizine")
                                    || detectedText.contains("cetirizine")) {

                                result +=
                                        "Levocetirizine → Night Time\n";
                            }

                            if (detectedText.contains("azithromycin")) {

                                result +=
                                        "Azithromycin → After Food\n";
                            }

                            if (detectedText.contains("paracetamol")
                                    || detectedText.contains("dolo")
                                    || detectedText.contains("crocin")) {

                                result +=
                                        "Paracetamol → Morning & Night\n";
                            }

                            result += "\n\n✅ AI Analysis Complete";

                            resultText.setText(result);

                        })

                        .addOnFailureListener(e ->
                                resultText.setText(
                                        "Error: " + e.getMessage()));

            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data);

        if (requestCode == PICK_IMAGE
                && resultCode == RESULT_OK
                && data != null) {

            imageUri = data.getData();

            try {

                bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(),
                        imageUri);

                prescriptionImage.setImageBitmap(bitmap);

            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }
}