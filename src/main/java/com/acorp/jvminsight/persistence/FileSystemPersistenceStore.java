package com.acorp.jvminsight.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class FileSystemPersistenceStore implements PersistenceStore {

  private final Path root;

  public FileSystemPersistenceStore(Path root) throws IOException {
    this.root = root.toAbsolutePath().normalize();
    Files.createDirectories(this.root);
  }

  @Override
  public void save(String key, byte[] content) throws IOException {
    Path target = resolve(key);
    Files.createDirectories(target.getParent());

    Path temporary = Files.createTempFile(target.getParent(), ".tmp-", ".part");
    try {
      Files.write(temporary, content);
      try {
        Files.move(
            temporary,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  @Override
  public Optional<byte[]> read(String key) throws IOException {
    Path target = resolve(key);
    return Files.exists(target) ? Optional.of(Files.readAllBytes(target)) : Optional.empty();
  }

  @Override
  public List<String> list(String prefix) throws IOException {
    Path prefixPath = resolve(prefix);
    Path directory = Files.isDirectory(prefixPath) ? prefixPath : prefixPath.getParent();

    if (directory == null || !Files.exists(directory)) {
      return List.of();
    }

    List<String> keys = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(directory)) {
      paths
          .filter(Files::isRegularFile)
          .forEach(
              path -> {
                String key = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                if (key.startsWith(prefix)) {
                  keys.add(key);
                }
              });
    }

    keys.sort(Comparator.naturalOrder());
    return keys;
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(resolve(key));
  }

  private Path resolve(String key) {
    if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
      throw new IllegalArgumentException("Invalid persistence key: " + key);
    }

    Path resolved = root.resolve(key).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Persistence key escapes storage root: " + key);
    }
    return resolved;
  }
}
