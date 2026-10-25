# JARVIS para Android

Versión **0.2.0 — Control del teléfono**, para **Android 10 o superior**. Proyecto independiente de [JARVIS PC](https://github.com/luisfabricio7771/jarvis-pc); sus archivos no se modifican.

## Instalar y habilitar

1. Instala `artifacts/JARVIS-Android-0.2.0.apk` sobre la versión anterior, sin desinstalarla. Se conservan clave, preferencias e historial cuando la firma coincide.
2. En **Ajustes**, configura la clave Gemini si aún no existe.
3. Abre **Control del teléfono** y activa **Permitir acciones por voz**.
4. Para interactuar dentro de apps, activa **Permitir control de pantalla**, elige **Apps autorizadas** y habilita el servicio **Control de pantalla de JARVIS** desde accesibilidad de Android.
5. Si quieres que Gemini encuentre los controles, activa **Compartir etiquetas visibles con Gemini** y revisa la explicación. Puede incluir nombres u otros datos visibles de las apps seleccionadas. No se comparten capturas, contraseñas ni el texto actual de los campos editables.
6. Deja **Revisar cada toque y desplazamiento** activo para las primeras pruebas. La escritura siempre exige revisar el texto.
7. Regresa a JARVIS, inicia la escucha y concede los permisos solicitados. Di **JARVIS**, espera el saludo y pide la acción.

Con control por voz habilitado, la conversación continúa al cambiar de app. La notificación permite volver a JARVIS o **Detener**. Bloquear la pantalla o cerrar la tarea detienen la escucha; no vuelve a arrancar por sí sola. Sin control por voz se conserva la pausa al salir de la app.

Android puede imponer restricciones adicionales a accesibilidad y a abrir apps desde segundo plano. Se habilitan desde el teléfono; JARVIS no concede sus propios permisos ni sortea restricciones del sistema.

## Acciones implementadas

- Abrir apps instaladas por nombre; devolver coincidencias si el nombre es ambiguo.
- Abrir Wi-Fi, Bluetooth, sonido, pantalla, batería y otros ajustes.
- Ajustar volumen multimedia o de la voz de JARVIS.
- Preparar alarmas y temporizadores en la app de reloj, sin ocultar su interfaz.
- Preparar marcador y borradores SMS, con revisión de número/contenido. El usuario pulsa la acción final en la app correspondiente.
- Abrir mapas y direcciones HTTPS.
- Consultar hora/zona horaria, batería y volumen del teléfono.
- Leer bajo petición etiquetas de controles de apps autorizadas; tocar, escribir, desplazar listas, volver atrás, ir al inicio y abrir recientes.

El control de pantalla usa nodos de accesibilidad, no coordenadas arbitrarias ni capturas. Los identificadores caducan y se invalidan al cambiar la pantalla o después de actuar. Se verifica otra vez el control antes de ejecutar. Si una app no expone controles accesibles o no acepta la acción, se informa y requiere uso manual.

Hay etiquetas y controles finales que se dejan expresamente para intervención manual, por ejemplo enviar, pagar, confirmar, eliminar, instalar o permitir acceso. No se automatizan contraseñas ni permisos del sistema. El resultado «acción solicitada/aceptada» no prueba que una app haya completado un envío, compra o guardado: el modelo debe comprobar la nueva pantalla y reconocer límites.

## Ejemplos para probar

- «Abre YouTube».
- «Toca Buscar» (con YouTube autorizado, accesibilidad y etiquetas activas).
- «Escribe música relajante» y revisa el texto antes de continuar.
- «Baja un poco» o «Vuelve atrás».
- «Pon el volumen multimedia al cincuenta por ciento».
- «Prepara un temporizador de cinco minutos».
- «Abre los ajustes de Wi-Fi».

La pantalla **Control del teléfono** también ofrece pruebas locales de apps, reloj, ajustes y estado **sin usar Gemini ni una clave API**.

## Voz, configuración y memoria local

Gemini **3.8 Live**, con su voz predeterminada, conversación simultánea e interrupción por voz. Activación local Vosk en español y filtro Silero incluidos en la APK. Gemini necesita Internet, acceso al modelo y cuota; la app no garantiza uso gratuito.

La clave se cifra con AES-GCM y Android Keystore. Historial SQLite limitado a 500 mensajes, sin grabaciones de audio; no se envía automáticamente al modelo. Tres colores: cian, azul y ámbar. No existe todavía sincronización con PC ni memoria inteligente compartida.

Los permisos de control y accesibilidad están desactivados por defecto. La selección de apps y el consentimiento para compartir etiquetas son separados. Los eventos de accesibilidad solo invalidan identificadores locales; no se guardan ni transmiten como un registro continuo.

## Compilar

JDK 17, Android SDK 35, Build Tools 35.0.0 y Platform Tools. Configura `JAVA_HOME` y `local.properties` con `sdk.dir`.

```powershell
.\build.ps1
```

Ejecuta pruebas, Android Lint y `assembleDebug`, y deja el APK en `artifacts/`. Para evitar bloqueos de OneDrive, `build.ps1` coloca archivos generados, cachés y caché de proyecto en una carpeta temporal propia; su ruta aparece en `.tools/build-root.txt`. El código permanece en el repositorio. Los informes están en `app/reports/` dentro de esa carpeta de compilación.

En macOS/Linux o Android Studio se puede usar `./gradlew testDebugUnitTest lintDebug assembleDebug` con la salida habitual en `app/build/`. Gradle y los modelos se descargan de fuentes oficiales con SHA-256 fijo. La APK es de desarrollo; SDK, cachés, modelos descargados, claves y APK no se suben como código a Git.

## Validación y límites

Ver [docs/PROGRESS.md](docs/PROGRESS.md), [docs/TESTING.md](docs/TESTING.md), [docs/INSTALL.md](docs/INSTALL.md) y [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Las pruebas de interfaces y accesibilidad usan ventanas Android simuladas por Robolectric en API 29 y 35. **Falta validar instalación, Gemini real, micrófono, servicio entre apps, accesibilidad, Bluetooth y batería en un teléfono físico.** No se afirma compatibilidad con todas las interfaces de terceros.

Fuentes: [Gemini Live](https://ai.google.dev/gemini-api/docs/live-api/tools), [intents Android](https://developer.android.com/guide/components/intents-common), [servicios visibles](https://developer.android.com/develop/background-work/services/fgs/service-types), [accesibilidad Android](https://developer.android.com/guide/topics/ui/accessibility/service).
