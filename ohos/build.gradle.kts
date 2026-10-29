import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    `maven-publish`
}
fun readMap(path: File) = (JsonSlurper().parse(path) as Map<*, *>).entries.associate { it.key.toString() to it.value }
fun asMap(value: Any?) = (value as Map<*, *>).entries.associate { it.key.toString() to requireNotNull(it.value) }
fun sha(path: File) = MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }
fun leaves(path: File): List<File> = if (path.isDirectory) path.walkTopDown().filter(File::isFile).toList() else listOf(path)
fun records(files: Iterable<File>) = files.filter(File::isFile).distinct().sortedBy { it.path }.map {
    mapOf("path" to it.absolutePath, "size" to it.length(), "sha256" to sha(it))
}
val config = readMap(file("port-config.json"))
val model = asMap(asMap(config.getValue("modules")).getValue("compose-charts"))
val sourceInputs = readMap(file("source-inputs.json"))
val pins = readMap(file("dependency-pins.json"))
val metadataPins = if (file("metadata-pins.json").isFile) readMap(file("metadata-pins.json")) else emptyMap()
val compilerPluginTools = readMap(file("compiler-plugin-toolchain.json"))
val compilerPluginPins = asMap(compilerPluginTools.getValue("pins"))
val compilerPluginArtifacts = asMap(compilerPluginTools.getValue("artifacts"))
val toolRoot = file(providers.gradleProperty("portTooling").get()).resolve("toolchains/kotlin-native/2.4.20-0.1.0-08")
val metadataTools = readMap(toolRoot.resolve("metadata-toolchain.json"))
val commonizerTools = readMap(toolRoot.resolve("commonizer-toolchain.json"))
val nativeTools = readMap(toolRoot.resolve("native-compiler-toolchain.json"))
val commonizerPins = asMap(metadataTools.getValue("launcherPins")) + asMap(commonizerTools.getValue("pins"))
val commonizerArtifacts = asMap(metadataTools.getValue("launcherArtifacts")) + asMap(commonizerTools.getValue("artifacts"))
val nativePins = asMap(nativeTools.getValue("pins"))
val nativeArtifacts = asMap(nativeTools.getValue("artifacts"))
val fingerprint = providers.gradleProperty("portFingerprint").get()
val work = file(providers.gradleProperty("portWork").get()).canonicalFile
check(fingerprint.matches(Regex("[0-9a-f]{64}")))
val manifestFile = work.resolve("source-manifest.json")
val manifest = readMap(manifestFile)
check(manifest["fingerprint"] == fingerprint)
group = model.getValue("group").toString()
version = providers.gradleProperty("portVersion").get()
layout.buildDirectory.set(work.resolve("build"))
kotlin {
    ohosX64()
    ohosArm64()
    sourceSets {
        val commonMain by getting {
            kotlin.setSrcDirs(listOf(work.resolve("sources/commonMain")))
            dependencies {
                // 保留官方单一 Foundation implementation 声明与 KGP 默认 stdlib。
                implementation("org.jetbrains.compose.foundation:foundation:1.12.0-0.1.0-11")
            }
        }
        val nativeMain = maybeCreate("nativeMain").apply {
            dependsOn(commonMain)
            kotlin.setSrcDirs(listOf(work.resolve("sources/nativeMain")))
        }
        getByName("ohosX64Main").dependsOn(nativeMain)
        getByName("ohosArm64Main").dependsOn(nativeMain)
        matching { it.name.endsWith("Test") }.configureEach { kotlin.setSrcDirs(emptyList<File>()) }
    }
}
configurations.configureEach {
    if (name == nativeTools["configuration"] || name == commonizerTools["configuration"]) {
        val fixedPins = if (name == nativeTools["configuration"]) nativePins else commonizerPins
        resolutionStrategy.eachDependency {
            val ga = "${requested.group}:${requested.name}"
            val fixed = fixedPins[ga]?.toString() ?: error("Unpinned compiler host dependency: $ga")
            if (requested.version != fixed) useVersion(fixed)
        }
    }
    val metadata = name.endsWith("ResolvableDependenciesMetadata") || name == "allSourceSetsCompileDependenciesMetadata"
    val runtime = metadata || name.endsWith("CompileKlibraries") || name.endsWith("CompilationDependenciesMetadata")
    if (runtime && !name.contains("Test", ignoreCase = true)) resolutionStrategy.eachDependency {
        val ga = "${requested.group}:${requested.name}"
        val fixed = (if (metadata) metadataPins[ga] ?: pins[ga] else pins[ga])?.toString()
            ?: error("Unpinned Charts dependency: $requested")
        if (requested.version != fixed) useVersion(fixed)
    }
}
fun evidence(name: String, value: Any) {
    val target = work.resolve("evidence/$name.json")
    target.parentFile.mkdirs()
    target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(value)) + "\n")
}
fun edges(configuration: Configuration) = configuration.incoming.resolutionResult.allDependencies.map { edge ->
    if (edge !is ResolvedDependencyResult) throw GradleException("Unresolved Charts dependency: $edge", (edge as? UnresolvedDependencyResult)?.failure)
    mapOf("from" to edge.from.id.displayName, "requested" to edge.requested.displayName, "selected" to edge.selected.id.displayName)
}
fun checkedTools(name: String, fixed: Map<String, Any>, expected: Map<String, Any>): Map<String, Any> {
    val configuration = configurations.getByName(name)
    val artifacts = configuration.resolvedConfiguration.resolvedArtifacts.map { artifact ->
        val id = artifact.moduleVersion.id
        val ga = "${id.group}:${id.name}"
        check(fixed[ga] == id.version) { "Host tool version differs: $id" }
        val item = asMap(expected.getValue(ga))
        check(sha(artifact.file) == item["sha256"] && artifact.file.length() == (item.getValue("size") as Number).toLong()) { "Host tool bytes differ: $id" }
        mapOf("gav" to id.toString(), "files" to records(listOf(artifact.file)))
    }
    check(artifacts.map { it.getValue("gav") }.toSet() == fixed.map { "${it.key}:${it.value}" }.toSet()) { "Host tool set differs: $name" }
    return mapOf("configuration" to name, "edges" to edges(configuration), "artifacts" to artifacts)
}
fun toolRecords() = mapOf("commonizer" to checkedTools(commonizerTools.getValue("configuration").toString(), commonizerPins, commonizerArtifacts),
    "nativeCompiler" to checkedTools(nativeTools.getValue("configuration").toString(), nativePins, nativeArtifacts))

