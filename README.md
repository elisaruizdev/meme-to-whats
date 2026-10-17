# meme-to-whats
Biblioteca personal de memes, vídeos e imágenes para organizar y compartir por WhatsApp.

App Android (Kotlin + Jetpack Compose). Todo se guarda en el móvil: sin registro ni servidor.

## Qué hace (MVP)

- **Añadir** fotos, GIF y vídeos con el selector de Android, o compartiéndolos con la app
  desde WhatsApp o la galería ("Compartir → Meme to Whats").
  Los archivos se copian a la app, así no se pierden si borras el original.
- **Organizar**: título, *frase o sonido* (lo que se oye), etiquetas, categoría y favoritos.
- **Buscar** por título, frase/sonido, etiquetas y categoría (sin importar tildes ni mayúsculas),
  y filtrar por favoritos, vídeos, imágenes o categoría.
- **Compartir**: botón directo a WhatsApp, o menú de compartir de Android.
  En la cuadrícula, **mantén pulsado** un meme para enviarlo directo a WhatsApp.

## Cómo ejecutarla

1. Abre esta carpeta en Android Studio y espera a que sincronice Gradle.
2. Conecta el móvil con la depuración USB activada (o usa un emulador) y pulsa ▶ Run.

Desde terminal: `./gradlew assembleDebug` genera `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura

```
app/src/main/java/com/memetowhats/app/
├── data/        Room (items, categorías) y LibraryRepository (importar, guardar, borrar)
├── ui/library/  Pantalla principal: búsqueda, filtros y cuadrícula
├── ui/detail/   Vista previa, edición, favorito, borrar y compartir
└── util/        Búsqueda sin tildes y envío a WhatsApp
```

## Próximos pasos

- Transcripción automática del audio de los vídeos para buscar por lo que se dice.
- Cuentas y chat propio (Firebase u otro backend), subiendo los archivos a almacenamiento online.
