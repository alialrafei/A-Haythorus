package com.acorp.jvminsight.discovery;

import java.util.List;
import java.util.stream.Collectors;

public final class JvmSelector {

  private JvmSelector() {}

  public static List<Long> selectTargetJvm(List<JvmCandidate> candidates, long selfPid) {

    List<JvmCandidate> nonSelf = candidates.stream().filter(c -> !c.isSelf(selfPid)).toList();

    if (nonSelf.isEmpty()) {
      throw new IllegalStateException("No target JVM found (only self JVM present)");
    }

    List<JvmCandidate> nonSidecar = nonSelf.stream().filter(c -> !c.looksLikeSidecar()).toList();

    if (nonSidecar.size() == 1) {
      return List.of(nonSidecar.get(0).pid());
    }

    return nonSidecar.stream().map(s -> s.pid()).collect(Collectors.toList());
    // throw new IllegalStateException(buildAmbiguousMessage(nonSidecar));
  }

  @Deprecated
  private static String buildAmbiguousMessage(List<JvmCandidate> candidates) {
    StringBuilder sb = new StringBuilder("Multiple JVMs detected. Unable to auto-select target.\n");

    candidates.forEach(c -> sb.append(" - ").append(c.describe()).append('\n'));

    sb.append("\nResolution options:\n")
        .append(" - Set env TARGET_JVM_PID\n")
        .append(" - Ensure one Java app per pod\n");

    return sb.toString();
  }
}
