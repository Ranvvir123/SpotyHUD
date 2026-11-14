# SpotyHUD

Client-side Fabric mod for Minecraft **1.21.11** (Java 21, Yarn mappings). A now-playing pill card with
cover art, colour-sampled background, progress bar and clickable transport buttons, plus key bindings to
control Spotify without alt-tabbing. **No Spotify Web API / OAuth / client IDs** - it reads and controls the
operating system's media session, so it works with Spotify Free and Premium.

| OS      | Backend                                                          | Requirement |
|---------|------------------------------------------------------------------|-------------|
| Windows | Windows SMTC via one background `powershell.exe` (PowerShell 5.1) | Spotify desktop app |
| Linux   | MPRIS via `playerctl` (prefers Spotify, falls back to any player) | `sudo apt install playerctl` |
| macOS   | AppleScript against the Spotify app (never launches it)           | Accept the Automation prompt |
| other   | no-op backend (HUD stays hidden, nothing crashes)                 | - |

## Build
Requires **JDK 21** and internet access (Gradle downloads Minecraft, Loom and Fabric API).

    ./gradlew build        # Windows: gradlew.bat build

Output: `build/libs/spotyhud-1.0.0.jar` (ignore `-sources`).
Versions live in `gradle.properties`; if one fails to resolve, take the current ones from https://fabricmc.net/develop/.

## Install
1. Install Minecraft 1.21.11 + Fabric Loader >= 0.18.1.
2. Put **Fabric API** (1.21.11) and `spotyhud-1.0.0.jar` in `.minecraft/mods/`.
3. Start Spotify, play something, launch the game.

## Controls (Options > Controls > SpotyHUD)
| Action | Default |
|--------|---------|
| Play / Pause | `]` (`P` is vanilla's Social Interactions key) |
| Next / Previous | `.` / `,` |
| HUD editor | `O` |

With the cursor free (chat, pause menu, HUD editor) the card shows prev / play-pause / next buttons - click them.
In the editor: drag the HUD, set scale 0.5x-2.0x and opacity, toggle frosted glass. Saved to `config/spotyhud.json`.
