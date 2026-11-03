plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

android {
	namespace = "org.duru.takip"
	compileSdk = 34

	defaultConfig {
		applicationId = "org.duru.takip"
		minSdk = 24
		targetSdk = 34
		versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
		versionName = System.getenv("VERSION_NAME") ?: "1.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		// İmzalama sertifikasının SHA-256'sı CI'da hesaplanıp buraya gömülür (kaynağa değil).
		// Uygulama açılışta kendi imzasını bununla karşılaştırır; yeniden imzalanmış
		// (kurcalanmış) kopyalar yakalanır. Yerel/debug derlemede boş kalır, kontrol atlanır.
		buildConfigField("String", "SIGN_CERT_SHA256", "\"${System.getenv("SIGN_CERT_SHA256") ?: ""}\"")
	}

	// İmza bilgileri yalnızca ortam değişkenlerinden (CI'da GitHub Actions secret'larından) gelir.
	// Hiçbir şifre/anahtar kaynak koda gömülmez; yoksa imzasız (debug) derlenir.
	val imzaYolu: String? = System.getenv("KEYSTORE_PATH")
	signingConfigs {
		create("pilot") {
			if (imzaYolu != null) {
				storeFile = file(imzaYolu)
				storeType = "PKCS12"
				storePassword = System.getenv("SIGN_PASS")
				keyAlias = System.getenv("KEY_ALIAS") ?: "duru"
				keyPassword = System.getenv("SIGN_PASS")
			}
		}
	}

	buildTypes {
		getByName("release") {
			// R8 ile kod küçültme + isim karıştırma: derlenmiş kod okunması/çözülmesi zorlaşır.
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			if (imzaYolu != null) signingConfig = signingConfigs.getByName("pilot")
		}
	}

	buildFeatures {
		buildConfig = true // Guncelleyici BuildConfig.VERSION_CODE kullanır
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions { jvmTarget = "17" }
}

dependencies {
	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("androidx.work:work-runtime-ktx:2.9.1")
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	testImplementation("junit:junit:4.13.2")
}
