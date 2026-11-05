# Better X-Ray

A configurable, client-side X-Ray mod for **Fabric**, **NeoForge** and **Forge**.
Unselected terrain stays visible as faint, textured blocks while the blocks you are looking for
keep their real models, textures and lighting. On top of that: Cave-Find, block highlighting with
tracers, a Structure Finder, Mob/Player/Item ESP and Fullbright.

Better X-Ray is client-only. It never sends anything to the server and works in singleplayer and
on multiplayer servers (the Structure Finder is singleplayer-only, see below).

## Features

- **X-Ray** – unselected terrain is rendered at an adjustable opacity (0–80 %, default 15 %);
  selected blocks stay fully visible and lit, even when completely enclosed. Water and lava can be
  hidden separately.
- **Three presets** – independently named block lists with search by name or registry ID.
- **Block tags** – presets and the highlight list accept tags such as `#c:ores`, which also cover
  blocks of other mods.
- **Cave-Find** – caves, ravines and hidden rooms show up as solid, lit hollow shapes through the
  terrain.
- **Hide Mobs** – hide mobs while X-Ray is on: off, only behind walls, or all.
- **Block highlighting** – boxes and optional tracers around selected blocks, adjacent ores merged
  into veins, a global color or one color per block, and an adjustable range.
- **Structure Finder** – boxes (and optional tracers) around villages, fortresses, bastions,
  modded structures and more in the loaded chunks.
- **Mob / Player / Item ESP** – outlines or boxes through walls, grouped by mob type, with per-group
  and per-mob style, color and tracers; player name and health labels; ESP for single items.
- **Fullbright** – works independently of X-Ray.
- **Chat commands** – change every setting without opening the GUI.
- Native Minecraft GUI that adapts to every GUI scale and window size; English and German.

## Supported versions

| Minecraft | Loader | Java | Optional renderer |
|-----------|--------|------|-------------------|
| 1.20.1  | Fabric (Loader 0.16.10+, API 0.92.12+) | 17+ | Sodium 0.5.13+ (0.5.x) |
| 1.20.1  | Forge 47.4.10+ | 17+ | Embeddium 0.3.31 |
| 1.21.1  | Fabric (Loader 0.16.10+, API 0.116.17+) | 21+ | Sodium 0.8.13+ (0.8.x) |
| 1.21.1  | NeoForge 21.1.0+ | 21+ | Sodium 0.8.13+ (0.8.x) |
| 1.21.1  | Forge 52.1.0+ | 21+ | – |
| 1.21.11 | Fabric (Loader 0.17.3+, API 0.141.6+) | 21+ | Sodium 0.8.14+ (0.8.x) |
| 1.21.11 | NeoForge 21.11.45+ | 21+ | Sodium 0.8.14+ (0.8.x) |
| 26.2    | Fabric (Loader 0.19.5+, API 0.161.0+) | 25+ | Sodium 0.9.2+ (0.9.x) |
| 26.2    | NeoForge 26.2.0.89+ | 25+ | Sodium 0.9.2+ (0.9.x) |
| 26.3    | Fabric (Loader 0.19.5+, API 0.160.7+) | 25+ | Sodium 0.9.2+ (0.9.x), Iris 1.11.x |
| 26.3    | NeoForge 26.3.0.64-beta+ | 25+ | Sodium 0.9.2+ (0.9.x) |

