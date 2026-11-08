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
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "CONTENT_BASE_URL",
            "\"https://raw.githubusercontent.com/$contentOwner/$contentRepo/$contentBranch/$contentPath\"",
        )
        buildConfigField(
            "String", "CONTENT_FALLBACK_URL",
            "\"https://cdn.jsdelivr.net/gh/$contentOwner/$contentRepo@$contentBranch/$contentPath\"",
        )
    }

    buildTypes {
        release {
            // Personal app: release is signed with the debug key so it can be side-loaded.
            signingConfig = signingConfigs.getByName("debug")
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
