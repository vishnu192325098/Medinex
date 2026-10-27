package com.example.medinex;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ScanHistoryActivity";

    private TextView historySummarySubtitle;
    private TextView emptyStatusText;
    private LinearLayout scansContainer;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_history);

        historySummarySubtitle = findViewById(R.id.historySummarySubtitle);
        emptyStatusText = findViewById(R.id.emptyStatusText);
        scansContainer = findViewById(R.id.scansContainer);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        loadScanHistory();
    }

    private void loadScanHistory() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            emptyStatusText.setVisibility(View.VISIBLE);
            emptyStatusText.setText("Please log in to view your scan history.");
            historySummarySubtitle.setText("User not logged in");
            return;
        }

        emptyStatusText.setVisibility(View.VISIBLE);
        emptyStatusText.setText("Loading your prescription scans...");
        historySummarySubtitle.setText("Fetching records...");
        scansContainer.removeAllViews();

        String uid = user.getUid();

        firestore.collection("users")
                .document(uid)
                .collection("prescriptions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        renderScans(querySnapshot.getDocuments());
                    } else {
                        // Fallback check to scanHistory collection for backward compatibility
                        loadScanHistoryFallback(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed loading prescriptions, checking fallback", e);
                    loadScanHistoryFallback(uid);
                });
    }

    private void loadScanHistoryFallback(String uid) {
        firestore.collection("users")
                .document(uid)
                .collection("scanHistory")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        renderScans(querySnapshot.getDocuments());
                    } else {
                        emptyStatusText.setVisibility(View.VISIBLE);
                        emptyStatusText.setText("No prescription scans saved yet.\n\nUpload a prescription from the scanner screen to see AI medicine analysis here.");
                        historySummarySubtitle.setText("0 scans on record");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed loading scan history fallback", e);
                    emptyStatusText.setVisibility(View.VISIBLE);
                    emptyStatusText.setText("Could not load scan records. Please check your internet connection.");
                    historySummarySubtitle.setText("Load error");
                });
    }

    private void renderScans(List<DocumentSnapshot> docs) {
        emptyStatusText.setVisibility(View.GONE);
        scansContainer.removeAllViews();

        historySummarySubtitle.setText(docs.size() + " prescription scan" + (docs.size() == 1 ? "" : "s") + " stored");

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        for (DocumentSnapshot doc : docs) {
            Timestamp timestamp = doc.getTimestamp("createdAt");
            String dateStr = (timestamp != null) ? sdf.format(timestamp.toDate()) : "Recent scan";

            String status = doc.getString("status");
            if (status == null || status.isEmpty()) {
                status = "completed";
            }

            String imageUrl = doc.getString("imageUrl");
            String imageUploadStatus = doc.getString("imageUploadStatus");
            List<Map<String, Object>> medicines = (List<Map<String, Object>>) doc.get("medicines");

            View scanCard = createScanCard(dateStr, status, imageUrl, imageUploadStatus, medicines);
            scansContainer.addView(scanCard);
        }
    }

    private View createScanCard(String dateStr, String status, String imageUrl, String imageUploadStatus, List<Map<String, Object>> medicines) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.glass_card);
        card.setPadding(36, 36, 36, 36);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 36);
        card.setLayoutParams(params);

        // Header: Date + Status Badge
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        TextView dateText = new TextView(this);
        dateText.setText("📅 " + dateStr);
        dateText.setTextColor(getResources().getColor(R.color.text_primary));
        dateText.setTextSize(16);
        dateText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dateText.setLayoutParams(dateParams);
        headerLayout.addView(dateText);

        TextView statusBadge = new TextView(this);
        boolean isCompleted = "completed".equalsIgnoreCase(status);
        statusBadge.setText(isCompleted ? "✓ Completed" : "ℹ Note");
        statusBadge.setTextColor(isCompleted ? 0xFF2E7D32 : 0xFFE65100);
        statusBadge.setTextSize(12);
        statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        statusBadge.setPadding(16, 8, 16, 8);
        headerLayout.addView(statusBadge);

        card.addView(headerLayout);

        // Medicines Summary
        int medCount = (medicines != null) ? medicines.size() : 0;
        TextView countText = new TextView(this);
        countText.setText("Identified Medicines: " + medCount);
        countText.setTextColor(getResources().getColor(R.color.text_secondary));
        countText.setTextSize(13);
        countText.setPadding(0, 10, 0, 16);
        card.addView(countText);

        if (medicines != null && !medicines.isEmpty()) {
            for (Map<String, Object> med : medicines) {
                String name = (String) med.get("name");
                String ingredient = (String) med.get("activeIngredient");
                String strength = (String) med.get("strength");
                String form = (String) med.get("dosageForm");
                Double confidence = null;
                Object confObj = med.get("matchConfidence");
                if (confObj instanceof Number) {
                    confidence = ((Number) confObj).doubleValue();
                }

                LinearLayout medItem = new LinearLayout(this);
                medItem.setOrientation(LinearLayout.VERTICAL);
                medItem.setPadding(20, 14, 20, 14);
                medItem.setBackgroundColor(0x0D0288D1);

                LinearLayout.LayoutParams medParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                medParams.setMargins(0, 0, 0, 12);
                medItem.setLayoutParams(medParams);

                TextView medNameText = new TextView(this);
                medNameText.setText("💊 " + (name != null ? name : "Medicine"));
                medNameText.setTextColor(getResources().getColor(R.color.text_primary));
                medNameText.setTextSize(15);
                medNameText.setTypeface(null, android.graphics.Typeface.BOLD);
                medItem.addView(medNameText);

                StringBuilder details = new StringBuilder();
                if (ingredient != null && !ingredient.isEmpty()) {
                    details.append("Generic: ").append(ingredient).append("\n");
                }
                if (strength != null && !strength.isEmpty()) {
                    details.append("Strength: ").append(strength).append("  •  ");
                }
                if (form != null && !form.isEmpty()) {
                    details.append("Form: ").append(form);
                }
                if (confidence != null && confidence > 0) {
                    details.append("\nConfidence: ").append(Math.round(confidence * 100)).append("%");
                }

                TextView medDetailsText = new TextView(this);
                medDetailsText.setText(details.toString().trim());
                medDetailsText.setTextColor(getResources().getColor(R.color.text_secondary));
                medDetailsText.setTextSize(12);
                medDetailsText.setPadding(0, 4, 0, 0);
                medItem.addView(medDetailsText);

                card.addView(medItem);
            }
        } else {
            TextView noMedsText = new TextView(this);
            noMedsText.setText("No clear medicine names recognized in this scan.");
            noMedsText.setTextColor(getResources().getColor(R.color.text_secondary));
            noMedsText.setTextSize(13);
            noMedsText.setPadding(0, 0, 0, 10);
            card.addView(noMedsText);
        }

        // Image Section
        if (imageUrl != null && !imageUrl.isEmpty()) {
            TextView imageLabel = new TextView(this);
            imageLabel.setText("🖼 Original Prescription Image:");
            imageLabel.setTextColor(getResources().getColor(R.color.text_primary));
            imageLabel.setTextSize(13);
            imageLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            imageLabel.setPadding(0, 12, 0, 8);
            card.addView(imageLabel);

            ImageView thumbnail = new ImageView(this);
            thumbnail.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    360
            ));
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundColor(0x15000000);
            card.addView(thumbnail);

            // Load thumbnail asynchronously
            loadAsyncImage(imageUrl, thumbnail);

            thumbnail.setOnClickListener(v -> showFullImageDialog(imageUrl));
        } else if ("failed".equalsIgnoreCase(imageUploadStatus)) {
            TextView failNotice = new TextView(this);
            failNotice.setText("⚠ Original image could not be uploaded to Cloud Storage.");
            failNotice.setTextColor(0xFFD32F2F);
            failNotice.setTextSize(12);
            failNotice.setPadding(0, 8, 0, 0);
            card.addView(failNotice);
        }

        return card;
    }

    private void loadAsyncImage(String urlString, ImageView target) {
        imageExecutor.execute(() -> {
            Bitmap bmp = null;
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoInput(true);
                conn.connect();
                InputStream is = conn.getInputStream();
                bmp = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Exception e) {
                Log.e(TAG, "Error downloading prescription thumbnail", e);
            }

            final Bitmap finalBmp = bmp;
            runOnUiThread(() -> {
                if (finalBmp != null && !isFinishing() && !isDestroyed()) {
                    target.setImageBitmap(finalBmp);
                }
            });
        });
    }

    private void showFullImageDialog(String imageUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xF0000000);
        layout.setPadding(24, 48, 24, 24);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕ Close Image View");
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setTextSize(18);
        closeBtn.setGravity(Gravity.END);
        closeBtn.setPadding(16, 16, 24, 24);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        layout.addView(closeBtn);

        ImageView fullImageView = new ImageView(this);
        fullImageView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        fullImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(fullImageView);

        loadAsyncImage(imageUrl, fullImageView);

        dialog.setContentView(layout);
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageExecutor.shutdown();
    }
}
