package org.kansei.tailwind.photo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoStorageTest {

    @Test
    void writesReadsAndDeletesUnderTheStorageRoot(@TempDir Path root) {
        PhotoStorage storage = new PhotoStorage(root.toString());

        String fileName = storage.write(new byte[]{1, 2, 3}, "jpg");

        assertThat(fileName).endsWith(".jpg");
        assertThat(root.resolve("aircraft-photos").resolve(fileName)).exists();
        assertThat(storage.read(fileName)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(1, 2, 3));

        storage.delete(fileName);

        assertThat(storage.read(fileName)).isEmpty();
    }

    @Test
    void leavesNoTemporaryFilesBehind(@TempDir Path root) throws IOException {
        PhotoStorage storage = new PhotoStorage(root.toString());

        storage.write(new byte[]{1}, "png");

        try (var files = Files.list(root.resolve("aircraft-photos"))) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp"));
        }
    }

    @Test
    void refusesNamesThatAreNotOurOwnGeneratedOnes(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("aircraft-photos"));
        Files.writeString(root.resolve("secret.txt"), "top secret");
        PhotoStorage storage = new PhotoStorage(root.toString());

        assertThat(storage.read("../secret.txt")).isEmpty();
        assertThat(storage.read("..\\secret.txt")).isEmpty();
        assertThat(storage.read("anything.jpg")).isEmpty();
        storage.delete("../secret.txt");

        assertThat(root.resolve("secret.txt")).exists();
    }
}