All builds share the same feature set. [Mod Menu](https://modrinth.com/mod/modmenu) is optionally
supported on Fabric; on NeoForge and Forge the configuration is also available from the Mods screen.

## Installation

1. Install the mod loader for your Minecraft version (on Fabric also Fabric API).
2. Download the jar matching your Minecraft version and loader from
   [Releases](https://github.com/realrbn/BetterXray/releases) (or CurseForge):
   - Fabric: `betterxray-<minecraft>-<version>.jar`
   - NeoForge: `betterxray-<minecraft>-neoforge-<version>.jar`
   - Forge: `betterxray-<minecraft>-forge-<version>.jar`
3. Put it into the `mods` folder of your instance.

The Forge jars include MixinExtras; nothing else is required. A dedicated server with the NeoForge
jar installed still starts, but the mod does nothing there.

## Controls

| Key     |Action                              |
| ------- |----------------------------------- |
| X       |Toggle X-ray                        |
| L       |Toggle Fullbright                   |
| G       |Open config menu                    |
| J       |Toggle Mob/Player ESP               |
| K       |Toggle ESP-Tracers                  |
| H       |Toggle Highlight                    |
| U       |Toggle Cave-Find                    |
| Unbound |Toggle Highlight-Tracers            |
| Unbound |Add Looked-at-Block to X-ray Preset |
| Unbound |Cycle X-ray Preset                  |

All keys can be rebound under **Controls → Better X-Ray**.

## Usage

### X-Ray and presets

The configuration screen provides the opacity slider, the water/lava switches and a block picker
with three presets. Search by localized name or registry ID and click a row to toggle the block in
the active preset. **Reset Preset** only affects the active preset.

**Block tags:** a tag covers every block it lists, so one entry like `#c:ores` selects all ores of a
modpack. Selected tags are always listed at the top; a search starting with `#` lists tags only.
Blocks covered by a selected tag show "via #tag". Tags come from the data packs of the current
world and update automatically after joining a world or a `/reload`.

### Cave-Find

Every block face that borders underground air (air without sky light) stays solid and fully lit,
so caves show up as hollow shapes; all other terrain follows the X-Ray opacity (set it to 0 % to
see only the caves). Cave-Find on its own shows only caves; turn X-Ray on as well to see your preset
blocks too. Water- or lava-filled caves are not marked; in the Nether and the End every air pocket
counts as a cave.

### Hide Mobs

The **Hide Mobs** button cycles **Off → Behind Walls → All** and stops drawing mobs while X-Ray or
Cave-Find is on. *Behind Walls* only hides mobs that are covered by blocks. Players, objects and
mobs with ESP enabled always stay visible.

### Block highlighting

The **Highlight Blocks** screen draws boxes (and optionally tracers) around selected blocks within
the highlight range (8 blocks up to the render distance).

- **Group Veins** (default on) merges adjacent blocks of the same kind into one vein with a single
  box and tracer. Deepslate ores count as their stone variant.
- The RGB sliders set the **global color**; the color square at the end of each row gives a block
  its own color. Tags can have their own color too; a block's own color wins.

### Structure Finder (singleplayer)

Draws a box around every selected structure in the loaded chunks, optionally with a tracer to its
center. Every registered structure is listed, including modded ones; one row covers all variants of
a structure set (e.g. all villages). Structure positions only exist on the server, so this works in
singleplayer and LAN worlds you host, but not on multiplayer servers. Dungeons, geodes and fossils
are features, not structures – find them with block highlighting instead.

### Mob / Player / Item ESP

Open **Mob ESP** from the X-Ray menu. Mobs are sorted into groups (Players, Animals, Aquatic
Creatures, Neutral Mobs, Villagers & Golems, Overworld Enemies, Illagers & Raiders, Nether Enemies,
End Mobs, Other Mobs and Objects); modded mobs are sorted by their spawn category.

- Every group and every single mob can be switched on and given its own **Outline/Box** style,
  **color** and **tracer**. Mobs follow their group until you override them.
- **Players** can additionally show their **name** and **health** (numbers, bar or both) through walls.
- **Objects** covers all non-mob entities (items, minecarts, armor stands, projectiles, …). The
  **Item ESP** list lets you mark single items, e.g. only diamonds and emeralds.
- The **ESP range** slider limits ESP to 8–512 blocks or leaves it unlimited (default).

ESP is off by default and works independently of X-Ray and block highlighting. Entities are shown
as far as the client knows about them (the server's entity tracking range).

### Commands

`/betterxray` (alias `/bxray`) runs entirely on the client; nothing is sent to the server.

| Command | Effect |
|---------|--------|
| `/betterxray` | Show the command overview |
| `/betterxray help` | Show the command overview |
| `/betterxray status` | Show all current settings |
| `/betterxray on` | Turn X-Ray on |
| `/betterxray off` | Turn X-Ray off |
| `/betterxray toggle` | Toggle X-Ray |
| `/betterxray add <block\|#tag> …` | Add blocks or tags to the active preset (several entries separated by spaces, tab completion) |
| `/betterxray remove <block\|#tag> …` | Remove blocks or tags from the active preset (several entries separated by spaces, tab completion) |
| `/betterxray list` | List all entries of the active preset |
| `/betterxray clear` | Remove all entries from the active preset |
| `/betterxray preset` | Show all three presets and which one is active |
| `/betterxray preset <1-3>` | Switch to preset 1, 2 or 3 |
| `/betterxray preset rename <name>` | Rename the active preset (max. 32 characters, spaces allowed) |
| `/betterxray preset reset` | Reset the active preset to its default name and blocks (Preset 1: default ores etc., Presets 2–3: empty) |
| `/betterxray opacity` | Show the current terrain opacity |
| `/betterxray opacity <0-80>` | Set the terrain opacity in percent |
| `/betterxray water` | Toggle water visibility |
| `/betterxray water on` | Show water |
| `/betterxray water off` | Hide water |
| `/betterxray lava` | Toggle lava visibility |
| `/betterxray lava on` | Show lava |
| `/betterxray lava off` | Hide lava |
| `/betterxray fullbright` | Toggle Fullbright |
| `/betterxray fullbright on` | Turn Fullbright on |
| `/betterxray fullbright off` | Turn Fullbright off |
| `/betterxray cavefind` | Toggle Cave-Find |
| `/betterxray cavefind on` | Turn Cave-Find on |
| `/betterxray cavefind off` | Turn Cave-Find off |
| `/betterxray hidemobs` | Cycle Hide Mobs: Off → Behind Walls → All |
| `/betterxray hidemobs off` | Show all mobs |
| `/betterxray hidemobs walls` | Hide mobs behind walls |
| `/betterxray hidemobs all` | Hide all mobs |
| `/betterxray highlight` | Toggle block highlighting |
| `/betterxray highlight on` | Turn block highlighting on |
| `/betterxray highlight off` | Turn block highlighting off |
| `/betterxray highlight add <block\|#tag> …` | Add blocks or tags to the highlight list (several entries separated by spaces, tab completion) |
| `/betterxray highlight remove <block\|#tag> …` | Remove blocks or tags from the highlight list (several entries separated by spaces, tab completion) |
| `/betterxray highlight list` | List all entries of the highlight list |
| `/betterxray highlight clear` | Remove all entries from the highlight list |
| `/betterxray highlight tracers` | Toggle highlight tracers |
| `/betterxray highlight tracers on` | Turn highlight tracers on |
| `/betterxray highlight tracers off` | Turn highlight tracers off |
| `/betterxray highlight range` | Show the current highlight range |
| `/betterxray highlight range <8-512>` | Set the highlight range in blocks (rounded to 8-block steps) |
| `/betterxray highlight range render` | Highlight range follows the render distance |
| `/betterxray esp` | Toggle Mob/Player ESP (master switch) |
| `/betterxray esp on` | Turn ESP on |
| `/betterxray esp off` | Turn ESP off |
| `/betterxray esp tracers` | Toggle ESP tracers |
| `/betterxray esp tracers on` | Turn ESP tracers on |
| `/betterxray esp tracers off` | Turn ESP tracers off |
| `/betterxray esp range` | Show the current ESP range |
| `/betterxray esp range <8-512>` | Set the ESP range in blocks (rounded to 8-block steps) |
| `/betterxray esp range unlimited` | Remove the ESP range limit |
| `/betterxray tags` | List all block tags of the current world (with block count) |
| `/betterxray tags <search>` | List all block tags containing the search text |
| `/betterxray tag <#tag>` | List all blocks of a tag (tab completion) |

Block IDs without a namespace default to `minecraft:` (`diamond_ore`, `#ores`). `tags` and `tag`
only work inside a world. Lists longer than 60 entries are cut off in the chat.


## Configuration

Settings are saved immediately to `config/betterxray.json`. The file can be edited by hand; unknown
block IDs are kept, so temporarily missing mods do not erase your selections. Configurations of
older versions are migrated automatically.

## Update check

Once per game session, when the first singleplayer world is opened, the mod reads a small JSON file
from `https://realrbn.github.io/update-check/update-check.json`. If a newer version is listed, a
hint with a download link appears in your own chat. Nothing is sent anywhere, and nothing is shown
on multiplayer servers. Turn it off with the **Update Check** button in the X-Ray menu or the JVM
argument `-Dbetterxray.updateCheck.url=off`.

## Compatibility and limitations

- **Sodium** is supported in the versions listed above; other Sodium releases are declared
  incompatible. On Forge 1.20.1, **Embeddium** takes its place.
- **Shader packs (Iris):** on Fabric 26.3 with Iris 1.11.x, X-Ray, Cave-Find, highlights and ESP
  keep working under an active shader pack (tested with Complementary, BSL, MakeUp Ultra Fast and
  Sildur's Vibrant). Packs using `separateAo` (Complementary, BSL) ignore the terrain opacity, so
  faint terrain is hidden there; the experimental **Shader Opacity** switch patches them to show it
  anyway. On all other targets the effect is turned off while a shader pack is active.
- **Entity Culling** is supported: it culls nothing while X-Ray or Cave-Find is on and leaves ESP
  targets alone.
- **Nvidium** is not supported (it ignores the faint-block opacity); a hint is shown in chat when
  it is installed.
- Unknown Fabric renderer implementations disable the effect and log a warning.
- While X-Ray or Cave-Find is on, translucent geometry is not depth-sorted, so overlapping
  translucent surfaces may blend in a slightly different order.
- Text, particles, beams and custom geometry of other mods are not made transparent.

## Building from source

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build all targets from one
shared `src` tree. Per-version settings live in `versions/<target>/gradle.properties`.

```sh
./gradlew buildAll              # all targets
./gradlew :26.3:build           # a single target
./gradlew :26.3:runClient       # start a development client
./gradlew :26.3:runClient -Psodium -Pmodmenu
./gradlew :26.3:runClientGameTest
```

Target names are `<minecraft>`, `<minecraft>-neoforge` and `<minecraft>-forge`. The jars end up in
`versions/<target>/build/libs`. On Windows use `gradlew.bat`. The first build needs internet access
to download dependencies.

### Debug mode

A developer debug mode checks internal invariants of Highlight and the Structure Finder, for
example that no section outside the highlight range (plus its scan margin) is scanned, kept or
drawn. Each broken invariant is reported once to the log and the chat. It is off by default and
switched on with `-Dbetterxray.debug=true` or in game:

| Command | Effect |
|---------|--------|
| `/bxray debug [on\|off]` | show the state and broken invariants, or switch the checks |
| `/bxray debug hud [on\|off]` | F3-style overlay with live counters: X-Ray render path, rebuilds, Highlight scan area and timings, ESP targets, Hide Mobs, Structure Finder scans |
| `/bxray debug bounds [on\|off]` | draw the scanned chunk columns (green: in range, yellow: scan margin, red: outside), queued sections (white), the highlight range as a wireframe sphere with the scan margin as a second hull, the ESP range (orange) and the Structure Finder's scan area (magenta) |
| `/bxray debug sections [on\|off]` | like `bounds`, but every scanned section as its own box |
| `/bxray debug stats` | scan and draw counters of Highlight and the Structure Finder in the chat |
| `/bxray debug clear` | forget reported violations |

The HUD is hidden while the F3 screen is open.

Gametest and validation runs switch it on and fail when an invariant broke.

## License

[MIT](LICENSE)
