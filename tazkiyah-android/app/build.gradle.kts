import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val contentOwner = providers.gradleProperty("tazkiyah.contentOwner").get()
val contentRepo = providers.gradleProperty("tazkiyah.contentRepo").get()
val contentBranch = providers.gradleProperty("tazkiyah.contentBranch").get()
val contentPath = providers.gradleProperty("tazkiyah.contentPath").getOrElse("")

// Release signing: the upload key lives OUTSIDE the repo, in ~/.gradle/gradle.properties
// (TAZKIYAH_RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD). Without it a
// release build fails instead of silently falling back to the debug key.
val releaseStoreFile = providers.gradleProperty("TAZKIYAH_RELEASE_STORE_FILE").orNull
val wantsRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = false) }
if (wantsRelease && releaseStoreFile == null) {
    throw GradleException(
        "Release signing is not configured: add TAZKIYAH_RELEASE_STORE_FILE, TAZKIYAH_RELEASE_STORE_PASSWORD, " +
            "TAZKIYAH_RELEASE_KEY_ALIAS and TAZKIYAH_RELEASE_KEY_PASSWORD to ~/.gradle/gradle.properties (see README).",
    )
}

android {
    namespace = "com.inovixlabs.tazkiyah"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.inovixlabs.tazkiyah"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "CONTENT_BASE_URL",
            "\"https://raw.githubusercontent.com/$contentOwner/$contentRepo/$contentBranch/$contentPath\"",
        )
        buildConfigField(
            "String", "CONTENT_FALLBACK_URL",
            "\"https://cdn.jsdelivr.net/gh/$contentOwner/$contentRepo@$contentBranch/$contentPath\"",
        )
        // App releases (APK on GitHub Releases, tags app-v<version>) and the source code.
        buildConfigField("String", "GITHUB_REPO", "\"$contentOwner/$contentRepo\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("TAZKIYAH_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("TAZKIYAH_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("TAZKIYAH_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Copies a snapshot of the sibling `tazkiyah-content` repo into the APK as `assets/seed/`,
 * so the first launch shows the catalog even without a network (docs/PLAN.md, "আপডেট মেকানিজম").
 */
abstract class CopyContentSeedTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        val source = contentDir.get().asFile
        fs.sync {
            into(outputDir.dir("seed"))
            if (source.resolve("manifest.json").isFile) {
                from(source) { include("manifest.json", "data/**", "images/**") }
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val copySeed = tasks.register<CopyContentSeedTask>(
            "copy${variant.name.replaceFirstChar { it.uppercase() }}ContentSeed",
        ) {
            contentDir.set(rootProject.layout.projectDirectory.dir("../tazkiyah-content"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copySeed, CopyContentSeedTask::outputDir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.youtube.player.core)
    implementation(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
