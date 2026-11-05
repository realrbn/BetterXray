plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
    id("net.minecraftforge.gradle") version "7.0.40" apply false
}

stonecutter active "26.3" /* [SC] DO NOT EDIT */

tasks.register("buildAll") {
    group = "build"
    description = "Builds every supported Minecraft version."
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}

// Source-level renames between Minecraft generations. Stonecutter rewrites them when the
// active version changes; they keep the shared code free of trivial version conditions.
stonecutter parameters {
    val v = current.version
    // Loader of the current target: "<minecraft>-neoforge" builds with NeoForge, "<minecraft>-forge" with Forge.
    val loader = when {
        current.project.endsWith("-neoforge") -> "neoforge"
        current.project.endsWith("-forge") -> "forge"
        else -> "fabric"
    }
    constants.match(loader, "fabric", "neoforge", "forge")
    // 1.21.11 renamed ResourceLocation to Identifier (Mojang mappings).
    replacements.regex(eval(v, ">=1.21.11")) {
        replace("\\bResourceLocation\\b", "Identifier")
        reverse("\\bIdentifier\\b", "ResourceLocation")
    }
    // 26.1 renamed GuiGraphics to GuiGraphicsExtractor and its drawing methods.
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bGuiGraphics\\b", "GuiGraphicsExtractor")
        reverse("\\bGuiGraphicsExtractor\\b", "GuiGraphics")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bg\\.drawString\\(", "g.text(")
        reverse("\\bg\\.text\\(", "g.drawString(")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bg\\.drawCenteredString\\(", "g.centeredText(")
        reverse("\\bg\\.centeredText\\(", "g.drawCenteredString(")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bg\\.renderOutline\\(", "g.outline(")
        reverse("\\bg\\.outline\\(", "g.renderOutline(")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bg\\.renderItem\\(", "g.item(")
        reverse("\\bg\\.item\\(", "g.renderItem(")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bvoid render\\((GuiGraphics\\w*) g\\b", "void extractRenderState(GuiGraphicsExtractor g")
        reverse("\\bvoid extractRenderState\\((GuiGraphics\\w*) g\\b", "void render(GuiGraphics g")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bsuper\\.render\\(g,", "super.extractRenderState(g,")
        reverse("\\bsuper\\.extractRenderState\\(g,", "super.render(g,")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bvoid renderContent\\((GuiGraphics\\w*) g\\b", "void extractContent(GuiGraphicsExtractor g")
        reverse("\\bvoid extractContent\\((GuiGraphics\\w*) g\\b", "void renderContent(GuiGraphics g")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\bvoid renderWidget\\((GuiGraphics\\w*) g\\b", "void extractWidgetRenderState(GuiGraphicsExtractor g")
        reverse("\\bvoid extractWidgetRenderState\\((GuiGraphics\\w*) g\\b", "void renderWidget(GuiGraphics g")
    }
    // 26.1 moved the level/camera render states into renderer.state.level.
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\brenderer\\.state\\.CameraRenderState\\b", "renderer.state.level.CameraRenderState")
        reverse("\\brenderer\\.state\\.level\\.CameraRenderState\\b", "renderer.state.CameraRenderState")
    }
    replacements.regex(eval(v, ">=26.1")) {
        replace("\\brenderer\\.state\\.LevelRenderState\\b", "renderer.state.level.LevelRenderState")
        reverse("\\brenderer\\.state\\.level\\.LevelRenderState\\b", "renderer.state.LevelRenderState")
    }
}
