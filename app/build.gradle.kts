import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.GradleException
import java.io.IOException
import java.util.Properties

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("room.schemaLocation=${schemaDir.path}")
}

val uploadSigningProperties = Properties()
val uploadSigningPropertiesFile = rootProject.file("key.properties")
if (uploadSigningPropertiesFile.isFile) {
    uploadSigningPropertiesFile.inputStream().use(uploadSigningProperties::load)
}

fun uploadSigningValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: uploadSigningProperties.getProperty(propertyName)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

val uploadStoreFilePath = uploadSigningValue(
    environmentName = "CLEANGUARD_UPLOAD_STORE_FILE",
    propertyName = "storeFile",
)
val uploadStorePassword = uploadSigningValue(
    environmentName = "CLEANGUARD_UPLOAD_STORE_PASSWORD",
    propertyName = "storePassword",
)
val uploadKeyAlias = uploadSigningValue(
    environmentName = "CLEANGUARD_UPLOAD_KEY_ALIAS",
    propertyName = "keyAlias",
)
val uploadKeyPassword = uploadSigningValue(
    environmentName = "CLEANGUARD_UPLOAD_KEY_PASSWORD",
    propertyName = "keyPassword",
)
val uploadSigningValues = listOf(
    uploadStoreFilePath,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
)
val isUploadSigningConfigured = uploadSigningValues.all { it != null }
check(uploadSigningValues.all { it == null } || isUploadSigningConfigured) {
    "Upload signing is only partially configured. Provide all CLEANGUARD_UPLOAD_* " +
        "environment variables or every property in the ignored key.properties file."
}

val uploadStoreFile = uploadStoreFilePath?.let { configuredPath ->
    val configuredFile = try {
        file(configuredPath).canonicalFile
    } catch (exception: IOException) {
        throw GradleException(
            "Invalid upload keystore path. In key.properties, use forward slashes " +
                "(for example C:/SecureKeys/CleanGuard/cleanguard-upload.jks).",
            exception,
        )
    }
    val repositoryRoot = rootProject.rootDir.canonicalFile.toPath()
    check(!configuredFile.toPath().startsWith(repositoryRoot)) {
        "The upload keystore must be stored outside the Git repository."
    }
    configuredFile
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cleanguard.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cleanguard.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (isUploadSigningConfigured) {
            create("upload") {
                storeFile = uploadStoreFile
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (isUploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

val verifyReleaseSigningConfiguration by tasks.registering {
    group = "verification"
    description = "Verifies that the protected CleanGuard upload signing configuration is complete."

    doLast {
        check(isUploadSigningConfigured) {
            "Release signing is not configured. See docs/release/v0.2/SIGNING-SETUP.md."
        }
        checkNotNull(uploadStoreFile).run {
            check(isFile) { "Configured upload keystore does not exist: $this" }
        }
    }
}

tasks.matching { task ->
    task.name == "assembleRelease" || task.name == "bundleRelease"
}.configureEach {
    dependsOn(verifyReleaseSigningConfiguration)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg(RoomSchemaArgProvider(file("$projectDir/schemas")))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(platform(libs.kotlinx.serialization.bom))
    ksp(libs.androidx.room.compiler)
    add("benchmarkImplementation", libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
