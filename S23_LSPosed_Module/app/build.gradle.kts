plugins {
    id("com.android.application")
}

android {
    namespace = "com.s23.enhancements"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.s23.enhancements"
        minSdk = 30
        targetSdk = 35
        versionCode = 30
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            val envStore = System.getenv("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
            storeFile = file(envStore ?: "../../debug.keystore")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "debug"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets["main"].resources.directories.add("src/main/resources")

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

configurations.configureEach {
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.fragment", module = "fragment")
    exclude(group = "androidx.fragment", module = "fragment-ktx")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
    exclude(group = "com.google.android.material", module = "material")
}

dependencies {
    compileOnly(files("../../libxposed-api-102.jar"))
    testImplementation("junit:junit:4.13.2")

    implementation("sesl.androidx.core:core:1.19.0+1.0.29-sesl9+rev6")
    implementation("sesl.androidx.appcompat:appcompat:1.8.0+1.0.38-sesl9+rev8")
    implementation("sesl.androidx.fragment:fragment:1.9.0+1.0.6-sesl9+rev2")
    implementation("sesl.androidx.coordinatorlayout:coordinatorlayout:1.3.0+1.0.6-sesl9+rev0")
    implementation("sesl.androidx.recyclerview:recyclerview:1.4.0+1.0.25-sesl9+rev7")
    implementation("sesl.androidx.preference:preference:1.2.1+1.0.5-sesl9+rev1")
    implementation("sesl.androidx.customview:customview:1.2.0-rc01+1.0.1-sesl9+rev0")
    implementation("sesl.androidx.drawerlayout:drawerlayout:1.2.0+1.0.5-sesl9+rev1")
    implementation("sesl.com.google.android.material:material:1.14.0+1.0.47-sesl9+rev14")
}
