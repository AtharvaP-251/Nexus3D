package com.nexus.nexus3d

import java.nio.ByteBuffer

object DspNativeBridge {
    init {
        System.loadLibrary("dsp_native")
    }

    /**
     * Set the gain value for the C++ DSP stub
     */
    external fun setGain(gain: Float)

    /**
     * Process audio directly using a DirectByteBuffer
     */
    external fun processAudio(byteBuffer: ByteBuffer, size: Int)
}
