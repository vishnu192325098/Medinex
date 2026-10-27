
package com.example.medinex;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class FirebaseDataManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public FirebaseDataManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    private DocumentReference getDocument(
            String collection,
            String documentId
    ) {
        if (auth.getCurrentUser() == null) {
            throw new IllegalStateException(
                    "User must be logged in to save data."
            );
        }

        return db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection(collection)
                .document(documentId);
    }

    public interface SaveCallback {
        void onComplete(boolean success, String message);
    }

    public void saveRecord(
            String collection,
            String documentId,
            Map<String, Object> data,
            SaveCallback callback
    ) {
        try {
            Map<String, Object> record = new HashMap<>(data);
            record.put("updatedAt", FieldValue.serverTimestamp());

            getDocument(collection, documentId)
                    .set(record, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        if (callback != null) {
                            callback.onComplete(true, "Saved successfully");
                        }
                    })
                    .addOnFailureListener(error -> {
                        if (callback != null) {
                            callback.onComplete(false, error.getMessage());
                        }
                    });

        } catch (Exception error) {
            if (callback != null) {
                callback.onComplete(false, error.getMessage());
            }
        }
    }

    public void getRecord(
            String collection,
            String documentId,
            com.google.android.gms.tasks.OnSuccessListener<
                    com.google.firebase.firestore.DocumentSnapshot
                    > onSuccess,
            com.google.android.gms.tasks.OnFailureListener onFailure
    ) {
        try {
            getDocument(collection, documentId)
                    .get()
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure);

        } catch (Exception error) {
            if (onFailure != null) {
                onFailure.onFailure(error);
            }
        }
    }

    public void deleteRecord(
            String collection,
            String documentId,
            SaveCallback callback
    ) {
        try {
            getDocument(collection, documentId)
                    .delete()
                    .addOnSuccessListener(unused -> {
                        if (callback != null) {
                            callback.onComplete(true, "Deleted successfully");
                        }
                    })
                    .addOnFailureListener(error -> {
                        if (callback != null) {
                            callback.onComplete(false, error.getMessage());
                        }
                    });

        } catch (Exception error) {
            if (callback != null) {
                callback.onComplete(false, error.getMessage());
            }
        }
    }
}
