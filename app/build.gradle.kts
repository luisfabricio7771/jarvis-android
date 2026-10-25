import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.jarvis.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.jarvis.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildTypes { release { isMinifyEnabled = false } }
    androidResources { noCompress += listOf("onnx", "zip") }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

val prepareVoiceModels by tasks.registering {
    val destination = layout.projectDirectory.dir("src/main/assets")
    val models = listOf(
        Triple("wake-model.zip", "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip", "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"),
        Triple("silero_vad.onnx", "https://raw.githubusercontent.com/snakers4/silero-vad/v5.1/src/silero_vad/data/silero_vad.onnx", "2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f")
    )
    outputs.files(models.map { destination.file(it.first) })
    doLast {
        destination.asFile.mkdirs()
        fun sha(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        for ((name, url, expected) in models) {
            val target = destination.file(name).asFile
            if (!target.exists() || sha(target) != expected) {
                val partial = File(target.path + ".part")
                val connection = URI(url).toURL().openConnection().apply { connectTimeout = 30000; readTimeout = 180000 }
                connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(sha(partial) == expected) { "Model checksum mismatch: $name" }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            check(sha(target) == expected)
        }
    }
}
tasks.named("preBuild").configure { dependsOn(prepareVoiceModels) }
