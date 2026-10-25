# JARVIS Android — Progreso

## 0.1.0 · 24/10/2026

Proyecto nativo creado exclusivamente en el repositorio Android. La aplicación original de PC se conserva sin cambios.

Implementado y compilado: interfaz, tres colores, ajustes protegidos, historial SQLite, Gemini 3.8 Live con voz predeterminada, streaming simultáneo, interrupción por voz, filtro Silero y activación local Vosk para Android 10+. Modelos incluidos y verificados por SHA-256. Ciclo de vida limitado a la app visible, salida por altavoz cuando no hay dispositivo externo y cierre por inactividad.

Validado: 25 pruebas automáticas sin fallos, análisis Android sin errores, APK firmado y verificado. Pruebas con modelos reales y voz sintética: 13 escenarios de activación y cuatro de clasificación de audio. Detalles y límites en [TESTING.md](TESTING.md).

Pendiente: instalación, conversación Gemini y hardware en un teléfono; calibración de ruido y pronunciación, Bluetooth y batería. La escucha en segundo plano/pantalla bloqueada, herramientas, memoria avanzada y sincronización no forman parte de esta primera entrega.

Avisos del análisis: textos de interfaz fijados en español. Las API de audio e insets mantienen rutas compatibles con Android 10 y algunas llamadas están marcadas obsoletas por SDK 35; esto no impidió compilar ni ejecutar las pruebas.

El APK local es de desarrollo y no se publica en Play Store. Credenciales, claves de firma, conversaciones, cachés, herramientas y APK no se suben como código a GitHub.
