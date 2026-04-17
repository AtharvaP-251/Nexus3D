package com.audixlab.nexus

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.C
import java.nio.ByteBuffer

class CustomAudioProcessor : AudioProcessor {

    private var pendingAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var activeAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        pendingAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return pendingAudioFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // We can process directly in place since our effect is zero-latency and 1-to-1 input/output mapping
        // However, ExoPlayer AudioProcessors typically read from input and write to an internal buffer.
        // For processing in place to keep allocations 0, we'll just modify input and then set output.
        // ExoPlayer says: The buffer will be modified by other AudioProcessors, so we should output a new buffer if needed,
        // but ExoPlayer gives us a direct buffer. Wait, actually we can just process directly on `inputBuffer`!
        
        // Wait, to be safe with Media3 pipeline, we should allocate our own buffer if we modify it, or we can mutate input 
        // if we are positive its our turn. But Media3 requires allocating a buffer. Let's do it properly without allocating per frame.
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(java.nio.ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        outputBuffer.put(inputBuffer)
        outputBuffer.flip() // Prepare for native reading/writing
        
        // Run native C++ DSP processing
        DspNativeBridge.processAudio(outputBuffer, remaining)
        outputBuffer.position(0)
        outputBuffer.limit(remaining)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val result = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER // Need to hand it over
        return result
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        activeAudioFormat = pendingAudioFormat
    }

    override fun reset() {
        flush()
        pendingAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }
}
