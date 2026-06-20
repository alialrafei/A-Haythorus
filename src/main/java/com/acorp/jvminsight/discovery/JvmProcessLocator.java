package com.acorp.jvminsight.discovery;

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

    List<JvmCandidate> candidates =
        ProcessHandle.allProcesses()
            .filter(ProcessHandle::isAlive)
            .filter(JvmProcessLocator::isJavaProcess)
            .map(JvmCandidate::new)
            .toList();

    LOGGER.info("Discovered {} running Java process(es).", candidates.size());

    candidates.forEach(
        candidate ->
            LOGGER.info(
                "Candidate JVM -> pid={}, command={}", candidate.pid(), candidate.command()));

    List<Long> targets = JvmSelector.selectTargetJvm(candidates, selfPid);

    if (targets.isEmpty()) {

      LOGGER.warn("No target JVMs selected from {} candidates.", candidates.size());

    } else {

      LOGGER.info("Selected {} target JVM(s): {}", targets.size(), targets);
    }

    return targets;
  }

  private static boolean isJavaProcess(ProcessHandle process) {

    boolean isJava = process.info().command().map(cmd -> cmd.contains("java")).orElse(false);

    if (LOGGER.isDebugEnabled() && isJava) {

      LOGGER.debug(
          "Java process detected -> pid={}, command={}",
          process.pid(),
          process.info().command().orElse("unknown"));
    }

    return isJava;
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
