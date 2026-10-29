package dev.astra.client.features;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.InputStream;

/**
 * The only class that touches JLayer (the MP3 decoder packed into Astra's jar). Kept separate so
 * that if the decoder were ever missing, only the radio fails (with a message), never the game.
 */
final class Mp3Decoder {

    private final Bitstream bits;
    private final Decoder decoder = new Decoder();
    private short[] samples;
    private int length;

    Mp3Decoder(InputStream in) {
        bits = new Bitstream(in);
    }

    /** Decodes the next frame; false at the end of the stream. */
    boolean next() throws Exception {
        Header h = bits.readFrame();
        if (h == null) {
            return false;
        }
        try {
            SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bits);
            samples = out.getBuffer();
            length = out.getBufferLength();
        } finally {
            bits.closeFrame();
        }
        return true;
    }

    short[] samples() {
        return samples;
    }

    int length() {
        return length;
    }

    int sampleRate() {
        return decoder.getOutputFrequency();
    }

    int channels() {
        return decoder.getOutputChannels();
    }
}
