package com.acorp.jvminsight.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface PersistenceStore {

  void save(String key, byte[] content) throws IOException;

  Optional<byte[]> read(String key) throws IOException;

  List<String> list(String prefix) throws IOException;

  void delete(String key) throws IOException;
}
