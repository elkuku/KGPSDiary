package org.elkuku.kgpsdiary

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import javax.inject.Inject
import org.elkuku.kgpsdiary.settings.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.elkuku.kgpsdiary.ui.AppNavHost
import org.elkuku.kgpsdiary.ui.MainViewModel
import org.elkuku.kgpsdiary.ui.SignInScreen
import org.elkuku.kgpsdiary.ui.theme.KGPSDiaryTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings by settings.state.collectAsStateWithLifecycle()
            val dark = appSettings.theme.isDark(isSystemInDarkTheme())
            // The status and navigation bar icons follow the app's theme, not only the phone's
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            KGPSDiaryTheme(darkTheme = dark) {
                KGPSDiaryApp()
            }
        }
    }

    private companion object {
        // As enableEdgeToEdge() has them by default
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

@Composable
fun KGPSDiaryApp(viewModel: MainViewModel = hiltViewModel()) {
    // The Activity: Credential Manager shows its UI on top of it
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.message?.asString()

    LaunchedEffect(Unit) { viewModel.start(context) }
    LaunchedEffect(message, state.signedIn) {
        // Signed in, the profile screen shows the messages
        if (message != null && !state.signedIn) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    when {
        !state.started -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        // The screens have their own Scaffolds, snackbars and insets
        state.signedIn -> AppNavHost(
            mainState = state,
            onRefreshProfile = { viewModel.refresh(context) },
            onSignOut = { viewModel.signOut(context) },
        )
        else -> Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            SignInScreen(busy = state.busy, onSignIn = { viewModel.signIn(context) }, modifier = Modifier.padding(padding))
        }
    }
}
