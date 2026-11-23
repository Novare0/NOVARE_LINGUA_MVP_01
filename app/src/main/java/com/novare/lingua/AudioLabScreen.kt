
package com.novare.lingua

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED

@Composable
fun AudioLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Esperando autorización") }
    var level by remember { mutableFloatStateOf(0f) }
    var capturing by remember { mutableStateOf(false) }

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK &&
            result.data != null
        ) {
            val serviceIntent = Intent(
                context,
                AudioCaptureService::class.java
            ).apply {
                putExtra(
                    AudioCaptureService.EXTRA_RESULT_CODE,
                    result.resultCode
                )
                putExtra(
                    AudioCaptureService.EXTRA_RESULT_DATA,
                    result.data
                )
            }

            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                capturing = true
                status = "Iniciando captura..."
            } catch (e: Exception) {
                status = "No se pudo iniciar: ${e.localizedMessage}"
            }
        } else {
            status = "Autorización cancelada."
        }
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            projectionLauncher.launch(
                projectionManager.createScreenCaptureIntent()
            )
        } else {
            status = "Permiso de audio denegado."
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                when (intent?.action) {
                    AudioCaptureService.ACTION_STATUS -> {
                        status = intent.getStringExtra(
                            AudioCaptureService.EXTRA_STATUS
                        ) ?: status

                        if (status == "Captura detenida.") {
                            capturing = false
                            level = 0f
                        }
                    }

                    AudioCaptureService.ACTION_LEVEL -> {
                        level = intent.getFloatExtra(
                            AudioCaptureService.EXTRA_LEVEL,
                            0f
                        ).coerceIn(0f, 100f)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioCaptureService.ACTION_STATUS)
            addAction(AudioCaptureService.ACTION_LEVEL)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    fun startCapture() {
        if (Build.VERSION.SDK_INT < 29) {
            status = "Se requiere Android 10 o superior."
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            projectionLauncher.launch(
                projectionManager.createScreenCaptureIntent()
            )
        } else {
            microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopCapture() {
        context.stopService(
            Intent(context, AudioCaptureService::class.java)
        )
        capturing = false
        level = 0f
        status = "Deteniendo captura..."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090B0E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "NOVARE / AUDIO LAB",
                color = Color(0xFFB8F36B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Diagnóstico de audio",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "Monitor de reproducción multimedia autorizada.",
                color = Color(0xFF9BA3AD),
                fontSize = 13.sp
            )

            Surface(
                color = Color(0xFF151A20),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "NIVEL DE AUDIO",
                        color = Color(0xFF9BA3AD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "${level.toInt()}%",
                        color = Color(0xFFB8F36B),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(
                                Color(0xFF343B43),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(level / 100f)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFFB8F36B),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                    }

                    Text(
                        status,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }

            Button(
                onClick = {
                    if (capturing) stopCapture() else startCapture()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (capturing) {
                        Color(0xFF343B43)
                    } else {
                        Color(0xFFB8F36B)
                    },
                    contentColor = if (capturing) {
                        Color.White
                    } else {
                        Color(0xFF10140B)
                    }
                )
            ) {
                Text(
                    if (capturing) "DETENER CAPTURA" else "INICIAR CAPTURA"
                )
            }

            Text(
                "La captura depende de que la aplicación de origen permita compartir su audio. Las llamadas y otras fuentes restringidas no estarán disponibles.",
                color = Color(0xFF707985),
                fontSize = 11.sp
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    if (capturing) stopCapture()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VOLVER AL TRADUCTOR", color = Color.White)
            }
        }
    }
}
