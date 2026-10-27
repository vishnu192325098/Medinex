
package com.example.medinex;

import android.os.Build;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String RXNORM_BASE_URL =
            "https://rxnav.nlm.nih.gov/";

    private static final String OPENFDA_BASE_URL =
            "https://api.fda.gov/";

    private static final String TAG = "RetrofitClient";

    // Standard host loopback for Android emulator (10.0.2.2 maps to host 127.0.0.1)
    public static final String EMULATOR_URL = "http://10.0.2.2:8000/";

    // Configured physical device backend URL resolved from BuildConfig.PHYSICAL_BACKEND_URL
    public static final String PHYSICAL_DEVICE_URL = BuildConfig.PHYSICAL_BACKEND_URL;

    // Configured production backend HTTPS placeholder resolved from BuildConfig.PRODUCTION_BACKEND_URL
    public static final String PRODUCTION_BACKEND_URL = BuildConfig.PRODUCTION_BACKEND_URL;

    private static String customSharedAiBaseUrl = null;

    private static MedicineApiService rxNormApi;
    private static OpenFdaApiService openFdaApi;
    private static SharedRecognitionApiService sharedAiApi;
    private static OkHttpClient sharedHttpClient;

    public static boolean isEmulator() {
        return (Build.BRAND != null && Build.BRAND.startsWith("generic") && Build.DEVICE != null && Build.DEVICE.startsWith("generic"))
                || (Build.FINGERPRINT != null && (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown")))
                || (Build.HARDWARE != null && (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")))
                || (Build.MODEL != null && (Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK built for x86")))
                || (Build.MANUFACTURER != null && Build.MANUFACTURER.contains("Genymotion"))
                || (Build.PRODUCT != null && (Build.PRODUCT.contains("sdk_google") || Build.PRODUCT.contains("google_sdk") || Build.PRODUCT.contains("sdk") || Build.PRODUCT.contains("emulator")));
    }

    public static synchronized String getSharedAiBaseUrl() {
        String url;
        if (customSharedAiBaseUrl != null && !customSharedAiBaseUrl.trim().isEmpty()) {
            url = customSharedAiBaseUrl.endsWith("/") ? customSharedAiBaseUrl.trim() : customSharedAiBaseUrl.trim() + "/";
            android.util.Log.d(TAG, "[RetrofitClient] Using custom Shared AI base URL: " + url);
            return url;
        }

        String defaultPhysical = (BuildConfig.PHYSICAL_BACKEND_URL != null && !BuildConfig.PHYSICAL_BACKEND_URL.trim().isEmpty())
                ? BuildConfig.PHYSICAL_BACKEND_URL.trim()
                : "http://172.20.10.3:8000/";
        if (!defaultPhysical.endsWith("/")) {
            defaultPhysical += "/";
        }

        // When configured with an HTTPS URL, use it directly for both physical devices and emulators
        // (enables connection from college Wi-Fi, cellular networks, or emulators)
        if (defaultPhysical.startsWith("https://")) {
            url = defaultPhysical;
        } else {
            // Local development: emulators route to host loopback 10.0.2.2:8000, physical devices use LAN IP
            url = isEmulator() ? EMULATOR_URL : defaultPhysical;
        }

        android.util.Log.i(TAG, "[RetrofitClient] Selected host for Shared AI: " + url
                + " (Environment: " + (isEmulator() ? "Android Emulator" : "Physical Device") + ")");
        return url;
    }

    public static synchronized void setSharedAiBaseUrl(String url) {
        customSharedAiBaseUrl = url;
        sharedAiApi = null;
        android.util.Log.i(TAG, "[RetrofitClient] Custom Shared AI base URL set to: " + url);
    }

    public static synchronized void useProductionBackend() {
        setSharedAiBaseUrl(PRODUCTION_BACKEND_URL);
    }

    private static synchronized OkHttpClient getHttpClient() {
        if (sharedHttpClient == null) {
            sharedHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .addInterceptor(chain -> {
                        okhttp3.Request request = chain.request();
                        long startNs = System.nanoTime();
                        android.util.Log.i("MedinexNetwork", "--> HTTP " + request.method() + " " + request.url());
                        okhttp3.Response response;
                        try {
                            response = chain.proceed(request);
                        } catch (Exception e) {
                            android.util.Log.e("MedinexNetwork", "<-- HTTP FAILED " + request.url() + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
                            throw e;
                        }
                        long tookMs = (System.nanoTime() - startNs) / 1000000;
                        android.util.Log.i("MedinexNetwork", "<-- HTTP " + response.code() + " " + response.message() + " " + request.url() + " (" + tookMs + "ms)");
                        return response;
                    })
                    .build();
        }
        return sharedHttpClient;
    }

    public static synchronized MedicineApiService getApi() {
        if (rxNormApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(RXNORM_BASE_URL)
                    .client(getHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            rxNormApi = retrofit.create(MedicineApiService.class);
        }
        return rxNormApi;
    }

    public static synchronized OpenFdaApiService getOpenFdaApi() {
        if (openFdaApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(OPENFDA_BASE_URL)
                    .client(getHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            openFdaApi = retrofit.create(OpenFdaApiService.class);
        }
        return openFdaApi;
    }

    public static synchronized SharedRecognitionApiService getRecognitionApi() {
        if (sharedAiApi == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(getSharedAiBaseUrl())
                    .client(getHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            sharedAiApi = retrofit.create(SharedRecognitionApiService.class);
        }
        return sharedAiApi;
    }
}
