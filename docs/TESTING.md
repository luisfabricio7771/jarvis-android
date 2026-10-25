# Validación de JARVIS Android 0.2.0

Entrega: 25 de octubre de 2026, America/Mexico_City.

## Automatizada

`build.ps1` ejecuta pruebas JUnit/Robolectric, Android Lint y compilación. La carpeta temporal seleccionada por el script aparece en `.tools/build-root.txt`; dentro de `app/reports/` están los informes. La salida se aisló de OneDrive después de detectar bloqueos de sus archivos generados.

Se comprobaron:

- Gemini 3.8 Live con voz predeterminada, parámetros compatibles y herramientas habilitadas solo con consentimiento de control.
- WebSocket real contra MockWebServer: confirmación, PCM, transcripciones, interrupción, cierre correcto del servidor de prueba, resultados correlacionados por ID, rechazo de duplicados y cancelaciones.
- Validación estricta: parámetros permitidos, enteros acotados, números sin USSD/extensiones, HTTPS sin credenciales, nombre de app exacto o ambiguo.
- Intents Android de marcador, SMS, reloj, mapas y navegador; ninguna llamada/SMS se envía directamente. Interfaces del reloj visibles.
- Cola de revisión acotada, ordenada, cancelable y con caducidad. Una acción cancelada o expirada no puede aprobarse.
- Servicio de conversación sin autoarranque de audio al enlazar, sin reinicio persistente, parada al apagar pantalla y panel local sin clave/micrófono.
- Accesibilidad en ventanas simuladas: selección de apps, consentimiento independiente para etiquetas, exclusión de contraseñas, contenido actual de campos y texto no interactivo; toque por identificador exacto; controles finales manuales; rechazo por cambio de posición; invalidación tras desplazamiento manual; desplazamiento nativo; escritura detenida hasta revisión y operación nativa con el texto revisado.
- Identificadores por app con vencimiento; limpieza al detener o reemplazar contexto; permiso de enlace de accesibilidad reservado al sistema.
- Persistencia SQLite/preferencias, roles válidos, borrado y arranque/cierre en API 29 y 35 simuladas.
- Filtro de voz: ruido, clic aislado, voz sostenida, preámbulo, cierre por silencio y falso positivo casi inaudible.

`docs/build-verification.json` registra el APK, SHA-256, tamaño, resultados y alineación. Se verifica con `apksigner`, `aapt` y `zipalign`. Las bibliotecas arm64 se revisan para segmentos ELF de 16 KiB. La firma se compara con la de 0.1.0 para permitir actualización local.

## Modelos de voz

Se conserva la validación de 0.1.0 con los mismos modelos: Vosk de escritorio 0.3.45, ONNX Runtime 1.22.0 y voz sintética Microsoft Sabina Desktop. Tres formas de JARVIS activaron; ocho frases sin la palabra, silencio y clics no activaron. Silero detectó voz en 19 cuadros y cero cuadros sobre el umbral para silencio, ruido blanco y clics. Informes en `wake-validation.json` y `silero-validation.json`. No son grabaciones personales ni pruebas de micrófono Android.

## Pendiente en teléfono físico

1. Actualizar sin perder clave/historial y comprobar Android 10+.
2. Conceder micrófono y notificaciones; probar apertura de apps sin cortar la voz, detener desde notificación y bloqueo de pantalla.
3. Habilitar accesibilidad manualmente, autorizar una app y revisar consentimiento de etiquetas.
4. Comprobar botones, listas y escritura en esa app; cancelar una revisión y comprobar que no se ejecuta.
5. Cambiar de pantalla durante una revisión: debe rechazar controles antiguos. Revocar acceso a una app o a etiquetas y comprobar el rechazo.
6. Verificar que contraseñas/campos sensibles no se exponen y que controles finales requieren intervención manual.
7. Probar ajustes, volumen, reloj, marcador y SMS en las apps instaladas del teléfono.
8. Probar conexión real de Gemini, cuota, Internet, auriculares/Bluetooth, ruido, pronunciación, temperatura y batería.

No hay teléfono conectado: esta entrega no afirma validación de hardware ni de todas las apps de terceros. Algunos pasos requieren interacción manual, y Android/fabricante pueden restringir apertura de apps o accesibilidad.
