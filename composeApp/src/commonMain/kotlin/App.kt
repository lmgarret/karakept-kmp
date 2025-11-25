
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.karakept.app.di.appModule
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

@Composable
@Preview
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        com.karakept.app.ui.theme.AppTheme {
            val serverRepository = org.koin.compose.koinInject<com.karakept.app.data.repository.ServerRepository>()
            var initialScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<cafe.adriel.voyager.core.screen.Screen?>(null) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (serverRepository.hasServers()) {
                    initialScreen = com.karakept.app.ui.screens.MainScreen()
                } else {
                    initialScreen = com.karakept.app.ui.screens.LoginScreen()
                }
            }

            if (initialScreen != null) {
                Navigator(initialScreen!!) { navigator ->
                    SlideTransition(navigator)
                }
            }
        }
    }
}
