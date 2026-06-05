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
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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
                implementation(libs.kotlin.reflect)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
        "enumPropertyNaming" to "UPPERCASE",
        // Without this, every generated file embeds the current timestamp, busting the
        // Gradle build cache for all downstream api-client compile tasks on every run.
        "hideGenerationTimestamp" to "true"
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

// Patch generated code to fix OpenAPI Generator bug with oneOf discriminated unions.
// The generator only extracts ASSET from the Type enum, missing LINK and TEXT.
// Whitespace between tokens is matched with \s+ so the patch is line-ending agnostic:
// CI checks this script out as CRLF (autocrlf, no .gitattributes) while the generated
// file is LF, so a regex with literal newlines would silently fail to match there.
// The task is idempotent and throws if it can neither patch nor confirm an existing
// patch, so a generator format change fails the build instead of shipping unpatched.
val patchOpenApiClient by tasks.registering {
    dependsOn("openApiGenerate")

    doLast {
        val targetFile = file("${generatedSourcesDir.get().asFile}/src/main/kotlin/com/karakept/api/model/BookmarksPostRequest.kt")
        if (!targetFile.exists()) {
            throw GradleException("Cannot patch Type enum: $targetFile not found")
        }

        val content = targetFile.readText()
        val incompleteEnum = Regex(
            """enum class Type\(val value: kotlin\.String\)\s+\{\s+@SerialName\(value = "asset"\) ASSET\("asset"\);\s+\}"""
        )
        val completeEnum = """enum class Type(val value: kotlin.String) {
        @SerialName(value = "link") LINK("link"),
        @SerialName(value = "text") TEXT("text"),
        @SerialName(value = "asset") ASSET("asset");
    }"""

        when {
            content.contains("""LINK("link")""") ->
                println("ℹ️ BookmarksPostRequest.Type already contains LINK; no patch needed")
            incompleteEnum.containsMatchIn(content) -> {
                targetFile.writeText(incompleteEnum.replace(content, completeEnum))
                println("✅ Patched BookmarksPostRequest.Type enum with LINK and TEXT values")
            }
            else -> throw GradleException(
                "Failed to patch BookmarksPostRequest.Type: neither the LINK value nor the expected " +
                    "ASSET-only enum was found. The OpenAPI generator output format may have changed."
            )
        }
    }
}

// Make compilation depend on code generation and patching
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("patchOpenApiClient")
}
