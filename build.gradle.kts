@file:Suppress("DEPRECATION")

plugins {
    id("dev.kikugie.loom-back-compat")
}

version = property("mod_version") as String
base.archivesName = "betterxray-${sc.current.version}"

repositories {
    maven("https://api.modrinth.com/maven")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modCompileOnly("maven.modrinth:sodium:${property("sodium_version")}")
    modCompileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")

    if (hasProperty("sodium")) {
        modRuntimeOnly("maven.modrinth:sodium:${property("sodium_version")}")
    }
    if (hasProperty("modmenu")) {
        modRuntimeOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
    }
    // -Pentityculling runs Entity Culling (EntityCullingCompat); Better X-Ray only reaches it by reflection.
    if (hasProperty("entityculling")) {
        val entityCulling = "maven.modrinth:entityculling:${property("entityculling_version")}"
        modRuntimeOnly(entityCulling)
        // Remapping (obfuscated targets) drops the nested-jar list from its fabric.mod.json, so its
        // bundled TRansition/TRender libraries are put on the dev classpath themselves.
        // Resolved lazily: Loom must have set up its repositories first.
        if (stonecutter.eval(sc.current.version, "<26.1")) modRuntimeOnly(files(provider {
            val bundle = configurations.detachedConfiguration(dependencies.create(entityCulling)).apply { isTransitive = false }
            val libs = layout.buildDirectory.dir("entityculling-libs").get().asFile
            copy {
                from(zipTree(bundle.singleFile)) { include("META-INF/jars/*.jar"); eachFile { path = name } }
                into(libs)
                includeEmptyDirs = false
            }
            fileTree(libs) { include("*.jar") }.files
        }))
    }
    // Shader support: only targets with a pinned Iris version compile against it.
    // -Piris runs Iris (and the Sodium it requires); -PshaderPack=<file in run/shaderpacks> selects a pack.
    if (hasProperty("iris_version")) {
        modCompileOnly("maven.modrinth:iris:${property("iris_version")}")
        if (hasProperty("iris")) {
            modRuntimeOnly("maven.modrinth:iris:${property("iris_version")}")
            if (!hasProperty("sodium")) modRuntimeOnly("maven.modrinth:sodium:${property("sodium_version")}")
        }
    }
}

val javaVersion = property("java_version").toString().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_${base.archivesName.get()}" }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to sc.current.version,
        // Declared minimums; default to the versions the build was compiled and tested against.
        "loader_version" to (project.findProperty("fabric_loader_min") ?: project.property("fabric_loader_version")).toString(),
        "fabric_api_version" to (project.findProperty("fabric_api_min") ?: project.property("fabric_api_version")).toString(),
        "sodium_breaks" to project.property("sodium_breaks").toString(),
        // Tested Iris range for shader support; empty on targets without it.
        "iris_supported" to (project.findProperty("iris_supported") ?: "").toString(),
        "java_version" to javaVersion.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "pack.mcmeta")
    // Core shader for faint block entities: only the JSON-shader era (before 1.21.2) uses it.
    if (stonecutter.eval(sc.current.version, ">=1.21.2")) exclude("assets/betterxray/shaders/**")
    else if (stonecutter.eval(sc.current.version, ">=1.21")) exclude("assets/betterxray/shaders/core/faint_model_legacy.json")
}

// Fabric's client gametest API exists from 1.21.4 on; older targets use the validation fixture.
val hasClientGameTests = stonecutter.eval(sc.current.version, ">=1.21.4")

