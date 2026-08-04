plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.navigation.safeargs)
}

/**
 * Reads a value from gradle.properties, failing the build with a clear message when it is missing.
 * Used for the Cloudinary settings so a misconfigured machine fails at configuration time
 * rather than with a confusing upload error at runtime.
 */
fun requiredProperty(name: String): String =
    providers.gradleProperty(name).orNull
        ?: throw GradleException("Missing '$name' in gradle.properties. See README.")

android {
    namespace = "com.roeiamor.fitshare"

    // Stays at 36: API 37 is deliberately not installed. See CLAUDE.md > Pinned dependencies.
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.roeiamor.fitshare"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Cloudinary unsigned upload. The preset is public by design - it is embedded in
        // every client that uses it - so this is not a leaked secret. Explained in the README.
        buildConfigField(
            "String",
            "CLOUDINARY_CLOUD_NAME",
            "\"${requiredProperty("CLOUDINARY_CLOUD_NAME")}\""
        )
        buildConfigField(
            "String",
            "CLOUDINARY_UPLOAD_PRESET",
            "\"${requiredProperty("CLOUDINARY_UPLOAD_PRESET")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            // Local unit tests run against a stub android.jar whose methods throw by default.
            // ErrorMapperTest has to construct real Firebase exceptions, and their constructor
            // calls TextUtils.isEmpty internally - which throws "not mocked" and fails the test
            // before it reaches the code under test. Returning defaults instead lets those
            // constructors complete. Nothing in the app relies on this; it affects tests only.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // AndroidX UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    // Reads the orientation tag a camera writes into a JPEG. Without it, photos taken in portrait
    // upload sideways, because BitmapFactory ignores EXIF.
    implementation(libs.androidx.exifinterface)
    implementation(libs.material)

    // MVVM: ViewModel + LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Single-Activity navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Firebase - the BOM aligns every module to one compatible set
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Coroutines, plus await() for Firebase Task<T>
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Image loading and the Cloudinary upload client
    implementation(libs.glide)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
