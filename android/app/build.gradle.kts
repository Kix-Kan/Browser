import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
}

val versionPins = Properties().apply {
    rootProject.projectDir.parentFile.resolve("VERSION.env").inputStream().use { load(it) }
}
val geckoViewVersion = requireNotNull(versionPins.getProperty("ANDROID_GECKOVIEW_VERSION"))
val generatedStartAssets = layout.buildDirectory.dir("generated/kixkan-start-assets")

abstract class SyncKixKanStartTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFile
    abstract val symbolFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun sync() {
        fileSystemOperations.sync {
            from(sourceDirectory)
            from(symbolFile) { rename { "kixkan-symbol.png" } }
            into(outputDirectory.dir("kixkan-start"))
        }
    }
}

val syncKixKanStart = tasks.register<SyncKixKanStartTask>("syncKixKanStart") {
    // Android owns this page; never rewrite the dirty shared desktop start page.
    sourceDirectory.set(rootProject.projectDir.resolve("start"))
    symbolFile.set(layout.projectDirectory.file("src/main/res/drawable-nodpi/kixkan_symbol.png"))
    outputDirectory.set(generatedStartAssets)
}

android {
    namespace = "com.kixkan.browser.prototype"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.kixkan.browser.prototype"
        minSdk = 26
        targetSdk = 37
        versionCode = requireNotNull(versionPins.getProperty("ANDROID_APP_VERSION_CODE")).toInt()
        versionName = requireNotNull(versionPins.getProperty("ANDROID_APP_VERSION"))
        buildConfigField("String", "KIXKAN_API_BASE_URL", "\"https://kixkan.com\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(syncKixKanStart) { task ->
        task.outputDirectory
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoViewVersion")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
