package com.example.medinex;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface SharedRecognitionApiService {

    @Multipart
    @POST("api/medicine/recognize")
    Call<MedicineRecognitionResponse> recognizeMedicine(
            @Part MultipartBody.Part file
    );
}