if (hasClientGameTests) {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "betterxray-test"
            enableGameTests = false
            enableClientGameTests = true
            eula = true
        }
    }

    // Client gametests must not contact the real update server; UpdateCheckTest points the checker at a local file.
    loom.runs.matching { it.name == "clientGameTest" }.configureEach {
        property("betterxray.updateCheck.url", "off")
    }

    // Shader baseline: copies the packs from <root>/run/shaderpacks into the freshly
    // deleted gametest run directory and selects -PshaderPack there; only ShaderBaselineTest runs.
    if (hasProperty("iris") && hasProperty("shaderPack")) {
        val pack = property("shaderPack").toString()
        val gameTestDir = layout.buildDirectory.dir("run/clientGameTest")
        val syncShaderPacks = tasks.register("syncShaderPacks") {
            mustRunAfter("deleteGameTestRunDir")
            doLast {
                val dir = gameTestDir.get().asFile
                File(dir, "config").mkdirs()
                // "none": the same scenes with Iris loaded but no pack, as the reference.
                if (pack == "none") {
                    File(dir, "config/iris.properties").writeText("enableShaders=false\ndisableUpdateMessage=true\n")
                    return@doLast
                }
                copy { from(rootProject.file("run/shaderpacks")) { include(pack) }; into(File(dir, "shaderpacks")) }
                if (!File(dir, "shaderpacks/$pack").exists()) throw GradleException("Missing run/shaderpacks/$pack")
                File(dir, "config/iris.properties").writeText("enableShaders=true\nshaderPack=$pack\ndisableUpdateMessage=true\n")
            }
        }
        tasks.matching { it.name == "runClientGameTest" }.configureEach { dependsOn(syncShaderPacks) }
        loom.runs.matching { it.name == "clientGameTest" }.configureEach {
            property("betterxray.test.shaderPack", pack)
            if (hasProperty("opacityPatch")) property("betterxray.test.opacityPatch", "true")
            if (hasProperty("irisDebug")) property("betterxray.debug.dumpShaders", "true")
        }
    }

    // Obfuscated targets (1.21.11): the same gametests against the remapped release jar in a real
    // intermediary-mapped client with the published Fabric API (and optionally Sodium) jars.
    if (stonecutter.eval(sc.current.version, "<26.1")) {
        val gametestJar = tasks.register<Jar>("gametestJar") {
            from(sourceSets.getByName("gametest").output)
            archiveClassifier = "gametest-dev"
            destinationDirectory = layout.buildDirectory.dir("devlibs")
        }
        val remapGametestJar = tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapGametestJar") {
            inputFile = gametestJar.flatMap { it.archiveFile }
            classpath.from(sourceSets.main.get().output)
            archiveClassifier = "gametest"
            destinationDirectory = layout.buildDirectory.dir("gametest-libs")
        }
        val prodMods = configurations.create("prodGameTestMods") { isTransitive = false }
        dependencies {
            prodMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
            // The gametest modules are not part of the Fabric API jar.
            prodMods(fabricApi.module("fabric-client-gametest-api-v1", property("fabric_api_version").toString()))
            prodMods(fabricApi.module("fabric-gametest-api-v1", property("fabric_api_version").toString()))
            if (hasProperty("sodium")) prodMods("maven.modrinth:sodium:${property("sodium_version")}")
        }
        tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("prodClientGameTest") {
            mods.from(tasks.named("remapJar"), remapGametestJar, prodMods)
            runDir = layout.buildDirectory.dir("run/prodClientGameTest")
            jvmArgs.addAll("-Dfabric.client.gametest", "-Dbetterxray.updateCheck.url=off",
                "-Dfabric.client.gametest.testModResourcesPath=" + rootProject.file("src/gametest/resources").absolutePath)
            useXVFB = false
        }
    }
} else {
    // Scripted client run (own test mod, screenshots, pixel checks) for versions without the gametest API.
    val validation = sourceSets.create("validation") {
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    }
    loom {
        mods {
            register("betterxray") { sourceSet(sourceSets.main.get()) }
            register("betterxray_validation") { sourceSet(validation) }
        }
        runs {
            register("validation") {
                client()
                source(validation)
                runDir("run-validation")
                property("betterxray.validation", "true")
                // The test JVM ships a newer LWJGL than 1.20.1 pins; Sodium 0.5 would refuse to start.
                property("sodium.checks.issue2561", "false")
                if (project.hasProperty("persistence")) property("betterxray.persistence", project.property("persistence").toString())
                if (project.hasProperty("realUpdateCheck")) property("betterxray.validation.realUpdateCheck", "true")
            }
        }
    }
    // Production check: the remapped release jar and a remapped fixture jar in a real
    // intermediary-mapped client with the published Fabric API (and optionally Sodium) jars.
    val validationJar = tasks.register<Jar>("validationJar") {
        from(validation.output)
        archiveClassifier = "validation-dev"
        destinationDirectory = layout.buildDirectory.dir("devlibs")
    }
    val remapValidationJar = tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapValidationJar") {
        inputFile = validationJar.flatMap { it.archiveFile }
        classpath.from(sourceSets.main.get().output)
        archiveClassifier = "validation"
        destinationDirectory = layout.buildDirectory.dir("validation-libs")
    }
    val prodMods = configurations.create("prodValidationMods") { isTransitive = false }
    dependencies {
        prodMods("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
        if (hasProperty("sodium")) prodMods("maven.modrinth:sodium:${property("sodium_version")}")
    }
    tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("prodValidation") {
        mods.from(tasks.named("remapJar"), remapValidationJar, prodMods)
        runDir = layout.projectDirectory.dir("run-prod-validation")
        jvmArgs.addAll("-Dbetterxray.validation=true", "-Dsodium.checks.issue2561=false")
        useXVFB = false
    }
}

if (hasProperty("vulkan")) {
    loom.runs.configureEach {
        programArgs("--graphicsBackend", "VULKAN")
    }
}

if (hasProperty("persistence")) {
    loom.runs.configureEach {
        property("betterxray.test.persistence", project.property("persistence").toString())
    }
    if (hasClientGameTests && (project.property("persistence") == "verify" || project.property("persistence") == "keymigration")) {
        tasks.named("deleteGameTestRunDir").configure { enabled = false }
    }
}
