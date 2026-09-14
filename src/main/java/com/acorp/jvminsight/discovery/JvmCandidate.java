package com.acorp.jvminsight.discovery;

import com.sun.tools.attach.VirtualMachineDescriptor;
import java.util.Arrays;
import java.util.Optional;

public final class JvmCandidate {

  private final long pid;
  private final Optional<String> displayName;
  private final Optional<String> command;
  private final Optional<String[]> arguments;

  public JvmCandidate(VirtualMachineDescriptor descriptor) {
    this.pid = parsePid(descriptor.id());
    this.displayName = optional(descriptor.displayName());
    this.command = Optional.empty();
    this.arguments = Optional.empty();
  }

  public JvmCandidate(long pid, ProcessHandle.Info info) {
    this.pid = pid;
    this.displayName = Optional.empty();
    this.command = info.command();
    this.arguments = info.arguments();
  }

  public long pid() {
    return pid;
  }

  public boolean isSelf(long selfPid) {
    return pid == selfPid;
  }

  public Optional<String> displayName() {
    return displayName;
  }

  public Optional<String> command() {
    return command;
  }

  public Optional<String[]> arguments() {
    return arguments;
  }

  public String describe() {
    return "PID="
        + pid
        + " DISPLAY="
        + displayName.orElse("<empty>")
        + " CMD="
        + command.orElse("<empty>")
        + " ARGS="
        + arguments.map(a -> String.join(" ", a)).orElse("<empty>");
  }

  public boolean looksLikeSidecar() {
    return containsSidecarMarker(displayName.orElse(""))
        || containsSidecarMarker(command.orElse(""))
        || arguments()
            .map(args -> Arrays.stream(args).anyMatch(JvmCandidate::containsSidecarMarker))
            .orElse(false);
  }

  private static boolean containsSidecarMarker(String value) {
    String normalized = value.toLowerCase();
    return normalized.contains("jvm-watcher")
        || normalized.contains("jvminsight")
        || normalized.contains("a-haythorus");
  }

  private static Optional<String> optional(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private static long parsePid(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return -1L;
    }
  }
}
