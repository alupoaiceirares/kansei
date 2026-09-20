package org.kansei.tailwind.photo;

import java.util.Optional;

/**
 * Decides what an image really is from its first bytes. A declared content type or file name is never trusted.
 */
public final class ImageMagicBytes {

    private static final int[] JPEG = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageMagicBytes() {
    }

    public static Optional<String> detectExtension(byte[] data) {
        if (matches(data, JPEG)) {
            return Optional.of("jpg");
        }
        if (matches(data, PNG)) {
            return Optional.of("png");
        }
        return Optional.empty();
    }

    public static String contentTypeFor(String extension) {
        return "png".equals(extension) ? "image/png" : "image/jpeg";
    }

    private static boolean matches(byte[] data, int[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