fun toolchain() = mapOf(
    "hostTools" to toolRecords(),
    "gradle" to gradle.gradleVersion,
    "javaVersion" to System.getProperty("java.version"),
    "javaRuntimeVersion" to System.getProperty("java.runtime.version"),
    "javaHome" to System.getProperty("java.home"),
    "nativeHome" to providers.gradleProperty("kotlin.native.home").get(),
    "konanDataDir" to System.getenv("KONAN_DATA_DIR"),
    "gradleUserHome" to gradle.gradleUserHomeDir.absolutePath,
    "temp" to System.getenv("TEMP"),
    "plugins" to buildscript.configurations.getByName("classpath").resolvedConfiguration.resolvedArtifacts.map {
        mapOf("component" to it.moduleVersion.id.toString(), "files" to records(listOf(it.file)))
    },
)
fun resolution() = listOf("commonMainResolvableDependenciesMetadata", "nativeMainResolvableDependenciesMetadata", "ohosX64CompileKlibraries", "ohosArm64CompileKlibraries").filter { configurations.findByName(it) != null }.map { name ->
    val configuration = configurations.getByName(name)
    val metadata = name.endsWith("ResolvableDependenciesMetadata")
    mapOf("configuration" to name, "edges" to configuration.incoming.resolutionResult.allDependencies.map { edge ->
        if (edge !is ResolvedDependencyResult) throw GradleException("Unresolved Charts dependency: $edge", (edge as? UnresolvedDependencyResult)?.failure)
        mapOf("from" to edge.from.id.displayName, "requested" to edge.requested.displayName, "selected" to edge.selected.id.displayName)
    }, "artifacts" to configuration.incoming.artifacts.artifacts.map { artifact ->
        val id = artifact.id.componentIdentifier
        check(id is ModuleComponentIdentifier)
        val ga = "${id.group}:${id.module}"
        check((if (metadata) metadataPins[ga] ?: pins[ga] else pins[ga]) == id.version)
        val attrs = artifact.variant.attributes.keySet().associate { it.name to artifact.variant.attributes.getAttribute(it).toString() }
        if (metadataPins.containsKey(ga)) {
            val common = attrs["org.gradle.usage"] == "kotlin-metadata" && attrs["org.jetbrains.kotlin.platform.type"] == "common"
            val javaAlias = ga == "androidx.arch.core:core-common" && id.version == "2.2.0" &&
                attrs["org.gradle.usage"] in listOf("java-api", "java-runtime") &&
                attrs["org.jetbrains.kotlin.platform.type"] in listOf(null, "jvm") && attrs["org.gradle.category"] == "library"
            check(metadata && (common || javaAlias) && attrs["org.jetbrains.kotlin.native.target"] == null) {
                "Charts metadata supplement selected outside common metadata: $id"
            }
        }
        mapOf("component" to id.displayName, "attributes" to attrs, "files" to records(leaves(artifact.file)))
    })
}
tasks.register("resolveCharts") {
    doLast {
        check(gradle.gradleVersion == config.getValue("gradle") && System.getProperty("java.specification.version") == "17")
        listOf("compileCommonMainKotlinMetadata", "allMetadataJar", "compileKotlinOhosX64", "compileKotlinOhosArm64").forEach { tasks.getByName(it) }
        evidence("resolve", mapOf("fingerprint" to fingerprint, "toolchain" to toolchain(),
            "configurations" to resolution()))
    }
}
fun recordStage(name: String, target: String?) {
    tasks.register(name) {
        val compilerName = if (target == null) "compileCommonMainKotlinMetadata" else "compileKotlin$target"
        val producerName = compilerName
        dependsOn(producerName)
        if (target == null) dependsOn("allMetadataJar")
        outputs.upToDateWhen { false }
        doLast {
            val compiler = tasks.getByName(compilerName) as KotlinNativeCompile
            val sources = compiler.sources.files.filter { it.isFile && it.extension == "kt" }.map { it.canonicalFile }.toSet()
            val pluginFiles = compiler.compilerPluginClasspath?.files.orEmpty()
            val pluginIds = listOf("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable")
            val expectedPlugins = pluginIds.map { id ->
                check(compilerPluginPins[id] == config["kotlin"])
                val item = asMap(compilerPluginArtifacts.getValue(id))
                (item.getValue("size") as Number).toLong() to item.getValue("sha256").toString()
            }.toSet()
            check(expectedPlugins.size == pluginIds.size && pluginFiles.size == pluginIds.size &&
                pluginFiles.map { it.length() to sha(it) }.toSet() == expectedPlugins) {
                "Charts compiler plugin set differs"
            }
            // 从配方中的官方清单推导路径与hash，不把可变work manifest当作原源真值。
            val originalMembers = (sourceInputs.getValue("sourceMembers") as List<*>).map(::asMap)
            val stageMembers = originalMembers.filter { target != null || it.getValue("member").toString().startsWith("commonMain/") }
            val expected = stageMembers.associate { work.resolve("sources/" + it.getValue("member")).canonicalFile to it.getValue("sha256").toString() }
            check(expected.size == (if (target == null) 41 else 42) && sources == expected.keys && expected.all { (path, hash) -> sha(path) == hash }) {
                "Complete Charts compiler source set or original bytes differ"
            }
            val producers = listOf(tasks.getByName(producerName)) + if (target == null) listOf(tasks.getByName("allMetadataJar")) else emptyList()
            producers.forEach { producer ->
                val state = producer.state
                check(state.executed && producer.enabled && !state.noSource && state.failure == null &&
                    state.skipMessage in listOf(null, "UP-TO-DATE", "FROM-CACHE")) { "Charts producer did not execute: ${producer.path}" }
                check(records(producer.outputs.files.files.flatMap(::leaves)).any { (it.getValue("size") as Long) > 0 })
            }
            val stage = if (target == null) "metadata" else if (target == "OhosX64") "native-x64" else "native-arm64"
            evidence(stage, mapOf("fingerprint" to fingerprint, "stage" to stage, "sourceCount" to expected.size,
                "sourceSetSha256" to sourceInputs.getValue(if (target == null) "commonSourceSetSha256" else "sourceSetSha256"), "sourceManifest" to records(listOf(manifestFile)).single(),
                "sources" to records(sources), "publicApi" to sourceInputs.getValue("publicApi"), "toolchain" to toolchain(),
                "explicitApi" to project.extensions.getByType<KotlinMultiplatformExtension>().explicitApi?.name,
                "sourceDependencies" to listOf("implementation" to "commonMainImplementation", "api" to "commonMainApi").associate { (scope, configuration) ->
                    scope to configurations.getByName(configuration).dependencies.map { "${it.group}:${it.name}" }.filter { it != "org.jetbrains.kotlin:kotlin-stdlib" }
                },
                "compilerPlugins" to records(compiler.compilerPluginClasspath?.files.orEmpty()), "pluginOptions" to compiler.compilerPluginCommandLine,
                "metadataLanguageOptions" to if (target == null) mapOf("languageVersion" to compiler.compilerOptions.languageVersion.orNull?.version,
                    "apiVersion" to compiler.compilerOptions.apiVersion.orNull?.version, "optIns" to compiler.compilerOptions.optIn.get().sorted(),
                    "freeCompilerArgs" to compiler.compilerOptions.freeCompilerArgs.get(),
                    "explicitApi" to project.extensions.getByType<KotlinMultiplatformExtension>().explicitApi?.name) else null,
                "metadataLibraries" to if (target == null) compiler.libraries.files.sortedBy { it.path }.map {
                    mapOf("path" to it.absolutePath, "files" to records(leaves(it)))
                } else null,
                "producers" to producers.map { mapOf("task" to it.path, "outputs" to records(it.outputs.files.files.flatMap(::leaves))) },
                "configurations" to resolution(), "runtime" to "NOT_RUN"))
        }
    }
}
recordStage("recordChartsMetadata", null)
recordStage("recordChartsX64", "OhosX64")
recordStage("recordChartsArm64", "OhosArm64")
publishing {
    repositories { maven { name = "Port"; url = uri(providers.gradleProperty("portRepository").get()) } }
    publications.withType<MavenPublication>().configureEach {
        artifact(file("legal/LICENSE")) { classifier = "license"; extension = "txt" }
        artifact(work.resolve("provenance.json")) { classifier = "provenance"; extension = "json" }
        if (name == "kotlinMultiplatform") {
            (sourceInputs.getValue("originalClassifiers") as List<*>).map(::asMap).forEach { original ->
                val key = original.getValue("key").toString()
                artifact(file(asMap(sourceInputs.getValue(key)).getValue("path"))) {
                    classifier = original.getValue("classifier").toString()
                    extension = original.getValue("extension").toString()
                }
            }
        }
        pom {
            name.set("Compose Charts")
            description.set("Complete unmodified Charts 1.0.0 common and native sources for CPF OHOS dual ABI")
            licenses { license { name.set("Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
            scm { url.set(model.getValue("repository").toString()) }
            developers { developer { id.set("ehsannarmani"); name.set("Ehsan Narmani"); email.set("ehsan.enk.narmani@gmail.com") } }
        }
    }
}
tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        check(readMap(work.resolve("provenance.json"))["fingerprint"] == fingerprint)
        (sourceInputs.getValue("rawOriginals") as List<*>).map(::asMap).forEach { original ->
            check(sha(file(original.getValue("path"))) == original.getValue("sha256"))
        }
        listOf("metadata", "native-x64", "native-arm64").forEach { check(readMap(work.resolve("evidence/$it.json"))["fingerprint"] == fingerprint) }
    }
}
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(file("legal")) { into("META-INF/licenses/compose-charts") }
    from(manifestFile) { into("META-INF/upstream") }
}
tasks.register("publishCharts") { dependsOn("publishAllPublicationsToPortRepository") }

// 每个 sources artifact 均包含完整的 common 和 native 原始源码。
tasks.withType<Jar>().matching { it.name.endsWith("SourcesJar", ignoreCase = true) }.configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(work.resolve("sources")) { include("**/*.kt") }
}
