# JARVIS para Android

Primera versión nativa **0.1.0**, para **Android 10 o superior**. Proyecto independiente de [JARVIS PC](https://github.com/luisfabricio7771/jarvis-pc): no depende del programa de Windows ni modifica sus archivos.

## Instalar la primera APK

En esta entrega el archivo local es `artifacts/JARVIS-Android-0.1.0.apk` (aproximadamente 124 MiB). El APK no se guarda como archivo de código en Git; las instrucciones de compilación permiten reconstruirlo.

1. Copia el APK a tu teléfono por USB o tu método habitual de transferencia.
2. Ábrelo desde Archivos y permite la instalación desde esa aplicación si Android lo solicita.
3. Abre **JARVIS → Ajustes** y pega tu clave de Google AI Studio. Pulsa **Guardar**.
4. Pulsa **Activar escucha**, concede el permiso de micrófono y vuelve a pulsar el botón si aparece «Micrófono autorizado».
5. Di **JARVIS**, espera el saludo y conversa. Puedes intervenir por voz durante las respuestas.
6. **Detener** cierra la conversación y apaga el micrófono. **Conversar ahora** permite probar el audio sin la palabra de activación.

Esta APK está firmada para pruebas locales, no es una publicación de Play Store. Conserva la firma local si quieres instalar futuras actualizaciones sobre esta misma app sin reinstalarla. No subas claves de firma al repositorio.

## Funciones de esta versión

- Gemini **3.8 Live** por WebSocket oficial: audio de entrada a 16 kHz y voz de salida a 24 kHz.
- Voz predeterminada del modelo, sin seleccionar una voz diferente.
- Activación local por «JARVIS» con Vosk en español, disponible desde Android 10. El modelo viene incluido en la APK; al primer uso se prepara en el almacenamiento interno.
- Frases competidoras para reducir confusiones con «ya vi», «ya ves», «llaves» y otras palabras parecidas. La detección exige un resultado final con confianza y duración suficientes.
- Filtro neuronal Silero para atenuar ruido antes de enviarlo a Gemini; cancelación de eco y reducción de ruido del teléfono cuando su hardware las ofrece.
- Conversación simultánea con interrupciones de voz, sin botón especial de interrupción.
- Historial de transcripciones en SQLite, limitado a 500 mensajes. No guarda grabaciones de audio ni envía el historial anterior automáticamente al modelo.
- Clave cifrada mediante AES-GCM y Android Keystore; la pantalla de configuración impide capturas. Copias de seguridad automáticas y transferencia de estos datos desactivadas.
- Colores cian, azul y ámbar. Ajustes y conversaciones persisten entre aperturas.
- La sesión se detiene al salir de la app o bloquear el teléfono. Tras 60 segundos sin actividad vuelve a esperar JARVIS si la activación está habilitada.

## Alcance y limitaciones comprobadas

La compilación, firma y pruebas automáticas pasaron. **La conversación completa con Gemini, el micrófono, altavoz, Bluetooth, Keystore físico y batería requieren prueba en un teléfono real.** Las pruebas de arranque Android 10/15 son de Robolectric, no pruebas de hardware ni de emulador.

El filtro reduce activaciones por ruido; no puede garantizar cero falsos positivos y puede reconocer voces de televisión o de otras personas. La precisión debe ajustarse con tu pronunciación y entorno.

Gemini requiere Internet, una clave con acceso al modelo y cuota disponible. La app no garantiza que el servicio sea gratuito. Si la cuenta rechaza el modelo o no tiene cuota, se muestra un error; no cambia silenciosamente a otro modelo.

La escucha con pantalla bloqueada, servicio en segundo plano, memoria inteligente, herramientas del teléfono, control de Windows y sincronización entre dispositivos quedan pendientes. El historial local es un registro: no es memoria compartida con la versión de PC.

## Compilar desde este repositorio

Requisitos: JDK 17 y Android SDK con plataforma 35, Build Tools 35.0.0 y Platform Tools. Android Studio puede preparar estos componentes; acepta las licencias desde sus herramientas oficiales. Configura `JAVA_HOME` y `local.properties` con `sdk.dir` para tu SDK.

```powershell
.\build.ps1
```

El script ejecuta `testDebugUnitTest`, `lintDebug` y `assembleDebug`, y copia el APK a `artifacts/`. También puedes ejecutar `./gradlew testDebugUnitTest lintDebug assembleDebug` en macOS/Linux.

Gradle 8.11.1 se comprueba mediante SHA-256. Antes de compilar se descargan dos modelos oficiales con SHA-256 fijo y se incluyen en la APK. No hay descarga de modelos ni Python dentro del teléfono. `build.ps1 -TestOnly` ejecuta pruebas y análisis.

No se incluyen SDK, JDK, cachés, modelos descargados, claves ni APK en Git. Los APK se generan con la firma de desarrollo de la máquina. Para distribución estable se necesita una firma de lanzamiento respaldada por separado.

## Validación y arquitectura

Consulta [docs/PROGRESS.md](docs/PROGRESS.md), [docs/TESTING.md](docs/TESTING.md) y [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- `live/`: protocolo y conexión Gemini. No envía audio hasta recibir `setupComplete`.
- `audio/`: captura y reproducción independientes; filtro de voz y vaciado de audio al intervenir.
- `wake/`: reconocimiento local sin conexión a Gemini mientras espera la palabra.
- `data/`: configuración protegida e historial local.
- `MainActivity`: interfaz y ciclo de vida. No arranca el micrófono por sí sola al abrir la app.

Fuentes oficiales: [Gemini Live WebSocket](https://ai.google.dev/gemini-api/docs/live-api/get-started-websocket), [referencia Live](https://ai.google.dev/api/live), [Vosk](https://alphacephei.com/vosk/models), [Silero VAD](https://github.com/snakers4/silero-vad/tree/v5.1).
