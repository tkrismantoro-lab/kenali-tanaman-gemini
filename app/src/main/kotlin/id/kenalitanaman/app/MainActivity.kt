package id.kenalitanaman.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.kenalitanaman.app.ui.AnalyzingScreen
import id.kenalitanaman.app.ui.HomeScreen
import id.kenalitanaman.app.ui.KenaliTanamanTheme
import id.kenalitanaman.app.ui.ResultScreen
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: PlantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KenaliTanamanTheme {
                App(viewModel)
            }
        }
    }
}

@Composable
private fun App(viewModel: PlantViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Foto kamera ditulis ke cache aplikasi dan dibagikan ke aplikasi kamera lewat FileProvider.
    val cameraUri: Uri = remember {
        val file = File(context.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) viewModel.identify(cameraUri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.identify(uri)
    }

    when (val screen = state.screen) {
        Screen.Home -> HomeScreen(
            state = state,
            onTakePhoto = { takePhoto.launch(cameraUri) },
            onPickPhoto = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpen = viewModel::open,
            onSaveApiKey = viewModel::setApiKey,
            onDismissError = viewModel::dismissError,
        )

        Screen.Analyzing -> {
            BackHandler { /* tunggu sampai analisis selesai */ }
            AnalyzingScreen()
        }

        is Screen.Result -> {
            BackHandler(onBack = viewModel::backToHome)
            ResultScreen(
                entry = screen.entry,
                onBack = viewModel::backToHome,
                onDelete = { viewModel.delete(screen.entry) },
            )
        }
    }
}
