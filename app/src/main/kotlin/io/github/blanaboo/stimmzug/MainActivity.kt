package io.github.blanaboo.stimmzug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.blanaboo.stimmzug.ui.home.HomeScreen
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StimmzugTheme {
                HomeScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    // Exposes Compose test tags as resource-ids to uiautomator (QA helpers in tools/qa).
                    modifier = Modifier.safeDrawingPadding().semantics { testTagsAsResourceId = true }
                )
            }
        }
    }
}
