# JARVIS para Android

Repositorio privado separado de JARVIS para PC.

**Estado: preparación. No existe una aplicación Android ni un APK compilado todavía.**

El código actual para Windows se conserva en https://github.com/luisfabricio7771/jarvis-pc. Este repositorio evita mezclar sus dependencias de escritorio con el futuro proyecto móvil.

## Alcance previsto

- Conversación con Gemini Live, usando su API y la voz predeterminada.
- Intervención por voz durante las respuestas.
- Activación por JARVIS y filtrado de ruido con procesamiento local cuando sea viable en Android.
- Configuración y memoria persistentes.

La interfaz móvil, arquitectura, permisos de micrófono, servicio en segundo plano, consumo de batería y pruebas en un teléfono físico están pendientes. Estas posibilidades no se presentan como funciones terminadas.

## Respaldo

GitHub conserva el código y los commits que se envían, no todos los cambios locales automáticamente. Para recuperar este repositorio, inicia sesión en GitHub Desktop y clona `luisfabricio7771/jarvis-android`.

Nunca subir claves API, datos personales, `local.properties` ni claves de firma. Un APK futuro necesitará instrucciones de compilación y un respaldo seguro de su firma, separado del código.
