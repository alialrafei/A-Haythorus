package com.acorp.jvminsight.discovery;

import java.util.Arrays;
import java.util.Optional;

public class JvmCandidate {

  private final long pid;
  private final ProcessHandle.Info info;

  public JvmCandidate(ProcessHandle handle) {
    this.pid = handle.pid();
    this.info = handle.info();
  }

  public long pid() {
    return pid;
  }

  public boolean isSelf(long selfPid) {
    return pid == selfPid;
  }

  public Optional<String> command() {
    return info.command();
  }

  public Optional<String[]> arguments() {
    return info.arguments();
  }

  public String describe() {
    return "PID="
        + pid
        + " CMD="
        + command().orElse("<empty>")
        + " ARGS="
        + arguments().map(a -> String.join(" ", a)).orElse("<empty>");
  }

  public boolean looksLikeSidecar() {
    return arguments()
        .map(
            args ->
                Arrays.stream(args)
                    .anyMatch(a -> a.contains("jvm-watcher") || a.contains("jvminsight")))
        .orElse(false);
  }
}
