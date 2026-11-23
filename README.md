# NOVARE LINGUA — MVP 0.1

Primer prototipo Android: voz por micrófono → detección de idioma → traducción → subtítulos en pantalla.

## Requisitos
- Android Studio reciente compatible con AGP 9.5
- JDK 17
- Android SDK Platform 37
- Un dispositivo Android o emulador con micrófono y servicio de reconocimiento de voz

## Abrir
1. Extrae el ZIP.
2. Abre la carpeta `NOVARE_LINGUA_MVP_01` desde Android Studio.
3. Espera a que Gradle sincronice y descargue dependencias.
4. Ejecuta `app` en un dispositivo Android.
5. Acepta el permiso de micrófono y pulsa INICIAR.

## Qué hace esta versión
- Reconoce voz usando el servicio de reconocimiento de voz disponible en Android.
- Muestra resultados parciales cuando el servicio los proporciona.
- Identifica el idioma del texto reconocido con ML Kit.
- Traduce a uno de los idiomas del selector usando modelos descargables de ML Kit.
- No guarda transcripciones.

## Limitaciones conocidas
- Esta versión procesa una frase por sesión; vuelve a pulsar INICIAR para otra frase.
- La detección automática depende del texto que devuelva el reconocedor.
- El reconocimiento puede depender de la conexión, del servicio de voz y de los idiomas instalados en el dispositivo.
- La aplicación escucha el micrófono, no captura el audio interno de WhatsApp.
- No promete subtítulos de llamadas bidireccionales. Android restringe la captura del audio de otras apps y WhatsApp no expone una API pública para obtener el audio de sus llamadas.
- El overlay y la integración con llamadas se desarrollarán después de validar este MVP.

## Stack
- Kotlin
- Jetpack Compose / Material 3
- Android SpeechRecognizer
- ML Kit Language Identification
- ML Kit Translation
