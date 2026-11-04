package io.github.blanaboo.stimmzug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import io.github.blanaboo.stimmzug.ui.home.HomeScreen
import io.github.blanaboo.stimmzug.ui.theme.StimmzugTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StimmzugTheme {
                HomeScreen(versionName = BuildConfig.VERSION_NAME, modifier = Modifier.safeDrawingPadding())
            }
        }
    }
}
