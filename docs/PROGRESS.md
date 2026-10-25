# JARVIS Android — Progreso

## 0.2.0 · 25/10/2026

Control del teléfono implementado exclusivamente en el repositorio Android. Incluye 11 herramientas nativas (apps, ajustes, volumen, reloj, marcador, SMS, mapas, web y estado) y 5 de pantalla mediante accesibilidad opcional (controles visibles, tocar, escribir, desplazar y navegar). La integración Gemini maneja resultados por ID, duplicados y cancelaciones.

La conversación se trasladó a un servicio visible con notificación y Detener para continuar entre apps cuando el usuario habilita control. Bloquear la pantalla o cerrar la tarea apagan la escucha. No se autoarranca al reiniciar. Se conservan clave, SQLite, voz predeterminada y modelos locales de 0.1.0.

Accesibilidad requiere activación manual de Android, selección de apps y consentimiento separado para compartir etiquetas. Tiene revisión configurable de navegación, revisión obligatoria de escritura, controles finales manuales y rechazo de nodos antiguos. No captura pantallas ni transmite un registro de eventos de accesibilidad. No se controlan ajustes de seguridad o permisos mediante toques.

Validación: 89 pruebas aprobadas, Android Lint sin errores y APK firmado/verificado con la misma firma de 0.1.0. Pruebas de protocolo, acciones, ciclo de vida, privacidad y accesibilidad simulada en Android 10/15; informes y límites en [TESTING.md](TESTING.md) y [build-verification.json](build-verification.json). Compilación y firma se verifican antes de entregar. Los resultados de teléfono real, Gemini remoto y batería continúan pendientes.

Los archivos de compilación y caché se aíslan en una carpeta temporal propia para evitar bloqueos de OneDrive; el código permanece en este repositorio. No se incluyen claves, cachés, modelos ni APK como código en GitHub.

## 0.1.0 · 24/10/2026

Proyecto nativo creado exclusivamente en el repositorio Android. La aplicación original de PC se conserva sin cambios.

Implementado y compilado: interfaz, tres colores, ajustes protegidos, historial SQLite, Gemini 3.8 Live con voz predeterminada, streaming simultáneo, interrupción por voz, filtro Silero y activación local Vosk para Android 10+. Modelos incluidos y verificados por SHA-256. Ciclo de vida limitado a la app visible, salida por altavoz cuando no hay dispositivo externo y cierre por inactividad.

Validado: 25 pruebas automáticas sin fallos, análisis Android sin errores, APK firmado y verificado. Pruebas con modelos reales y voz sintética: 13 escenarios de activación y cuatro de clasificación de audio. Detalles y límites en [TESTING.md](TESTING.md).

Pendiente: instalación, conversación Gemini y hardware en un teléfono; calibración de ruido y pronunciación, Bluetooth y batería. La escucha en segundo plano/pantalla bloqueada, herramientas, memoria avanzada y sincronización no forman parte de esta primera entrega.

Avisos del análisis: textos de interfaz fijados en español. Las API de audio e insets mantienen rutas compatibles con Android 10 y algunas llamadas están marcadas obsoletas por SDK 35; esto no impidió compilar ni ejecutar las pruebas.

El APK local es de desarrollo y no se publica en Play Store. Credenciales, claves de firma, conversaciones, cachés, herramientas y APK no se suben como código a GitHub.
