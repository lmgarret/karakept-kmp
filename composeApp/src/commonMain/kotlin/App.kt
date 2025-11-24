
import androidx.compose.runtime.Composable
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
            Navigator(com.karakept.app.ui.screens.LoginScreen()) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}
