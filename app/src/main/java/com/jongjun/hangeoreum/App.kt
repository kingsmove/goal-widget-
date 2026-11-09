package com.jongjun.hangeoreum

import android.app.Application
import com.jongjun.hangeoreum.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
