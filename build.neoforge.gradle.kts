// NeoForge targets ("<minecraft>-neoforge" in settings.gradle.kts). The shared src tree is the same
// as for Fabric; Stonecutter's "fabric"/"neoforge" constants select the loader-specific code.
plugins {
    id("net.neoforged.moddev")
}

val minecraftVersion = sc.current.version
version = property("mod_version") as String
group = property("group") as String
base.archivesName = "betterxray-$minecraftVersion-neoforge"

val javaVersion = property("java_version").toString().toInt()

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
val validation = sourceSets.create("neoforgeValidation") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations.named("neoforgeValidationImplementation") { extendsFrom(configurations.implementation.get()) }
configurations.named("neoforgeValidationCompileOnly") { extendsFrom(configurations.compileOnly.get()) }
tasks.named<ProcessResources>("processNeoforgeValidationResources") { exclude("META-INF/mods.toml", "pack.mcmeta") }

// Sodium ships as a container jar with its own mod locator: compile against its extracted inner
// jars; dev runs install the real release jar into run/mods exactly like a player would (-Psodium).
val sodiumBundle = configurations.create("sodiumBundle") { isTransitive = false }

val extractSodium = tasks.register<Copy>("extractSodium") {
    from({ zipTree(sodiumBundle.singleFile) }) {
        include("META-INF/jarjar/*.jar")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("sodium-libs"))
}

// -Pentityculling installs Entity Culling the same way (EntityCullingCompat reaches it by reflection).
val entityCullingJar = configurations.create("entityCullingJar") { isTransitive = false }

val syncDevMods = tasks.register("syncDevMods") {
    val withSodium = project.hasProperty("sodium")
    val withEntityCulling = project.hasProperty("entityculling")
    if (withEntityCulling) inputs.files(entityCullingJar)
    val runDirs = listOf(project.file("run"), project.file("run-validation"))
    if (withSodium) inputs.files(sodiumBundle)
    outputs.upToDateWhen { false }
    doLast {
        for (runDir in runDirs) {
            val modsDir = runDir.resolve("mods")
            modsDir.mkdirs()
            modsDir.listFiles()?.filter { it.name.startsWith("sodium-") }?.forEach { it.delete() }
            if (withSodium) project.copy { from(sodiumBundle); into(modsDir) }
            modsDir.listFiles()?.filter { it.name.startsWith("entityculling-") }?.forEach { it.delete() }
            if (withEntityCulling) project.copy { from(entityCullingJar); into(modsDir) }
            // Dev runs enable NeoForge's Blaze3D validation layer (off in production). Its wrapped
            // textures crash Sodium's chunk renderer (cast to GlTexture), so it stays off with Sodium.
            val clientConfig = runDir.resolve("config/neoforge-client.toml")
            val setting = "enableB3DValidationLayer = ${!withSodium}"
            val pattern = Regex("(?m)^enableB3DValidationLayer\\s*=.*$")
            if (clientConfig.exists()) clientConfig.writeText(clientConfig.readText().replace(pattern, setting))
            else if (withSodium) { clientConfig.parentFile.mkdirs(); clientConfig.writeText(setting + "\n") }
        }
    }
}

dependencies {
    sodiumBundle("maven.modrinth:sodium:${property("sodium_version")}")
    if (project.hasProperty("entityculling")) entityCullingJar("maven.modrinth:entityculling:${property("entityculling_version")}")
    compileOnly(fileTree(layout.buildDirectory.dir("sodium-libs")) { include("*.jar") }.builtBy(extractSodium))
}

neoForge {
    version = property("neo_version").toString()
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
            // Own directory: client-only dev mods in run/mods (e.g. Sodium) must not reach the server.
            gameDirectory = project.file("run-server")
            programArgument("--nogui")
        }
        // Scripted client run with the fixture mod: ./gradlew :<target>:runValidation [-Psodium]
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

// Keep the validation mod out of the normal client/server runs.
afterEvaluate {
    val main = neoForge.mods.getByName("betterxray")
    neoForge.runs.named("client") { loadedMods.set(listOf(main)) }
    neoForge.runs.named("server") { loadedMods.set(listOf(main)) }
    neoForge.runs.named("validation") { loadedMods.set(listOf(main, neoForge.mods.getByName("betterxray_validation"))) }
}

tasks.matching { it.name == "runClient" || it.name == "runValidation" }.configureEach { dependsOn(syncDevMods) }

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraftVersion,
        "neo_version_range" to project.property("neo_version_range").toString(),
        "sodium_range" to project.property("sodium_range").toString(),
        "java_version" to javaVersion.toString(),
        // NeoForge 26.x reads the square mod icon from iconFile and warns about logoFile.
        "icon_key" to if (stonecutter.eval(minecraftVersion, ">=26.1")) "iconFile" else "logoFile"
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching(listOf("META-INF/neoforge.mods.toml", "*.mixins.json")) { expand(props) }
    exclude("fabric.mod.json", "META-INF/mods.toml", "pack.mcmeta")
    // Core shader for faint block entities: only the JSON-shader era (before 1.21.2) uses it.
    if (stonecutter.eval(minecraftVersion, ">=1.21.2")) exclude("assets/betterxray/shaders/**")
    else exclude("assets/betterxray/shaders/core/faint_model_legacy.json")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_${base.archivesName.get()}" }
    }
}
