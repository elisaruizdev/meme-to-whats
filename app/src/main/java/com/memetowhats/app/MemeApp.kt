package com.memetowhats.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.video.VideoFrameDecoder
import com.memetowhats.app.data.AppDatabase
import com.memetowhats.app.data.LibraryRepository

class MemeApp : Application(), SingletonImageLoader.Factory {
    val repository by lazy { LibraryRepository(this, AppDatabase.create(this)) }

    // Coil necesita estos decodificadores para las miniaturas de vídeo y los GIF animados.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
}
