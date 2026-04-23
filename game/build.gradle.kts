plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.devfigas.dotsandboxes"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.devfigas.dotsandboxes"
        minSdk = 23
        targetSdk = 35
        versionCode = 26042401
        versionName = "1.2.2"

        testApplicationId = "com.devfigas.dotsandboxes.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "defaultAvatar", "\"🔴\"")
    }

    signingConfigs {
        getByName("debug") {
            // Uses default debug keystore
        }
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            if (keystoreFile != null) {
                val keystorePath = rootProject.file(keystoreFile)
                if (keystorePath.exists()) {
                    storeFile = keystorePath
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                } else {
                    storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                    storePassword = "android"
                    this.keyAlias = "androiddebugkey"
                    this.keyPassword = "android"
                }
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                this.keyAlias = "androiddebugkey"
                this.keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "APPODEAL_APP_KEY", "\"bb6b7a244e322d8b8eadecaadf93f174c3b22f3fdfb77b72\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "APPODEAL_APP_KEY", "\"be06163375d71ab1bceb57389c1c5f1e9cde120e52e1810f\"")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.code.gson:gson:2.10.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Appodeal SDK
    implementation("com.appodeal.ads:sdk:3.12.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // Single resolution path - local and CI both resolve these via GitHub Packages
    // (see settings.gradle.kts). Versions come from lib-versions.properties.
    implementation("com.devfigas:p2pkit:${findProperty("p2pkitVersion")}")
    implementation("com.devfigas:uikit:${findProperty("uikitVersion")}")
    implementation("com.devfigas:gridgame:${findProperty("gridgameVersion")}")
    implementation("com.devfigas:mockpvp:${findProperty("mockpvpVersion")}")

    val emoji2Version = "1.4.0"
    implementation("androidx.emoji2:emoji2:$emoji2Version")
    implementation("androidx.emoji2:emoji2-views:$emoji2Version")
    implementation("androidx.emoji2:emoji2-views-helper:$emoji2Version")
    implementation("androidx.emoji2:emoji2-emojipicker:$emoji2Version")
}

// Core dependency groups that must never be excluded
val coreDependencyGroups = setOf(
    "com.google", "com.firebase", "androidx", "org.jetbrains", "com.appodeal.ads"
)

fun isCoreGroup(group: String) = coreDependencyGroups.any { group.startsWith(it) }

// Mapping: SDK group -> corresponding Appodeal adapter module name
val sdkGroupToAdapterModule = mapOf(
    "com.pubmatic.sdk" to "pubmatic",
    "com.inmobi.monetization" to "inmobi",
    "net.pubnative" to "hybid",
    "com.ironsource.sdk" to "ironsource",
    "com.unity3d.ads" to "unity_ads",
    "com.vungle" to "vungle",
    "com.chartboost" to "chartboost",
    "com.applovin" to "applovin",
    "com.fyber" to "fyber",
    "com.amazon.android" to "amazon",
    "com.mbridge" to "mintegral",
    "com.pangle" to "pangle",
    "com.my.target" to "mytarget",
    "com.yandex.android" to "yandex",
)

val ciExcludes = (findProperty("excludeAdGroups") as? String)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.toSet()
    ?: emptySet()

val adSdkProbe = configurations.detachedConfiguration(
    dependencies.create("com.appodeal.ads:sdk:3.12.0.1")
)
val unresolvedAdGroups = adSdkProbe.resolvedConfiguration
    .lenientConfiguration
    .unresolvedModuleDependencies
    .map { it.selector.group }
    .filter { !isCoreGroup(it) }
    .toSet()

val adapterExcludes = unresolvedAdGroups
    .mapNotNull { sdkGroupToAdapterModule[it] }
    .map { "com.appodeal.ads.sdk.networks" to it }

val ciAdapterExcludes = ciExcludes
    .mapNotNull { sdkGroupToAdapterModule[it] }
    .map { "com.appodeal.ads.sdk.networks" to it }

val allGroupExcludes = ciExcludes + unresolvedAdGroups
val allModuleExcludes = adapterExcludes + ciAdapterExcludes

if (allGroupExcludes.isNotEmpty() || allModuleExcludes.isNotEmpty()) {
    println("Excluding unavailable ad mediation SDK groups: $allGroupExcludes")
    println("Excluding corresponding adapter modules: ${allModuleExcludes.map { it.second }}")
    configurations.configureEach {
        allGroupExcludes.forEach { group ->
            exclude(group = group)
        }
        allModuleExcludes.forEach { (group, module) ->
            exclude(group = group, module = module)
        }
    }
}
