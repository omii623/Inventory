plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.inventory"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.inventory"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // A rendszer „App nyelve” beállításához (Android 13+), a res/resources.properties alapján.
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            // R8: kódzsugorítás, obfuszkálás és erőforrás-zsugorítás (keepRules/rules.keep), mint a cashAppban.
            optimization {
                enable = true
            }
            isShrinkResources = true
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    // A séma JSON-ok verziókövetve: ezekből készülnek majd a migrációs tesztek.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    // Az alkalmazásonkénti nyelvváltáshoz (AppCompatDelegate.setApplicationLocales).
    implementation(libs.androidx.appcompat)
    // A fotók EXIF-forgatásához (a beépített android.media változat régebbi Androidokon hibás).
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    // Valódi org.json a unit tesztekhez (az android.jar-ban csak üres csonk van) – a mentés kódolójához.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
