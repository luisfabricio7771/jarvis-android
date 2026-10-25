# Validación de JARVIS Android 0.1.0

Fecha de entrega: 24 de octubre de 2026 (America/Mexico_City).

## Automatizada

`build.ps1` ejecuta 25 pruebas JUnit y Robolectric, Android Lint y compilación del APK. El detalle de pruebas se encuentra en `app/build/reports/tests/testDebugUnitTest/`; el análisis en `app/build/reports/lint-results-debug.html`. Son archivos locales regenerables.

Cobertura funcional comprobada:

- Configuración de Gemini 3.8 Live sin imponer voz ni parámetros incompatibles.
- PCM de 16 bits, frecuencia declarada, ida y vuelta sin alterar los bytes.
- Conexión con WebSocket de prueba real (MockWebServer), saludo/configuración, confirmación de sesión, audio, transcripción, fin de turno e interrupción.
- Audio bloqueado antes de `setupComplete`, después de error y al cerrar.
- Errores del proveedor sin reenviar mensajes que pudieran contener la clave; respuesta inválida y aviso `goAway`.
- Filtro temporal: ruido, clic aislado, voz sostenida, preámbulo de audio, cierre tras silencio y falsos positivos casi inaudibles.
- Palabra completa de activación, sin activar por subcadenas.
- SQLite persistente al cerrar y reabrir, borrado, validación de roles y preferencias persistentes.
- Arranque y cierre sin activar micrófono o conectarse automáticamente, tanto con API 29 como API 35 simuladas por Robolectric.

El APK se comprobó con `apksigner verify`, `aapt dump badging` y `zipalign -c -P 16 4`. Además se verificaron los segmentos ELF de las cuatro bibliotecas arm64: todos admiten alineación de 16 KiB. `docs/build-verification.json` registra pruebas, tamaño, SHA-256 y alineación. Esta verificación no sustituye probar la instalación.

## Modelos reales, audio sintético

Se ejecutaron Vosk de escritorio 0.3.45 y ONNX Runtime 1.22.0 con los mismos modelos incluidos en la APK. La biblioteca Android de Vosk es 0.3.75; el micrófono de Android no participó en estas pruebas.

Voz generada por Windows SpeechSynthesizer, Microsoft Sabina Desktop: «Jarvis», «Hola Jarvis» y «Oye Jarvis» activaron. Ocho frases sin la palabra, silencio y clics simulados no activaron. La primera gramática confundía «ya vi»/«ya ves» y se corrigió antes de la entrega.

Silero marcó 19 cuadros de voz en «Jarvis» y cero cuadros sobre el umbral en silencio, ruido blanco y clics. Ver `docs/wake-validation.json` y `docs/silero-validation.json`. No se usaron grabaciones personales ni una clave real de Gemini.

Para repetir en Windows con voz Sabina disponible, crea un entorno Python separado, instala `tools/voice-test-requirements.txt` y ejecuta `tools/Test-SyntheticVoice.ps1 -Python <ruta-al-python-del-entorno>`. Los audios sintéticos quedan en `.tools/` y no se suben a Git.

## En teléfono: pendiente

1. Instalar en Android 10 o superior y abrir sin otorgar permiso: no debe escuchar.
2. Guardar la clave desde Ajustes, cerrar/abrir y comprobar que sigue disponible sin mostrarla en pantalla.
3. Conceder micrófono, activar espera y probar JARVIS, frases parecidas, teclado, golpes y silencio.
4. Esperar el saludo, conversar e intervenir mientras responde. Comprobar altavoz y, por separado, audífonos/Bluetooth.
5. Probar Internet desconectado, clave inválida y cuota agotada: debe cerrar la sesión y mostrar un error sin clave.
6. Salir o bloquear el teléfono: comprobar que desaparece el indicador de micrófono después de liberar el recurso.
7. Comprobar retorno a espera tras inactividad, conservación del historial y borrado.
8. Evaluar pronunciación, ruido, temperatura, memoria y batería. Ajustar sensibilidad según resultados reales.

No se afirma validación de hardware ni conversación remota exitosa hasta completar esta lista en un teléfono.
