
package com.example.medinex;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UploadActivity extends AppCompatActivity {

    private static final String TAG = "UploadActivity";
    private static final int PICK_IMAGE = 1;
    private static final int TAKE_PHOTO = 2;
    private static final int CAMERA_PERMISSION_CODE = 100;

    private Button selectImageBtn;
    private Button scanBtn;
    private Button viewHistoryBtn;
    private ImageView prescriptionImage;
    private TextView resultText;

    private Uri imageUri;
    private Bitmap bitmap;
    private OcrImagePreprocessor.ImageQualityResult lastQualityResult;
    private MedicineDatasetManager.ParseResult lastParseResult;
    private FirebaseDataManager dataManager;
    private String lastFallbackReason = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        selectImageBtn = findViewById(R.id.selectImageBtn);
        scanBtn = findViewById(R.id.scanBtn);
        viewHistoryBtn = findViewById(R.id.viewHistoryBtn);
        prescriptionImage = findViewById(R.id.prescriptionImage);
        resultText = findViewById(R.id.resultText);

        dataManager = new FirebaseDataManager();

        selectImageBtn.setOnClickListener(v -> showImageSourceDialog());
        scanBtn.setOnClickListener(v -> scanPrescription());

        if (viewHistoryBtn != null) {
            viewHistoryBtn.setOnClickListener(v -> {
                Intent intent = new Intent(UploadActivity.this, ScanHistoryActivity.class);
                startActivity(intent);
            });
        }
    }

    private void scanPrescription() {
        if (bitmap == null && imageUri == null) {
            Toast.makeText(this,
                    "Please select or capture a medicine image first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        resultText.setText("Connecting to Medinex Shared AI Recognition Engine...");

        try {
            Bitmap inputBitmap = bitmap;

            if (inputBitmap == null && imageUri != null) {
                inputBitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), imageUri
                );
            }

            if (inputBitmap == null) {
                resultText.setText("Unable to load the medicine image.");
                return;
            }

            final Bitmap targetBitmap = inputBitmap;

            // Standardize max dimension to 1024px for optimal network transfer and memory efficiency
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            int width = targetBitmap.getWidth();
            int height = targetBitmap.getHeight();
            float maxDim = 1024f;
            Bitmap toSend = targetBitmap;
            if (width > maxDim || height > maxDim) {
                float scale = Math.min(maxDim / width, maxDim / height);
                toSend = Bitmap.createScaledBitmap(targetBitmap, Math.round(width * scale), Math.round(height * scale), true);
            }
            toSend.compress(Bitmap.CompressFormat.JPEG, 85, stream);
            byte[] byteArray = stream.toByteArray();

            RequestBody reqBody = RequestBody.create(MediaType.parse("image/jpeg"), byteArray);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", "medicine.jpg", reqBody);

            String apiUrl = RetrofitClient.getSharedAiBaseUrl() + "api/medicine/recognize";
            Log.i(TAG, "[SCAN_DIAGNOSTIC] Initiating scan request to API URL: " + apiUrl
                    + " | isEmulator: " + RetrofitClient.isEmulator()
                    + " | imageBytes: " + byteArray.length
                    + " | dimensions: " + toSend.getWidth() + "x" + toSend.getHeight());

            resultText.setText("Analyzing image with Shared Packaging AI Engine...\nConnecting to " + RetrofitClient.getSharedAiBaseUrl());

            RetrofitClient.getRecognitionApi().recognizeMedicine(part).enqueue(new Callback<MedicineRecognitionResponse>() {
                @Override
                public void onResponse(Call<MedicineRecognitionResponse> call, Response<MedicineRecognitionResponse> response) {
                    Log.i(TAG, "[SCAN_DIAGNOSTIC] HTTP Status Code: " + response.code() + " from " + apiUrl);
                    if (response.isSuccessful() && response.body() != null) {
                        MedicineRecognitionResponse result = response.body();
                        Log.i(TAG, "[SCAN_DIAGNOSTIC] Shared AI Request SUCCESS (HTTP 200)");
                        try {
                            String rawJson = new com.google.gson.Gson().toJson(result);
                            Log.d(TAG, "[SCAN_DIAGNOSTIC] Raw response JSON: " + rawJson);
                        } catch (Exception ignored) {}
                        Log.i(TAG, "[SCAN_DIAGNOSTIC] Recognition method: " + result.recognitionMethod
                                + " | Uncertainty status: " + result.uncertaintyStatus);
                        if (result.detectedMedicines != null) {
                            for (MedicineRecognitionResponse.DetectedMedicine dm : result.detectedMedicines) {
                                Log.i(TAG, "[SCAN_DIAGNOSTIC] Server Candidate: " + dm.name
                                        + " | Strength: " + dm.strength
                                        + " | Confidence: " + dm.confidence
                                        + " | Verification tier: " + dm.verificationTier);
                            }
                        }
                        lastFallbackReason = null;
                        runOnUiThread(() -> {
                            renderSharedAiResult(result);
                            saveSharedScanHistory(result, targetBitmap);
                        });
                    } else {
                        String errorBodyStr = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBodyStr = response.errorBody().string();
                            }
                        } catch (Exception ignored) {}
                        Log.w(TAG, "[SCAN_DIAGNOSTIC] Shared AI Request FAILED with HTTP " + response.code()
                                + " | Raw error body: " + errorBodyStr);
                        Log.w(TAG, "[SCAN_DIAGNOSTIC] Fallback activation: Activating offline ML Kit engine due to server HTTP error " + response.code());
                        String reason = "Shared AI server returned HTTP " + response.code()
                                + (response.message() != null && !response.message().isEmpty() ? " (" + response.message() + ")" : "")
                                + ". Switched to offline ML Kit candidate engine.";
                        runOnUiThread(() -> runLocalOcrFallback(targetBitmap, reason));
                    }
                }

                @Override
                public void onFailure(Call<MedicineRecognitionResponse> call, Throwable t) {
                    String excMsg = t.getClass().getSimpleName() + ": " + t.getMessage();
                    Log.w(TAG, "[SCAN_DIAGNOSTIC] Shared AI Network Call FAILED: " + excMsg);
                    Log.w(TAG, "[SCAN_DIAGNOSTIC] Fallback activation: Activating offline ML Kit engine due to network failure: " + excMsg);
                    String reason = "Shared AI service unavailable (" + excMsg + ") at " + apiUrl
                            + ". Switched to offline ML Kit candidate engine.";
                    runOnUiThread(() -> runLocalOcrFallback(targetBitmap, reason));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error initiating scan", e);
            resultText.setText("Error preparing image for scan: " + e.getMessage());
        }
    }

    private void renderSharedAiResult(MedicineRecognitionResponse resp) {
        if (resp == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("MEDINEX SHARED AI MEDICINE RECOGNITION REPORT\n");
        sb.append("Engine: FastAPI + RapidOCR + Clinical Dosage Guard\n");
        if (resp.recognitionMethod != null && !resp.recognitionMethod.isEmpty()) {
            sb.append("Method: ").append(resp.recognitionMethod).append("\n");
        }
        if (resp.visualClassifierNote != null && !resp.visualClassifierNote.isEmpty()) {
            sb.append("Visual Model Note: ").append(resp.visualClassifierNote).append("\n");
        }
        sb.append("\n");

        // Uncertainty Status Badge
        String badge = "[STATUS: PROBABLE MATCH - Candidate Identified]";
        if ("uncertain".equalsIgnoreCase(resp.uncertaintyStatus)) {
            badge = "[STATUS: UNCERTAIN - Clinical Verification Advised]";
        } else if ("unknown".equalsIgnoreCase(resp.uncertaintyStatus)) {
            badge = "[STATUS: UNKNOWN - Text/Packaging Inconclusive]";
        }
        sb.append(badge).append("\n\n");

        // Strict Dosage Guard Status
        if (resp.strengthDosageText != null && !resp.strengthDosageText.isEmpty()) {
            sb.append("VERIFIED STRENGTH / DOSAGE: ").append(resp.strengthDosageText).append("\n");
            sb.append("Dosage Guard: Strict boundary protection active (no conversions or guesses)\n\n");
        }

        // Detected Medicines
        if (resp.detectedMedicines != null && !resp.detectedMedicines.isEmpty()) {
            sb.append("IDENTIFIED PACKAGING MEDICINES (").append(resp.detectedMedicines.size()).append(")\n\n");

            for (MedicineRecognitionResponse.DetectedMedicine dm : resp.detectedMedicines) {
                sb.append("------------------------------\n");
                sb.append("Product: ").append(safeText(dm.name)).append("\n");

                if (dm.brandCandidates != null && !dm.brandCandidates.isEmpty()) {
                    sb.append("Brand Candidates: ").append(String.join(", ", dm.brandCandidates)).append("\n");
                }
                if (dm.activeIngredients != null && !dm.activeIngredients.isEmpty()) {
                    sb.append("Active Salts: ").append(String.join(", ", dm.activeIngredients)).append("\n");
                }
                sb.append("Verified Strength: ").append(safeText(dm.strength)).append("\n");
                sb.append("Dosage Form: ").append(safeText(dm.dosageForm)).append("\n");
                sb.append("Manufacturer: ").append(dm.manufacturer != null && !dm.manufacturer.isEmpty() ? dm.manufacturer : "Not specified on package").append("\n");
                sb.append("Category: ").append(safeText(dm.category)).append("\n");
                sb.append("Route: ").append(safeText(dm.route)).append("\n");
                sb.append("Confidence: ").append(Math.round(dm.confidence * 100)).append("%\n");
                sb.append("Verification Tier: ").append(safeText(dm.verificationTier)).append("\n");

                if (dm.evidence != null && !dm.evidence.isEmpty()) {
                    sb.append("OCR Evidence: ").append(String.join(", ", dm.evidence)).append("\n");
                }
                sb.append("\n");
            }
        } else {
            sb.append("No catalog packaging matched with sufficient confidence.\n\n");
        }

        // Critical Warning / Discrepancy
        if (resp.warning != null && !resp.warning.isEmpty()) {
            sb.append("CRITICAL NOTICE / DISCREPANCY:\n");
            sb.append(resp.warning).append("\n\n");
        }

        // Strict Loose-Pill Isolation Notice
        sb.append("LOOSE-PILL ISOLATION NOTICE:\n");
        if (resp.loosePillNotice != null && !resp.loosePillNotice.isEmpty()) {
            sb.append(resp.loosePillNotice).append("\n\n");
        } else {
            sb.append("Packaging AI does NOT identify unboxed/loose pills. Always verify against original intact blister packs, bottles, or cartons.\n\n");
        }

        // Medical Disclaimer
        sb.append("Medical Disclaimer: ");
        if (resp.disclaimer != null && !resp.disclaimer.isEmpty()) {
            sb.append(resp.disclaimer).append("\n");
        } else {
            sb.append("Medinex AI recognition is for informational verification only. Always consult a licensed clinician or pharmacist before taking or dispensing medication.\n");
        }

        Log.i(TAG, "[SCAN_DIAGNOSTIC] Final displayed result (Shared AI):\n" + sb.toString());
        resultText.setText(sb.toString());
    }

    private void saveSharedScanHistory(MedicineRecognitionResponse resp, Bitmap originalBitmap) {
        if (resp == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user to save scan history");
            return;
        }

        final String uid = currentUser.getUid();
        final String scanId = UUID.randomUUID().toString();

        ArrayList<Map<String, Object>> medicines = new ArrayList<>();
        if (resp.detectedMedicines != null) {
            for (MedicineRecognitionResponse.DetectedMedicine dm : resp.detectedMedicines) {
                Map<String, Object> med = new HashMap<>();
                med.put("name", dm.name != null ? dm.name : "Unidentified");
                med.put("activeIngredient", (dm.activeIngredients != null && !dm.activeIngredients.isEmpty())
                        ? String.join(", ", dm.activeIngredients) : "Unknown");
                med.put("strength", dm.strength != null ? dm.strength : "");
                med.put("dosageForm", dm.dosageForm != null ? dm.dosageForm : "");
                med.put("manufacturer", dm.manufacturer != null ? dm.manufacturer : "Not specified on package");
                med.put("category", dm.category != null ? dm.category : "");
                med.put("matchConfidence", dm.confidence);
                med.put("verificationTier", dm.verificationTier != null ? dm.verificationTier : "shared_packaging_ai");
                med.put("dataSource", "Medinex Shared AI (Mendeley Packaging)");
                medicines.add(med);
            }
        }

        final String storagePath = "users/" + uid + "/prescriptions/" + scanId + "/original.jpg";

        Map<String, Object> scan = new HashMap<>();
        scan.put("scanId", scanId);
        scan.put("medicines", medicines);
        scan.put("medicineCount", medicines.size());
        scan.put("status", medicines.isEmpty() ? "no_medicine_identified" : "completed");
        scan.put("uncertaintyStatus", resp.uncertaintyStatus != null ? resp.uncertaintyStatus : "uncertain");
        scan.put("rawOcrText", resp.extractedText != null ? resp.extractedText : "");
        scan.put("strengthDosageText", resp.strengthDosageText != null ? resp.strengthDosageText : "");
        scan.put("storagePath", storagePath);
        scan.put("imageUploadStatus", originalBitmap != null ? "pending" : "no_image");
        scan.put("source", "shared_ai_backend");
        scan.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .document(uid)
                .collection("prescriptions")
                .document(scanId)
                .set(scan, SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "Error saving shared scan to prescriptions", e));

        db.collection("users")
                .document(uid)
                .collection("scanHistory")
                .document(scanId)
                .set(scan, SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "Error saving shared scan to scanHistory", e));

        uploadPrescriptionImageToStorage(uid, scanId, storagePath);
    }

    private void runLocalOcrFallback(Bitmap inputBitmap, String reason) {
        if (inputBitmap == null) return;

        lastFallbackReason = reason;
        Log.i(TAG, "[SCAN_DIAGNOSTIC] Offline fallback activated: " + reason);
        resultText.setText(reason + "\n\nProcessing with offline ML Kit engine...");

        try {
            lastQualityResult = OcrImagePreprocessor.assessImageQuality(inputBitmap);
            Bitmap preprocessedBitmap = OcrImagePreprocessor.preprocessForOcr(this, inputBitmap, imageUri);
            Bitmap bitmapForOcr = preprocessedBitmap != null ? preprocessedBitmap : inputBitmap;

            InputImage image = InputImage.fromBitmap(bitmapForOcr, 0);

            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(visionText -> {
                        Log.i(TAG, "[SCAN_DIAGNOSTIC] Offline ML Kit OCR completed | Text length: "
                                + (visionText.getText() != null ? visionText.getText().length() : 0));
                        analyzePrescription(visionText.getText());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "[SCAN_DIAGNOSTIC] Offline OCR failed: " + e.getMessage(), e);
                        resultText.setText("Error in offline OCR fallback: " + e.getMessage() + "\n\nInitial failure: " + reason);
                    });

        } catch (Exception e) {
            Log.e(TAG, "[SCAN_DIAGNOSTIC] Error in local OCR fallback", e);
            resultText.setText("Offline fallback processing error: " + e.getMessage());
        }
    }

    private void showImageSourceDialog() {
        String[] options = {
                "Choose from Gallery / Files",
                "Take Photo with Camera"
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Medicine Image Source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openGallery();
                    } else {
                        requestCamera();
                    }
                })
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*"
        );
        startActivityForResult(intent, PICK_IMAGE);
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent cameraIntent =
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, TAKE_PHOTO);
        } else {
            Toast.makeText(this,
                    "Camera app not available",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults
        );

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this,
                        "Camera permission denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_IMAGE) {
            imageUri = data.getData();
            if (imageUri == null) return;

            try {
                bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), imageUri
                );

                prescriptionImage.setImageBitmap(bitmap);
                lastQualityResult =
                        OcrImagePreprocessor.assessImageQuality(bitmap);

                resultText.setText(
                        "Medicine image selected. Tap Analyze to scan."
                );

            } catch (IOException e) {
                Log.e(TAG, "Unable to load image", e);
                Toast.makeText(this,
                        "Unable to load selected image",
                        Toast.LENGTH_SHORT).show();
            }

        } else if (requestCode == TAKE_PHOTO) {
            Bundle extras = data.getExtras();

            if (extras != null && extras.get("data") instanceof Bitmap) {
                bitmap = (Bitmap) extras.get("data");
                imageUri = null;

                prescriptionImage.setImageBitmap(bitmap);
                lastQualityResult =
                        OcrImagePreprocessor.assessImageQuality(bitmap);

                resultText.setText(
                        "Medicine photo captured. Tap Analyze to scan."
                );
            }
        }
    }

    private void analyzePrescription(String rawOcrText) {
        MedicineDatasetManager datasetManager =
                MedicineDatasetManager.getInstance(this);

        lastParseResult =
                datasetManager.parsePrescriptionText(rawOcrText);

        renderPrescriptionAnalysis();
        saveScanHistory(rawOcrText);

        Set<String> searchedQueries = new HashSet<>();

        for (MedicineDatasetManager.ExtractedMedicineDetail detail
                : lastParseResult.detectedMedicines) {

            if (detail != null && detail.item != null
                    && detail.item.name != null) {

                String term = detail.item.name.trim();

                if (!term.isEmpty()
                        && searchedQueries.add(
                        term.toLowerCase(Locale.ROOT))) {
                    searchMedicineOnline(detail);
                }
            }
        }
    }

    private void saveScanHistory(String rawOcrText) {
        if (lastParseResult == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user to save scan history");
            return;
        }

        final String uid = currentUser.getUid();
        final String scanId = UUID.randomUUID().toString();

        ArrayList<Map<String, Object>> medicines = new ArrayList<>();

        for (MedicineDatasetManager.ExtractedMedicineDetail detail
                : lastParseResult.detectedMedicines) {

            if (detail == null || detail.item == null) continue;

            Map<String, Object> medicine = new HashMap<>();
            medicine.put("name", detail.item.name);
            medicine.put("activeIngredient", detail.item.activeSalt);
            medicine.put("strength", detail.strength);
            medicine.put("dosageForm", detail.dosageForm);
            medicine.put("matchConfidence", detail.matchConfidence);
            medicine.put("dataSource", detail.dataSource);

            medicines.add(medicine);
        }

        final String storagePath = "users/" + uid + "/prescriptions/" + scanId + "/original.jpg";

        Map<String, Object> scan = new HashMap<>();
        scan.put("scanId", scanId);
        scan.put("medicines", medicines);
        scan.put("medicineCount", medicines.size());
        scan.put("status", medicines.isEmpty()
                ? "no_medicine_identified" : "completed");
        scan.put("uncertaintyStatus", "uncertain");
        scan.put("source", "offline_mlkit_fallback");
        scan.put("rawOcrText", rawOcrText);
        scan.put("storagePath", storagePath);
        scan.put("imageUploadStatus", (bitmap != null || imageUri != null) ? "pending" : "no_image");
        scan.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .document(uid)
                .collection("prescriptions")
                .document(scanId)
                .set(scan, SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "Error saving prescription to Firestore", e));

        db.collection("users")
                .document(uid)
                .collection("scanHistory")
                .document(scanId)
                .set(scan, SetOptions.merge())
                .addOnFailureListener(e -> Log.e(TAG, "Error saving to scanHistory", e));

        uploadPrescriptionImageToStorage(uid, scanId, storagePath);
    }

    private void uploadPrescriptionImageToStorage(String uid, String scanId, String storagePath) {
        Bitmap imageToUpload = bitmap;
        if (imageToUpload == null && imageUri != null) {
            try {
                imageToUpload = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } catch (Exception e) {
                Log.e(TAG, "Could not get image bitmap for storage upload", e);
            }
        }

        if (imageToUpload == null) {
            return;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int width = imageToUpload.getWidth();
            int height = imageToUpload.getHeight();
            float maxDim = 1600f;
            if (width > maxDim || height > maxDim) {
                float scale = Math.min(maxDim / width, maxDim / height);
                Bitmap scaled = Bitmap.createScaledBitmap(imageToUpload, Math.round(width * scale), Math.round(height * scale), true);
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            } else {
                imageToUpload.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            }

            byte[] data = baos.toByteArray();

            // Always preserve scan image locally in on-device storage first
            File localScanDir = new File(getFilesDir(), "scans");
            if (!localScanDir.exists()) {
                localScanDir.mkdirs();
            }
            File localScanFile = new File(localScanDir, scanId + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(localScanFile)) {
                fos.write(data);
                fos.flush();
            } catch (IOException ioEx) {
                Log.e(TAG, "Could not cache scan image locally", ioEx);
            }

            StorageReference storageRef = FirebaseStorage.getInstance()
                    .getReference()
                    .child(storagePath);

            storageRef.putBytes(data)
                    .addOnSuccessListener(taskSnapshot -> {
                        storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                            Map<String, Object> update = new HashMap<>();
                            update.put("imageUrl", downloadUri.toString());
                            update.put("imageUploadStatus", "uploaded");
                            update.put("localImagePath", localScanFile.getAbsolutePath());

                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            db.collection("users").document(uid).collection("prescriptions").document(scanId).set(update, SetOptions.merge());
                            db.collection("users").document(uid).collection("scanHistory").document(scanId).set(update, SetOptions.merge());

                            Log.d(TAG, "Prescription image uploaded to Cloud Storage: " + downloadUri);
                            Toast.makeText(this, "Prescription image saved to Cloud Storage!", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Cloud storage bucket not configured or unreachable: " + e.getMessage() + ". Preserved scan locally at " + localScanFile.getAbsolutePath());
                        Map<String, Object> update = new HashMap<>();
                        update.put("imageStorageStatus", "saved_locally");
                        update.put("localImagePath", localScanFile.getAbsolutePath());
                        update.put("storageNotice", "Preserved in on-device storage (Cloud storage unconfigured)");

                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        db.collection("users").document(uid).collection("prescriptions").document(scanId).set(update, SetOptions.merge());
                        db.collection("users").document(uid).collection("scanHistory").document(scanId).set(update, SetOptions.merge());

                        Toast.makeText(this,
                                "Prescription analyzed and image saved locally on device.",
                                Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            Log.e(TAG, "Exception preparing image for upload", e);
        }
    }

    private synchronized void renderPrescriptionAnalysis() {
        if (lastParseResult == null) return;

        MedicineDatasetManager datasetManager =
                MedicineDatasetManager.getInstance(this);

        List<MedicineDatasetManager.ExtractedMedicineDetail> detectedMeds =
                lastParseResult.detectedMedicines;

        StringBuilder sb = new StringBuilder();
        sb.append("MEDINEX MEDICINE RECOGNITION REPORT (OFFLINE CANDIDATE)\n");
        sb.append("Recognition Method: Offline ML Kit OCR + Local Clinical Catalog\n");
        if (lastFallbackReason != null && !lastFallbackReason.isEmpty()) {
            sb.append("Offline Fallback Trigger: ").append(lastFallbackReason).append("\n");
        }
        sb.append("\n");

        sb.append("[STATUS: OFFLINE CANDIDATE - Local Packaging Candidate Only]\n");
        sb.append("Notice: Medinex Shared AI backend was unreachable. Results below are unconfirmed offline candidates matched against local device catalog.\n\n");

        if (lastQualityResult != null
                && !lastQualityResult.isAcceptable
                && lastQualityResult.qualityNotice != null) {
            sb.append("Image Quality Notice:\n")
                    .append(lastQualityResult.qualityNotice)
                    .append("\n\n");
        }

        if (lastParseResult.isUnreadableHandwriting
                && detectedMeds.isEmpty()) {
            sb.append("The medicine text could not be confidently identified.\n")
                    .append("Please upload a clearer image.\n");
            Log.i(TAG, "[SCAN_DIAGNOSTIC] Final displayed result (Offline Unreadable):\n" + sb.toString());
            resultText.setText(sb.toString());
            return;
        }

        sb.append("IDENTIFIED OFFLINE CANDIDATES (")
                .append(detectedMeds.size())
                .append(")\n\n");

        for (MedicineDatasetManager.ExtractedMedicineDetail detail
                : detectedMeds) {

            MedicineDatasetManager.MedicineItem med = detail.item;

            sb.append("------------------------------\n")
                    .append("Product (Candidate): ").append(safeText(med.name)).append("\n")
                    .append("Generic: ").append(safeText(med.activeSalt)).append("\n")
                    .append("Strength: ").append(safeText(detail.strength)).append("\n")
                    .append("Dosage form: ").append(safeText(detail.dosageForm)).append("\n")
                    .append("Manufacturer: ").append(detail.manufacturer != null && !detail.manufacturer.isEmpty() ? detail.manufacturer : "Not specified on package").append("\n")
                    .append("Category: ").append(safeText(med.category)).append("\n")
                    .append("Indication: ").append(safeText(med.indication)).append("\n")
                    .append("Uses: ").append(safeText(med.uses)).append("\n")
                    .append("Confidence: ")
                    .append(Math.round(detail.matchConfidence * 100))
                    .append("% (Offline Candidate Match)\n")
                    .append("Verification Tier: Candidate Match (Offline Local OCR)\n");

            if (detail.onlineMatchInfo != null && !detail.onlineMatchInfo.isEmpty()) {
                sb.append("Online Reference: ").append(detail.onlineMatchInfo).append("\n");
            }

            sb.append("Data source: ").append(safeText(detail.dataSource))
                    .append("\n\n");

            Log.i(TAG, "[SCAN_DIAGNOSTIC] Offline Candidate: " + med.name
                    + " | Confidence: " + detail.matchConfidence
                    + " | Tier: Candidate Match (Offline Local OCR)"
                    + " | Source: " + detail.dataSource);
        }

        List<MedicineDatasetManager.DuplicateResult> duplicates =
                datasetManager.checkDuplicates(detectedMeds);

        if (!duplicates.isEmpty()) {
            sb.append("POTENTIAL DUPLICATE MEDICINES\n");
            for (MedicineDatasetManager.DuplicateResult dup : duplicates) {
                sb.append(safeText(dup.med1.name))
                        .append(" + ")
                        .append(safeText(dup.med2.name))
                        .append(" | Shared ingredient: ")
                        .append(safeText(dup.sharedSalt))
                        .append("\n");
            }
            sb.append("\n");
        }

        List<MedicineDatasetManager.InteractionItem> interactions =
                datasetManager.checkInteractions(detectedMeds);

        if (!interactions.isEmpty()) {
            sb.append("POTENTIAL DRUG INTERACTIONS\n");
            for (MedicineDatasetManager.InteractionItem inter : interactions) {
                sb.append(safeText(inter.drug1))
                        .append(" + ")
                        .append(safeText(inter.drug2))
                        .append(" | Risk: ")
                        .append(safeText(inter.riskLevel))
                        .append(" | ")
                        .append(safeText(inter.description))
                        .append("\n");
            }
            sb.append("\n");
        }

        sb.append("Medical note: This scan is an offline local candidate assessment only. ")
                .append("Confirm medicine names, strengths and dosage with a pharmacist or clinician.\n");

        Log.i(TAG, "[SCAN_DIAGNOSTIC] Final displayed result (Offline Fallback):\n" + sb.toString());
        resultText.setText(sb.toString());
    }

    private void searchMedicineOnline(
            MedicineDatasetManager.ExtractedMedicineDetail detail
    ) {
        if (detail == null || detail.item == null
                || detail.item.name == null) return;

        String brandQuery = detail.item.name.trim();
        String genericQuery = detail.item.activeSalt != null
                ? detail.item.activeSalt.trim() : "";

        RetrofitClient.getApi()
                .searchMedicine(brandQuery, 5)
                .enqueue(new Callback<RxNormResponse>() {
                    @Override
                    public void onResponse(
                            Call<RxNormResponse> call,
                            Response<RxNormResponse> response
                    ) {
                        if (response.isSuccessful()
                                && response.body() != null) {

                            RxNormResponse.Candidate candidate =
                                    response.body()
                                            .getBestCandidateForTerm(brandQuery);

                            if (candidate != null
                                    && candidate.name != null
                                    && isReasonableOnlineMatch(
                                    brandQuery, candidate.name)) {

                                detail.onlineMatchInfo =
                                        "RxNorm candidate: " + candidate.name
                                                + " (RxCUI: "
                                                + safeText(candidate.rxcui) + ")";

                                runOnUiThread(
                                        UploadActivity.this::renderPrescriptionAnalysis
                                );
                                return;
                            }
                        }

                        if (!genericQuery.isEmpty()) {
                            searchGenericSaltOnline(detail, genericQuery);
                        } else {
                            detail.onlineMatchInfo =
                                    "No direct online match found.";
                            runOnUiThread(
                                    UploadActivity.this::renderPrescriptionAnalysis
                            );
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<RxNormResponse> call,
                            Throwable t
                    ) {
                        Log.w(TAG, "Medicine lookup failed", t);
                        detail.onlineMatchInfo =
                                "Online lookup unavailable.";
                        runOnUiThread(
                                UploadActivity.this::renderPrescriptionAnalysis
                        );
                    }
                });
    }

    private void searchGenericSaltOnline(
            MedicineDatasetManager.ExtractedMedicineDetail detail,
            String genericQuery
    ) {
        String primarySalt = genericQuery.split("[\\+,/]")[0].trim();

        if (primarySalt.isEmpty()) return;

        RetrofitClient.getApi()
                .searchMedicine(primarySalt, 5)
                .enqueue(new Callback<RxNormResponse>() {
                    @Override
                    public void onResponse(
                            Call<RxNormResponse> call,
                            Response<RxNormResponse> response
                    ) {
                        if (response.isSuccessful()
                                && response.body() != null) {

                            RxNormResponse.Candidate candidate =
                                    response.body()
                                            .getBestCandidateForTerm(primarySalt);

                            if (candidate != null) {
                                detail.onlineMatchInfo =
                                        "Ingredient candidate: "
                                                + safeText(candidate.name)
                                                + " (RxCUI: "
                                                + safeText(candidate.rxcui) + ")";
                            } else {
                                detail.onlineMatchInfo =
                                        "No direct ingredient match found.";
                            }
                        } else {
                            detail.onlineMatchInfo =
                                    "Online ingredient lookup unavailable.";
                        }

                        runOnUiThread(
                                UploadActivity.this::renderPrescriptionAnalysis
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<RxNormResponse> call,
                            Throwable t
                    ) {
                        detail.onlineMatchInfo =
                                "Online ingredient lookup unavailable.";
                        runOnUiThread(
                                UploadActivity.this::renderPrescriptionAnalysis
                        );
                    }
                });
    }

    private boolean isReasonableOnlineMatch(
            String query,
            String onlineName
    ) {
        if (query == null || onlineName == null) return false;

        String q = query.toLowerCase(Locale.ROOT).trim();
        String o = onlineName.toLowerCase(Locale.ROOT).trim();

        if (q.equals("dolo") && o.startsWith("no dolo")) return false;

        return o.contains(q) || q.contains(o);
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty()
                ? "Information not available" : value.trim();
    }
}
