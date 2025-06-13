plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.monekx.curfewnotifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monekx.curfewnotifier"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2b"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    splits {
        // Доступ к конфигурации ABI
        abi {
            // Включаем разделение по ABI
            isEnable = true // Используем присваивание

            // Сбросить все фильтры, чтобы не было конфликтов
            reset()

            // Включаем необходимые архитектуры
            // Используем setIncludes с listOf для явного указания
            include(

                    "armeabi-v7a",
                    "arm64-v8a",
                    "x86",
                    "x86_64"

            )

            // Отключить создание универсального APK, чтобы получить отдельные APK
            isUniversalApk = false // Используем присваивание с префиксом 'is'
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-simplexml:2.9.0")
    implementation("org.simpleframework:simple-xml:2.7.1") {
        exclude(group = "stax", module = "stax-api")
        exclude(group = "xpp3", module = "xpp3")
    }
    
    implementation("com.google.android.gms:play-services-location:21.3.0") // Используем последнюю стабильную версию
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20") // Последняя стабильная версия может отличаться
    implementation("org.osmdroid:osmdroid-wms:6.1.20")
    implementation(libs.androidx.datastore.preferences) // или самая свежая версия
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}