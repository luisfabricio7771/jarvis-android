plugins {
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

// Windows OneDrive can lock generated directories while Gradle replaces them.
// build.ps1 selects an isolated temporary build directory; source stays in this repo.
val jarvisBuildRoot = providers.environmentVariable("JARVIS_ANDROID_BUILD_ROOT")
subprojects {
    if(jarvisBuildRoot.isPresent) layout.buildDirectory.set(file(jarvisBuildRoot.get()+"/"+name))
}
