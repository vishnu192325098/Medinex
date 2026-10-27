package com.example.medinex;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenFdaApiService {

    @GET("drug/label.json")
    Call<OpenFdaResponse> searchDrugLabel(
            @Query("search") String search,
            @Query("limit") int limit
    );
}
