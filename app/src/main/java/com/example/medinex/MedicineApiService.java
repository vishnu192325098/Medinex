package com.example.medinex;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface MedicineApiService {

    @GET("REST/approximateTerm.json")
    Call<RxNormResponse> searchMedicine(
            @Query("term") String term,
            @Query("maxEntries") int maxEntries
    );

    @GET("REST/rxcui/{rxcui}/properties.json")
    Call<RxNormPropertiesResponse> getRxNormProperties(
            @Path("rxcui") String rxcui
    );
}