package com.acorp.jvminsight.discovery;

import com.sun.tools.attach.VirtualMachineDescriptor;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JvmProcessLocator {

  private static final Logger LOGGER = LoggerFactory.getLogger(JvmProcessLocator.class);

  private JvmProcessLocator() {}

  public static List<Long> autoDetectTargetJvmPid() {

    LOGGER.info("Starting JVM auto process scan.");

    Optional<Long> explicit = readPidFromEnv();

    if (explicit.isPresent()) {
      LOGGER.info("Using TARGET_JVM_PID override: {}", explicit.get());
      return List.of(explicit.get());
    }

    long selfPid = ProcessHandle.current().pid();
    LOGGER.info("Current JVM PID (watcher): {}", selfPid);

    List<JvmCandidate> candidates = discoverAttachableJvms();

    LOGGER.info("Discovered {} attachable JVM(s).", candidates.size());
    candidates.forEach(candidate -> LOGGER.info("Candidate JVM -> {}", candidate.describe()));

    List<Long> targets = JvmSelector.selectTargetJvm(candidates, selfPid);

    if (targets.isEmpty()) {
      LOGGER.warn("No target JVMs selected from {} candidates.", candidates.size());
    } else {
      LOGGER.info("Selected {} target JVM(s): {}", targets.size(), targets);
    }

    return targets;
  }

  private static List<JvmCandidate> discoverAttachableJvms() {
    try {
      return VirtualMachine.list().stream()
          .map(JvmCandidate::new)
          .filter(candidate -> candidate.pid() > 0)
          .toList();
    } catch (Exception ex) {
      LOGGER.warn("Attach API JVM scan failed; falling back to ProcessHandle scan.", ex);
      return ProcessHandle.allProcesses()
          .filter(ProcessHandle::isAlive)
          .filter(JvmProcessLocator::isJavaProcess)
          .map(process -> new JvmCandidate(process.pid(), process.info()))
          .toList();
    }
  }

  private static boolean isJavaProcess(ProcessHandle process) {
    return process.info().command().map(cmd -> cmd.toLowerCase().contains("java")).orElse(false);
  }

  private static Optional<Long> readPidFromEnv() {

    String value = System.getenv("TARGET_JVM_PID");

    if (value == null || value.isBlank()) {
      LOGGER.debug("TARGET_JVM_PID environment variable not found.");
      return Optional.empty();
    }

    try {
      long pid = Long.parseLong(value);
      LOGGER.info("Found TARGET_JVM_PID={} from environment.", pid);
      return Optional.of(pid);
    } catch (NumberFormatException ex) {
      LOGGER.error("Invalid TARGET_JVM_PID value '{}'. Expected a numeric PID.", value, ex);
      return Optional.empty();
    }
  }
}
