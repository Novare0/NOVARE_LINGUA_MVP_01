package com.novare.lingua

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions

private data class LanguageOption(val label: String, val tag: String)

private val sourceLanguages = listOf(
    LanguageOption("Español", "es-ES"),
    LanguageOption("English", "en-US"),
    LanguageOption("中文 · Mandarín", "zh-CN")
)

private val targetLanguages = listOf(
    LanguageOption("Español", "es"),
    LanguageOption("English", "en"),
    LanguageOption("日本語 · Japonés", "ja"),
    LanguageOption("Français · Francés", "fr"),
    LanguageOption("Deutsch · Alemán", "de"),
    LanguageOption("Português · Portugués", "pt"),
    LanguageOption("한국어 · Coreano", "ko"),
    LanguageOption("中文 · Chino", "zh"),
    LanguageOption("Italiano", "it"),
    LanguageOption("العربية · Árabe", "ar")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovareLinguaApp() }
    }
}

@androidx.compose.runtime.Composable
private fun NovareLinguaApp() {
    val context = LocalContext.current

    var listening by remember { mutableStateOf(false) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

        var audioLabOpen by remember { mutableStateOf(false) }

    if (audioLabOpen) {
        MaterialTheme {
            AudioLabScreen(
                onBack = { audioLabOpen = false }
            )
        }
        return
    }

    var liveText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }

    var detectedLanguage by remember { mutableStateOf("Esperando voz") }
    var status by remember { mutableStateOf("Listo para comenzar") }

    var source by remember { mutableStateOf(sourceLanguages.first()) }
    var target by remember { mutableStateOf(targetLanguages.first()) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var translator by remember { mutableStateOf<Translator?>(null) }

    fun closeTranslator() {
        translator?.close()
        translator = null
    }

    fun translateRecognized(text: String) {
        if (text.isBlank()) return

        val identifier = LanguageIdentification.getClient()
        identifier.identifyLanguage(text)
            .addOnSuccessListener { sourceTag ->
                identifier.close()

                if (sourceTag == "und") {
                    detectedLanguage = "Idioma no identificado"
                    status = "No se pudo identificar el idioma del texto."
                    return@addOnSuccessListener
                }

                detectedLanguage = sourceTag

                val detectedSource = TranslateLanguage.fromLanguageTag(sourceTag)
                val destination = TranslateLanguage.fromLanguageTag(target.tag)

                if (detectedSource == null || destination == null) {
                    status = "Este par de idiomas no está disponible."
                    return@addOnSuccessListener
                }

                if (detectedSource == destination) {
                    translatedText = text
                    status = "El texto ya está en el idioma de destino."
                    return@addOnSuccessListener
                }

                closeTranslator()

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(detectedSource)
                    .setTargetLanguage(destination)
                    .build()

                val newTranslator = Translation.getClient(options)
                translator = newTranslator
                status = "Preparando modelo de traducción…"

                newTranslator.downloadModelIfNeeded(
                    DownloadConditions.Builder().build()
                ).addOnSuccessListener {
                    status = "Traduciendo…"
                    newTranslator.translate(text)
                        .addOnSuccessListener { result ->
                            translatedText = result
                            status = if (listening) "Escuchando" else "Traducción lista"
                        }
                        .addOnFailureListener { error ->
                            status = "Error de traducción: ${error.localizedMessage ?: "desconocido"}"
                        }
                }.addOnFailureListener { error ->
                    status = "No se pudo descargar el modelo: ${error.localizedMessage ?: "revisa internet"}"
                }
            }
            .addOnFailureListener {
                identifier.close()
                status = "No se pudo detectar el idioma."
            }
    }

    fun stopListening() {
        listening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        status = "Sesión detenida"
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "El dispositivo no tiene un servicio de reconocimiento disponible."
            return
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status = "Escuchando en ${source.label}"
            }

            override fun onBeginningOfSpeech() {
                status = "Voz detectada"
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status = "Procesando voz…"
            }

            override fun onError(error: Int) {
                if (listening) {
                    status = "No se reconoció voz (código $error). Revisa el idioma o inténtalo de nuevo."
                }
                listening = false
                recognizer?.destroy()
                recognizer = null
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (text.isNotBlank()) {
                    liveText = text
                    translateRecognized(text)
                }

                listening = false
                status = if (text.isBlank()) {
                    "No se obtuvo texto. Pulsa iniciar para intentarlo otra vez."
                } else {
                    "Frase procesada."
                }

                recognizer?.destroy()
                recognizer = null
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (partial.isNotBlank()) liveText = partial
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, source.tag)
        }

        listening = true
        status = "Solicitando micrófono…"
        speechRecognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            startListening()
        } else {
            status = "Permiso de micrófono denegado."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            translator?.close()
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090B0E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                Text(
                    "NOVARE",
                    color = Color(0xFFB8F36B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "LINGUA",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Tu conversación, sin fronteras.",
                    color = Color(0xFF9BA3AD),
                    fontSize = 14.sp
                )
		OutlinedButton(
    onClick = { audioLabOpen = true },
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        "AUDIO LAB  →",
        color = Color(0xFFB8F36B),
        fontWeight = FontWeight.Bold
    )
}
                Surface(
                    color = Color(0xFF151A20),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "IDIOMA DE ENTRADA",
                            color = Color(0xFF9BA3AD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = { sourceMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(source.label, color = Color.White)
                        }

                        DropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false }
                        ) {
                            sourceLanguages.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        source = option
                                        sourceMenuExpanded = false
                                        status = "Idioma de entrada: ${option.label}"
                                    }
                                )
                            }
                        }

                        Text(
                            "Selecciona el idioma en el que vas a hablar.",
                            color = Color(0xFF9BA3AD),
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            "IDIOMA DE DESTINO",
                            color = Color(0xFF9BA3AD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = { targetMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(target.label, color = Color.White)
                        }

                        DropdownMenu(
                            expanded = targetMenuExpanded,
                            onDismissRequest = { targetMenuExpanded = false }
                        ) {
                            targetLanguages.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        target = option
                                        targetMenuExpanded = false
                                        status = "Idioma de destino: ${option.label}"
                                    }
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = Color(0xFF151A20),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "SUBTÍTULO ORIGINAL",
                            color = Color(0xFF9BA3AD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            liveText.ifBlank { "Aquí aparecerá lo que digas…" },
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            "TRADUCCIÓN · ${target.label.uppercase()}",
                            color = Color(0xFFB8F36B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            translatedText.ifBlank { "La traducción aparecerá aquí…" },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Idioma detectado en el texto: $detectedLanguage",
                            color = Color(0xFF9BA3AD),
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    status,
                    color = Color(0xFF9BA3AD),
                    fontSize = 12.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (listening) {
                                stopListening()
                            } else if (permissionGranted) {
                                startListening()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (listening) {
                                Color(0xFF343B43)
                            } else {
                                Color(0xFFB8F36B)
                            },
                            contentColor = if (listening) {
                                Color.White
                            } else {
                                Color(0xFF10140B)
                            }
                        )
                    ) {
                        Text(if (listening) "DETENER" else "INICIAR")
                    }

                    OutlinedButton(
                        onClick = {
                            liveText = ""
                            translatedText = ""
                            detectedLanguage = "Esperando voz"
                            status = "Pantalla limpiada"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LIMPIAR", color = Color.White)
                    }
                }

                Text(
                    "Prototipo 0.2 · El micrófono escucha el entorno. No captura directamente el audio interno de WhatsApp.",
                    color = Color(0xFF707985),
                    fontSize = 10.sp
                )
            }
        }
    }
}
