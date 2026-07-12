// Top-level build file where you can add configuration options common to all sub-projects/modules.
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt.yml")
    baseline = file("$rootDir/config/detekt-baseline.xml")
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "11"
    setSource(
        files(
            "app/src/main/java",
            "app/src/test/java",
            "app/src/androidTest/java",
        ),
    )
    include("**/*.kt")
    exclude("**/build/**")
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.register<DetektCreateBaselineTask>("detektBaselineMain") {
    description = "Creates detekt baseline."
    group = "verification"
    jvmTarget = "11"
    setSource(
        files(
            "app/src/main/java",
            "app/src/test/java",
            "app/src/androidTest/java",
        ),
    )
    include("**/*.kt")
    exclude("**/build/**")
    baseline.set(file("$rootDir/config/detekt-baseline.xml"))
    config.setFrom(files("$rootDir/config/detekt.yml"))
    buildUponDefaultConfig.set(true)
}
