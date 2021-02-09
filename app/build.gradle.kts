plugins {
    `android-application`
    kotlin("android")
    kotlin("kapt")
    hilt
}

androidAppConfig {
    defaultConfig {
        applicationId = "nick.filefun"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(Dependency.activity)
    implementation(Dependency.appCompat)
    implementation(Dependency.coreKtx)
    implementation(Dependency.constraintLayout)
    implementation(Dependency.material)
    implementation(Dependency.recyclerView)
    implementation(Dependency.Navigation.runtime)
    implementation(Dependency.Navigation.fragment)
    implementation(Dependency.Navigation.ui)
    implementation(Dependency.Dagger.runtime)
    implementation(Dependency.Dagger.Hilt.runtime)
    implementation(Dependency.multidex)
    implementation(Dependency.coil)
    implementation(Dependency.security)
    implementation(Dependency.savedState)

    kapt(Dependency.Dagger.compiler)
    kapt(Dependency.Dagger.Hilt.compiler)
}