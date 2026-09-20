apply plugin: 'com.android.application'
apply plugin: 'org.jetbrains.kotlin.android'
apply plugin: 'com.google.devtools.ksp'

android {
    namespace 'com.androplay'
    compileSdk 35

    defaultConfig {
        applicationId 'com.androplay'
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName '1.0.0'

        externalNativeBuild {
            cmake {
                cppFlags '-std=c++17 -frtti -fexceptions'
                arguments '-DANDROID_STL=c++_shared'
            }
        }

        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64'
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    buildFeatures {
        compose true
    }

    composeOptions {
        kotlinCompilerExtensionVersion '1.6.0'
    }

    packaging {
        jniLibs {
            useLegacyPackaging true
        }
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'

    def composeBom = platform('androidx.compose:compose-bom:2025.02.00')
    implementation composeBom
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.activity:activity-compose:1.9.0'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.tv:tv-material:1.1.0'
    implementation 'androidx.tv:tv-foundation:1.0.0'

    implementation 'androidx.media3:media3-exoplayer:1.2.1'
    implementation 'androidx.media3:media3-ui:1.2.1'
    implementation 'androidx.media3:media3-session:1.2.1'

    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2'
    implementation 'androidx.lifecycle:lifecycle-service:2.8.2'

    implementation 'com.google.code.gson:gson:2.11.0'

    debugImplementation 'androidx.compose.ui:ui-tooling'
}
