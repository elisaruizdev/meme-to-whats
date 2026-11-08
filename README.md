# meme-to-whats
Biblioteca personal de memes, vídeos e imágenes para organizar y compartir por WhatsApp.

App Android (Kotlin + Jetpack Compose). Todo se guarda en el móvil: sin registro ni servidor.

## Qué hace

Convierte tus **capturas de pantalla en stickers** y tus **grabaciones de pantalla en GIF**, y los
envía por cualquier app de mensajería.

- **Tutorial la primera vez**: explica la app con ilustraciones animadas, pide el permiso de fotos
  y guía para hacer la primera captura (con trucos según la marca del móvil). Espera a detectarla
  y la abre en el editor.
- **Inicio = «Mis creaciones»**: los stickers y GIF que has hecho, con filtros Todo / Stickers / GIF.
  Mantén pulsado uno para enviarlo rápido.
- **⊕ Convierte una captura**: solo las capturas y grabaciones de pantalla (no el resto del álbum),
  la más nueva primero. «Otra imagen o vídeo» para cualquier otro archivo.
- **Pegar**: si copias una captura («Copiar» en la vista previa), la app ofrece convertirla.
- **Editor**
  - Sticker: recorte, texto opcional → WebP 512×512 transparente (≤100 KB).
  - GIF: tramo (máx. 8 s), recorte, tamaño y texto. Codificador GIF propio (`media/GifEncoder.kt`).
  - **Subtítulos** (vídeos con sonido): voz a texto en el móvil con [Vosk](https://alphacephei.com/vosk/),
    sincronizados palabra a palabra y editables. El modelo de español (~40 MB) se descarga una vez;
    después funciona sin internet y el audio no sale del móvil.
  - «Quedarme solo con el sticker/GIF»: lo guarda en la galería y manda la captura original
    a la papelera (Android pide confirmarlo).
- **Enviar a**: iconos de las apps de mensajería instaladas (WhatsApp, Telegram, Signal…) y «Más».
  Lo enviado sin guardar se guarda solo en la galería (una vez, sin duplicados).
- **WhatsApp**: no acepta stickers sueltos de otras apps, solo packs. Cada sticker entra en el pack
  «Meme to Whats» (el más nuevo primero), que se completa con 3 stickers de inicio hasta que
  tengas 3 propios. El pack se sirve sin caché para que WhatsApp no pida actualizarlo.
- La **biblioteca** (memes con etiquetas y categorías) sigue en el icono de lista; los stickers y GIF
  creados también se guardan ahí.

## Cómo ejecutarla

1. Abre esta carpeta en Android Studio y espera a que sincronice Gradle.
2. Conecta el móvil con la depuración USB activada (o usa un emulador) y pulsa ▶ Run.

Tests: `./gradlew testDebugUnitTest` (comprueba el codificador GIF).

Desde terminal: `./gradlew assembleDebug` genera `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura

```
app/src/main/java/com/memetowhats/app/
├── media/          Capturas (MediaStore), recorte/texto/WebP, codificador GIF, audio y voz
│                   a texto (Vosk), galería y quitar la captura original
├── stickers/       Packs de stickers y el ContentProvider que lee WhatsApp
├── ui/onboarding/  Tutorial de la primera vez
├── ui/home/        Inicio: «Mis creaciones»
├── ui/captures/    Convierte una captura + aviso de imagen copiada
├── ui/editor/      Recorte, texto, tramo, subtítulos y resultado
├── ui/share/       Fila «Enviar a» con las apps de mensajería
├── ui/stickers/    Packs para WhatsApp y envío como sticker
├── data/           Room (items, categorías) y LibraryRepository
├── ui/library/     Biblioteca: búsqueda, filtros y cuadrícula
├── ui/detail/      Vista previa, edición, favorito, borrar y enviar
└── util/           Permisos, ajustes, búsqueda sin tildes y compartir
```

La APK solo incluye procesadores ARM (`abiFilters`), por el tamaño del motor de Vosk.

## Próximos pasos

- Usar la transcripción de los vídeos también para buscar en la biblioteca por lo que se dice.
- Cambiar el nombre de la app, que ya no es solo para WhatsApp.
- Cuentas y chat propio (Firebase u otro backend), subiendo los archivos a almacenamiento online.
