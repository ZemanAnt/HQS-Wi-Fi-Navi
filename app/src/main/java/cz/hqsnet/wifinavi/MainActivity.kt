package cz.hqsnet.wifinavi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // behem mereni nechat displej zapnuty
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

private fun hasLocationPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun locationEnabled(ctx: Context): Boolean {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(lm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(vm: WifiVM = viewModel(), tvm: ToolsVM = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var locOn by remember { mutableStateOf(locationEnabled(ctx)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        vm.refresh()
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> {
                    granted = hasLocationPermission(ctx)
                    locOn = locationEnabled(ctx)
                    vm.setActive(true)
                }
                Lifecycle.Event.ON_STOP -> vm.setActive(false)
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            vm.setActive(false)
        }
    }

    LaunchedEffect(granted) {
        if (!granted) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val tabs = listOf(
        "📶" to "Skener",
        "📊" to "Kanály",
        "🚶" to "Chůze",
        "🔀" to "Roaming",
        "⚡" to "Testy",
        "🧰" to "Nástroje",
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!granted || !locOn) {
                Card(Modifier.padding(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Android vyžaduje pro čtení Wi-Fi (BSSID, síla signálu okolních sítí) " +
                                "oprávnění k poloze a zapnuté Polohové služby. Poloha se nikam neodesílá.",
                        )
                        if (!granted) {
                            Button(onClick = {
                                permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }) { Text("Povolit oprávnění") }
                        }
                        if (!locOn) {
                            Button(onClick = {
                                ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }) { Text("Zapnout polohové služby") }
                        }
                    }
                }
            }
            when (tab) {
                0 -> ScannerScreen(vm)
                1 -> ChannelsScreen(vm)
                2 -> WalkScreen(vm)
                3 -> RoamScreen(vm)
                4 -> TestsScreen(vm, tvm)
                else -> ToolsScreen(vm, tvm)
            }
        }
    }
}
