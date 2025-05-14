plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.todolist"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.todolist"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 添加资源配置
        resConfigs("zh-rCN", "en")
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
        encoding = "UTF-8"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.room:room-runtime:2.5.0")
    annotationProcessor("androidx.room:room-compiler:2.5.0")  // Java 使用此方式
    
    // Parse SDK依赖
    implementation("com.github.parse-community:Parse-SDK-Android:4.3.0")
    
    // CircleImageView - 圆形头像库
    implementation("de.hdodenhof:circleimageview:3.1.0")
    
    implementation("androidx.work:work-runtime:2.9.0")
    implementation(libs.room.common.jvm)
    implementation(libs.room.runtime.android)
    
    // OkHttp 网络请求库
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    
    // Gson for JSON conversion
    implementation("com.google.code.gson:gson:2.9.1")
    
    // MPAndroidChart - 图表库
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:4.8.0")
    testImplementation("org.robolectric:robolectric:4.9")
    
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}