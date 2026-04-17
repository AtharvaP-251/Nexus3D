package com.audixlab.nexus

import android.app.Application
import com.audixlab.nexus.core.domain.DspRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NexusApplication : Application() {
    @Inject lateinit var dspRepository: DspRepository
}
