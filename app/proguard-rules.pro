# OkHttp / Okio: AAR kendi tüketici kurallarını getirir; yalnızca uyarıları sustur.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager, Worker sınıflarını yansımayla (reflection) oluşturur: koru.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# BuildConfig alanlarına doğrudan erişiliyor; korunsun.
-keep class org.duru.takip.BuildConfig { *; }
