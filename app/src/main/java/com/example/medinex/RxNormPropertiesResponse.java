package com.example.medinex;

import com.google.gson.annotations.SerializedName;

public class RxNormPropertiesResponse {

    @SerializedName("properties")
    public Properties properties;

    public static class Properties {
        @SerializedName("rxcui")
        public String rxcui;

        @SerializedName("name")
        public String name;

        @SerializedName("synonym")
        public String synonym;

        @SerializedName("tty")
        public String tty;

        @SerializedName("language")
        public String language;

        @SerializedName("suppress")
        public String suppress;
    }

    public String getConceptName() {
        if (properties != null && properties.name != null && !properties.name.trim().isEmpty()) {
            return properties.name.trim();
        }
        return null;
    }

    public String getRxCui() {
        if (properties != null && properties.rxcui != null) {
            return properties.rxcui.trim();
        }
        return null;
    }

    public String getTermType() {
        if (properties != null && properties.tty != null) {
            return properties.tty.trim();
        }
        return null;
    }
}
