plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id ("maven-publish")
    id ("kotlin-kapt")
    id("kotlin-parcelize") // BLE 데이터 클래스용 Parcelize 지원
}

publishing {
    publications {
        register("release", MavenPublication::class) { // MavenPublication::class 사용 가능
            groupId = "com.github.Rhpark"
            artifactId = "CustomSystemService"
            version = "0.1.0"

            afterEvaluate {
                from(components.findByName("release"))
            }
        }

        register("debug", MavenPublication::class) { // MavenPublication::class 사용 가능
            groupId = "com.github.Rhpark"
            artifactId = "CustomSystemService"
            version = "0.1.0" // 동일 버전 사용 시 주의 (이전 답변 참고)

            afterEvaluate {
                from(components.findByName("debug"))
            }
        }
    }
}

android {
    namespace = "kr.open.library.systemmanager"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    // BLE 기능을 위한 Parcelize 지원
    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.github.rhpark:Permissions:0.9.4")
    implementation("com.github.rhpark:Android_Custom_Logcat:0.9.2")
    
    // BLE 기능을 위한 추가 의존성
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1") // Kotlin 2.0.1 안정 버전, StateFlow, Dispatchers.IO
    implementation("com.google.code.gson:gson:2.10.1") // JSON 직렬화/역직렬화
}