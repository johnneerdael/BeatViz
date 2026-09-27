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

package dev.krtirtho.spotube.tv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.krtirtho.spotube.core.deeplink.ExternalUriHandler
import dev.krtirtho.spotube.core.newpipe.NewPipeDownloader
import dev.krtirtho.spotube.core.paths.Paths
import dev.krtirtho.spotube.media.PlaybackService
import dev.krtirtho.spotube.tv.phone.PhoneKeyboardService
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

/**
 * Android TV entry point. Same start-up as [dev.krtirtho.spotube.MainActivity],
 * but renders the remote-driven TV interface instead of the phone/desktop one.
 */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        NewPipeDownloader.init(Paths())

        val serviceIntent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Phone keyboard server comes back up if it was left on.
        PhoneKeyboardService.get(this).start()

        intent?.dataString?.let(ExternalUriHandler::onNewUri)
        setContent {
            TvApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let(ExternalUriHandler::onNewUri)
    }
}
