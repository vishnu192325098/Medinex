package com.example.medinex;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OpenFdaResponse {

    @SerializedName("results")
    public List<Result> results;

    public static class Result {
        @SerializedName("openfda")
        public OpenFdaData openfda;

        @SerializedName("indications_and_usage")
        public List<String> indicationsAndUsage;
    }

    public static class OpenFdaData {
        @SerializedName("brand_name")
        public List<String> brandName;

        @SerializedName("generic_name")
        public List<String> genericName;

        @SerializedName("manufacturer_name")
        public List<String> manufacturerName;

        @SerializedName("substance_name")
        public List<String> substanceName;

        @SerializedName("rxcui")
        public List<String> rxcui;

        @SerializedName("route")
        public List<String> route;
    }

    public String getFirstBrandName() {
        if (results != null && !results.isEmpty() && results.get(0).openfda != null
                && results.get(0).openfda.brandName != null && !results.get(0).openfda.brandName.isEmpty()) {
            return results.get(0).openfda.brandName.get(0);
        }
        return null;
    }

    public String getFirstGenericName() {
        if (results != null && !results.isEmpty() && results.get(0).openfda != null
                && results.get(0).openfda.genericName != null && !results.get(0).openfda.genericName.isEmpty()) {
            return results.get(0).openfda.genericName.get(0);
        }
        return null;
    }

    public String getFirstManufacturer() {
        if (results != null && !results.isEmpty() && results.get(0).openfda != null
                && results.get(0).openfda.manufacturerName != null && !results.get(0).openfda.manufacturerName.isEmpty()) {
            return results.get(0).openfda.manufacturerName.get(0);
        }
        return null;
    }
}
