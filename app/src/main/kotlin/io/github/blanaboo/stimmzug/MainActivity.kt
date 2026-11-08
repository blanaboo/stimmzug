package io.github.blanaboo.stimmzug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.blanaboo.stimmzug.ui.StimmzugNavHost
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars; the status bar icons follow the system's light/dark mode, as the theme does.
        enableEdgeToEdge()
        setContent {
            StimmzugTheme {
                // The surface fills the area behind the system bars with the theme background (#42); only the content
                // keeps clear of them.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StimmzugNavHost(
                        versionName = BuildConfig.VERSION_NAME,
                        // Exposes Compose test tags as resource-ids to uiautomator (QA helpers in tools/qa).
                        modifier = Modifier.safeDrawingPadding().semantics { testTagsAsResourceId = true }
                    )
                }
            }
        }
    }
}
