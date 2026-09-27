/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Compiles the vendored Spotify plugin (plugins/spotube-plugin-spotify) straight into
// the Android app so it runs as a built-in plugin: no .smplug install, no JS runtime.
// Only the plugin's shared (commonMain) sources are used; the JS-only entry point is
// replaced by src/androidMain.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

val pluginDir = rootProject.layout.projectDirectory.dir("plugins/spotube-plugin-spotify")

kotlin {
    androidLibrary {
        namespace = "dev.krtirtho.spotube.spotify_builtin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(pluginDir.dir("spotify_gql_client/src/commonMain/kotlin"))
            kotlin.srcDir(pluginDir.dir("spotube_plugin_spotify/src/commonMain/kotlin"))
            dependencies {
                api(project(":plugin_interfaces"))
                api(libs.semver)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation("com.osmerion.kotlin:kotlin-base32:1.0.1")
            }
        }
    }
}
