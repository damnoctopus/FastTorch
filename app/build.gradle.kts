import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val releaseProperties = Properties()
val releasePropertiesFile = rootProject.file(".signing/release.properties")
if (releasePropertiesFile.exists()) {
    releasePropertiesFile.inputStream().use { releaseProperties.load(it) }
}
fun signingValue(environment: String, property: String): String? =
    providers.environmentVariable(environment).orNull ?: releaseProperties.getProperty(property)

val releaseStoreFile = signingValue("FASTTORCH_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("FASTTORCH_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("FASTTORCH_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("FASTTORCH_KEY_PASSWORD", "keyPassword")
val releaseSigningAvailable = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
val buildVersionCode = providers.gradleProperty("versionCode").orElse("2").get().toInt()
require(buildVersionCode in 1..2_100_000_000) { "versionCode must be between 1 and 2100000000" }

android {
    namespace = "com.example.fasttorch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.fasttorch"
        minSdk = 28
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = providers.gradleProperty("versionName").orElse("1.1.0").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningAvailable) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    inputs.property("configured", releaseSigningAvailable)
    doLast {
        check(inputs.properties["configured"] as Boolean) {
            "Release signing is missing. Run scripts/setup-signing.ps1 or configure the FASTTORCH signing environment variables."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
