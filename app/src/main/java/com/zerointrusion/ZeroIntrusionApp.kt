package com.zerointrusion

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.zerointrusion.data.security.BiometricKeyAccess
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZeroIntrusionApp : Application(), DefaultLifecycleObserver {
    @Inject lateinit var keyAccess: BiometricKeyAccess

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        keyAccess.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        keyAccess.setForeground(false)
    }
}
