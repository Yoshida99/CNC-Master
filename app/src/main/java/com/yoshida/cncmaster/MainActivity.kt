package com.yoshida.cncmaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yoshida.cncmaster.ui.theme.CNCMasterTheme
import com.yoshida.cncmaster.v02.AdvisorScreen
import com.yoshida.cncmaster.v02.CuttingSpeedScreenV02
import com.yoshida.cncmaster.v02.DiagnosticsScreen
import com.yoshida.cncmaster.v02.FanucScreen
import com.yoshida.cncmaster.v02.FeedScreenV02
import com.yoshida.cncmaster.v02.MaterialsScreen
import com.yoshida.cncmaster.v02.PartsScreen
import com.yoshida.cncmaster.v02.RpmScreenV02
import com.yoshida.cncmaster.v02.ThreadsScreen
import com.yoshida.cncmaster.v02.ToolingScreen
import com.yoshida.cncmaster.v02.V02HomeScreen
import com.yoshida.cncmaster.v02.V02Screen

class MainActivity : ComponentActivity() {
    private var openUpdatesRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpdatesRequested = intent?.getBooleanExtra("open_updates", false) == true
        AppUpdateManager.schedule(this)

        setContent {
            CNCMasterTheme {
                CncMasterV02(openUpdatesRequested = openUpdatesRequested)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_updates", false)) {
            openUpdatesRequested = true
        }
    }
}

@Composable
private fun CncMasterV02(openUpdatesRequested: Boolean) {
    var screen by remember { mutableStateOf(V02Screen.HOME) }

    BackHandler(enabled = screen != V02Screen.HOME) {
        screen = V02Screen.HOME
    }

    val backToHome: () -> Unit = { screen = V02Screen.HOME }

    when (screen) {
        V02Screen.HOME -> V02HomeScreen(
            openUpdatesRequested = openUpdatesRequested,
            onNavigate = { screen = it },
        )
        V02Screen.ADVISOR -> AdvisorScreen(backToHome)
        V02Screen.RPM -> RpmScreenV02(backToHome)
        V02Screen.FEED -> FeedScreenV02(backToHome)
        V02Screen.VC -> CuttingSpeedScreenV02(backToHome)
        V02Screen.THREADS -> ThreadsScreen(backToHome)
        V02Screen.MATERIALS -> MaterialsScreen(backToHome)
        V02Screen.FANUC -> FanucScreen(backToHome)
        V02Screen.DIAGNOSTICS -> DiagnosticsScreen(backToHome)
        V02Screen.TOOLING -> ToolingScreen(backToHome)
        V02Screen.PARTS -> PartsScreen(backToHome)
    }
}
