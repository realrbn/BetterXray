// Forge 1.20.1 ("1.20.1-forge" in settings.gradle.kts), built with ModDevGradle's legacy plugin
// (ForgeGradle 6 needs Gradle 8). The shared src tree is the same as for Fabric and NeoForge;
// Stonecutter's "forge" constant selects the loader-specific code. Forge 1.20.1 runs on SRG names,
// so the jar is reobfuscated and the mixins get a refmap.
plugins {
    id("net.neoforged.moddev.legacyforge")
}

val minecraftVersion = sc.current.version
version = property("mod_version") as String
group = property("group") as String
base.archivesName = "betterxray-$minecraftVersion-forge"

val javaVersion = property("java_version").toString().toInt()
val mixinExtras = property("mixinextras_version").toString()
val forgeArtifactVersion = "$minecraftVersion-${property("forge_version")}"
val embeddium = property("embeddium_version").toString()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
}

repositories {
    maven("https://api.modrinth.com/maven")
}

// Development validation fixture (separate mod "betterxray_validation", never in the release jar).
// The fixture source set is shared with NeoForge (src/neoforgeValidation, "forge" constant).
val validation = sourceSets.create("neoforgeValidation") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations.named("neoforgeValidationImplementation") { extendsFrom(configurations.implementation.get()) }
configurations.named("neoforgeValidationCompileOnly") { extendsFrom(configurations.compileOnly.get()) }
tasks.named<ProcessResources>("processNeoforgeValidationResources") { exclude("META-INF/neoforge.mods.toml") }

legacyForge {
    enable {
        forgeVersion = forgeArtifactVersion
    }
    addModdingDependenciesTo(validation)

    runs {
        configureEach {
            gameDirectory = project.file("run")
            systemProperty("forge.logging.console.level", "info")
        }
        register("client") {
            client()
        }
        register("server") {
            server()
            gameDirectory = project.file("run-server")
            programArgument("--nogui")
        }
        // Scripted client run with the fixture mod: ./gradlew :1.20.1-forge:runValidation [-Pembeddium]
        register("validation") {
            client()
            gameDirectory = project.file("run-validation")
            systemProperty("betterxray.updateCheck.url", "off")
        }
    }

    mods {
        register("betterxray") {
            sourceSet(sourceSets.main.get())
        }
        register("betterxray_validation") {
            sourceSet(validation)
        }
    }
}

mixin {
    add(sourceSets.main.get(), "betterxray.refmap.json")
    config("betterxray.mixins.json")
    config("betterxray.sodium.mixins.json")
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    // Forge 1.20.1 does not ship MixinExtras: nested as a Jar-in-Jar library.
    compileOnly("io.github.llamalad7:mixinextras-common:$mixinExtras")
    annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtras")
    implementation("io.github.llamalad7:mixinextras-forge:$mixinExtras")
    jarJar("io.github.llamalad7:mixinextras-forge:$mixinExtras")
    // Optional Sodium fork for Forge 1.20.1 (Sodium 0.5 code base): compiled against, -Pembeddium runs it.
    modCompileOnly("maven.modrinth:embeddium:$embeddium")
    if (project.hasProperty("embeddium")) modRuntimeOnly("maven.modrinth:embeddium:$embeddium")
}

// Keep the validation mod out of the normal client/server runs.
afterEvaluate {
    val main = legacyForge.mods.getByName("betterxray")
    legacyForge.runs.named("client") { loadedMods.set(listOf(main)) }
    legacyForge.runs.named("server") { loadedMods.set(listOf(main)) }
    legacyForge.runs.named("validation") { loadedMods.set(listOf(main, legacyForge.mods.getByName("betterxray_validation"))) }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraftVersion,
        "forge_version" to project.property("forge_version").toString(),
        "java_version" to javaVersion.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching(listOf("META-INF/mods.toml", "*.mixins.json")) { expand(props) }
    // Forge 1.20.1 runs on SRG names: the configs point to the generated refmap.
    filesMatching(listOf("betterxray.mixins.json", "betterxray.sodium.mixins.json")) {
        filter { line -> if (line.contains("\"package\"")) "$line\n  \"refmap\": \"betterxray.refmap.json\"," else line }
    }
    exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "betterxray.iris.mixins.json")
    exclude("assets/betterxray/shaders/core/faint_model.json")
}

tasks.named<Jar>("jar") {
    manifest.attributes("MixinConfigs" to "betterxray.mixins.json,betterxray.sodium.mixins.json")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_${base.archivesName.get()}" }
    }
}
