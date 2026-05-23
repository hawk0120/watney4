package core

import androidx.compose.runtime.*
import javax.script.ScriptEngineManager
import com.jakewharton.mosaic.runMosaic
import kotlinx.coroutines.runBlocking

fun main(): kotlin.Unit = runBlocking {
    runMosaic {
        val engine = remember {
            ScriptEngineManager().getEngineByExtension("kts")
        }

        var input by remember { mutableStateOf("") }
        var output by remember { mutableStateOf(listOf<String>()) }
        var error by remember { mutableStateOf<String?>(null) }

        fun evaluate(code: String) {
            try {
                val result = engine.eval(code)
                output = output + "> $code" + (result?.toString() ?: "kotlin.Unit")
                error = null
            } catch (e: Exception) {
                output = output + "> $code"
                error = e.message
            }
        }

        // TEMP: simulate input since Mosaic input handling isn't wired yet
        LaunchedEffect(Unit) {
            while (true) {
                val line = readLine() ?: break
                evaluate(line)
            }
        }

        ReplScreen(
            output = output,
            input = input,
            error = error
        )
    }
}
