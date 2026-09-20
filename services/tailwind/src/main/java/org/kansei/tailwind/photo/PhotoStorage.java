package org.kansei.tailwind.photo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aircraft photo files under the storage root. File names are generated here, never taken from a client,
 * and every read or delete checks the name against that shape before touching the disk.
 */
@Slf4j
@Component
public class PhotoStorage {

    private static final String DIRECTORY = "aircraft-photos";
    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");

    private final Path directory;

    public PhotoStorage(@Value("${tailwind.storage.root-dir}") String rootDir) {
        this.directory = Path.of(rootDir).resolve(DIRECTORY);
    }

    // Writes to a temp file first and moves it into place, so a reader never sees half a photo
    public String write(byte[] data, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "upload-", ".tmp");
            try {
                Files.write(temp, data);
                Files.move(temp, directory.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("could not store the photo file", ex);
        }
        return fileName;
    }

    public Optional<byte[]> read(String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        try {
            Path file = directory.resolve(fileName);
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException ex) {
            log.warn("could not read photo file {}: {}", fileName, ex.toString());
            return Optional.empty();
        }
    }

    public void delete(String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(fileName));
        } catch (IOException ex) {
            log.warn("could not delete photo file {}: {}", fileName, ex.toString());
        }
    }
}
