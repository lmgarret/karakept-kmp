import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openapi.generator)
}

val generatedSourcesDir = layout.buildDirectory.dir("generated/openapi")

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        commonMain {
            kotlin.srcDir(generatedSourcesDir.map { it.dir("src/main/kotlin") })
            kotlin.exclude("**/AssetsApi.kt")
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

android {
    namespace = "com.karakept.api"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// OpenAPI Generator Configuration
openApiGenerate {
    generatorName.set("kotlin")
    templateDir.set("${projectDir}/templates")
    inputSpec.set("${rootDir}/karakeep-upstream/packages/open-api/karakeep-openapi-spec.json")
    outputDir.set(generatedSourcesDir.get().asFile.absolutePath)
    packageName.set("com.karakept.api")
    apiPackage.set("com.karakept.api.client")
    modelPackage.set("com.karakept.api.model")
    
    configOptions.set(mapOf(
        "library" to "multiplatform",
        "dateLibrary" to "kotlinx-datetime",
        "useCoroutines" to "true",
        "omitGradleWrapper" to "true",
        "enumPropertyNaming" to "UPPERCASE"
    ))
    
    modelNameMappings.set(mapOf(
        "List" to "KarakeepList"
    ))
    
    typeMappings.set(mapOf(
        "binary" to "ByteArray"
    ))
    
    // Skip models with invalid names
    skipValidateSpec.set(true)
}


// Make compilation depend on code generation and patching
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("openApiGenerate")
}
