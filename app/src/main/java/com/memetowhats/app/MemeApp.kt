package com.memetowhats.app

import android.app.Application
import com.memetowhats.app.data.AppDatabase
import com.memetowhats.app.data.LibraryRepository

class MemeApp : Application() {
    val repository by lazy { LibraryRepository(this, AppDatabase.create(this)) }
}
