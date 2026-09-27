# BeatViz — Spotube for Android TV

BeatViz is [Spotube](https://github.com/KRTirtho/spotube) (Kotlin/Compose rewrite, `dev` branch) with a remote-driven Android TV interface and a built-in Spotify metadata plugin. It ships **only** as an Android TV app.

## What is ours vs. upstream

| Path | Owner | Notes |
|---|---|---|
| `composeApp/src/androidMain/kotlin/dev/krtirtho/spotube/tv/` | BeatViz | The whole TV interface (web-player layout, D-pad focus, full-screen player) |
| `spotify_builtin/` | BeatViz | Gradle module compiling the Spotify plugin into the app |
| `plugins/spotube-plugin-spotify/` | Fork (git subtree) | [sonic-liberation/spotube-plugin-spotify](https://github.com/sonic-liberation/spotube-plugin-spotify) plus our changes |
| `.github/workflows/{android-build,upstream-sync,plugin-sync}.yml` | BeatViz | CI and the two daily syncs |
| Everything else | Upstream Spotube | Keep edits minimal so merges stay clean |

Upstream files we touch (small, marked `BeatViz:` in comments):
- `composeApp/src/androidMain/AndroidManifest.xml` — TV-only (leanback required), `TvActivity` is the only launcher entry.
- `settings.gradle.kts`, `composeApp/build.gradle.kts` — include `:spotify_builtin`.
- `modules/plugin/BuiltInPlugins.kt`, `PluginManager.kt`, `core/zipline/BuiltInPluginService.kt` — Spotify as a default built-in plugin via the `PlatformBuiltInPlugins` expect/actual hook.

## Spotify plugin fork

The plugin runs **in-process** as a built-in plugin (no `.smplug` install, no JS runtime): `spotify_builtin` compiles the plugin's `commonMain` sources for Android, and `SpotifyCoreAPI` ports the JS-only session/sign-in code. Users only sign in.

Our changes to the plugin (keep them small; they must survive `git subtree pull`):
- `HomeShortsSectionData` (the untitled shortcuts grid at the top of Home) is returned as a section with `description = SHORTCUTS_MARKER`.
- `genres()` returns `All` + the web player's Home chips (`home-genre:<chip id>`, passed to the `home` query as `facet`) + browse categories (`spotify:page:…`). The TV shows chips on Home and categories on Search.

## Syncs (daily, from `master`)

- **upstream-sync.yml** — merges `KRTirtho/spotube` `dev` into `master`.
- **plugin-sync.yml** — `git subtree pull` of the plugin into `plugins/spotube-plugin-spotify`.

Both only fast-forward `master` when the merge is clean **and** `android-build.yml` passes. Otherwise they open an issue (labels `upstream-sync` / `plugin-sync`); a clean-but-broken merge is left on the `upstream-sync` / `plugin-sync` branch for fixing.

Manual equivalents:

```bash
git fetch https://github.com/KRTirtho/spotube dev && git merge FETCH_HEAD
git subtree pull --prefix=plugins/spotube-plugin-spotify \
  https://github.com/sonic-liberation/spotube-plugin-spotify main
```

## Building

CI (`android-build.yml`) publishes upstream's helper projects to Maven local on macOS, then runs `./gradlew :composeApp:assembleDebug` on Linux with JetBrains JDK 21. The debug APK is uploaded as the `beatviz-debug-apk` artifact.

Locally: JetBrains JDK 21, Android SDK, Rust, and the same three helper projects in Maven local (see the workflow), then `./gradlew :composeApp:assembleDebug`.

## TV interface conventions

- Every interactive element uses `Modifier.tvFocusable(...)`: white focus outline, OK activates, hold OK / Menu opens the action sheet.
- Give elements a `focusKey` so `TvFocusMemory` can restore focus on Back; mark one element per screen `initialFocus = true`.
- Data comes from upstream ViewModels (Home, Library, Playlist, Album, Artist, Search); new data loading goes into a ViewModel, not the composable.
- The full-screen player's black background is reserved for the visualizer (MilkDrop).
