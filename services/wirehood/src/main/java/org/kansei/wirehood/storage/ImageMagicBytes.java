package org.kansei.wirehood.storage;

import java.util.Optional;

// Magic-byte sniffing for uploaded thumbnails, the declared Content-Type/filename extension is client-supplied and never trusted, only the actual file bytes decide what a submission really is
public final class ImageMagicBytes {

    private static final int[] JPEG = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageMagicBytes() {
    }

    public static Optional<String> detectExtension(byte[] header) {
        if (matches(header, JPEG)) {
            return Optional.of("jpg");
        }
        if (matches(header, PNG)) {
            return Optional.of("png");
        }
        return Optional.empty();
    }

    private static boolean matches(byte[] header, int[] signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((header[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
