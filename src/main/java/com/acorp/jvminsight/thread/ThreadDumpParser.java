package com.acorp.jvminsight.thread;

import com.acorp.jvminsight.thread.dto.ThreadDumpSnapshot;
import com.acorp.jvminsight.thread.dto.ThreadDumpThread;
import com.acorp.jvminsight.thread.dto.ThreadSummary;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ThreadDumpParser {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadDumpParser.class);

  private static final Pattern HEADER_PATTERN =
      Pattern.compile("^\"([^\"]+)\"( daemon)? prio=(\\d+) Id=(\\d+) ([A-Z_]+).*");

  private ThreadDumpParser() {}

  public static ThreadDumpSnapshot parse(String dump) {

    LOGGER.debug("Starting thread dump parsing.");

    ThreadDumpSnapshot snapshot = new ThreadDumpSnapshot();

    if (dump == null || dump.isBlank()) {

      LOGGER.debug("Thread dump is empty.");

      return snapshot;
    }

    ThreadDumpThread current = null;

    try {

      for (String line : dump.split("\n")) {

        Matcher matcher = HEADER_PATTERN.matcher(line);

        if (matcher.matches()) {

          current = new ThreadDumpThread();

          current.setThreadName(matcher.group(1));

          current.setDaemon(matcher.group(2) != null);

          current.setPriority(Integer.parseInt(matcher.group(3)));

          current.setThreadId(Long.parseLong(matcher.group(4)));

          Thread.State state;

          try {

            state = Thread.State.valueOf(matcher.group(5));

          } catch (IllegalArgumentException ex) {

            LOGGER.warn(
                "Unknown thread state '{}' for thread '{}'.", matcher.group(5), matcher.group(1));

            state = Thread.State.RUNNABLE;
          }

          current.setState(state);

          snapshot.getThreads().add(current);

          incrementState(snapshot.getSummary(), state);

          LOGGER.trace(
              "Parsed thread name='{}' id={} state={}",
              current.getThreadName(),
              current.getThreadId(),
              current.getState());

          continue;
        }

        if (current == null) {
          continue;
        }

        if (line.startsWith("\tat ")) {

          current.getStackTrace().add(line.trim());

        } else if (line.contains("blocked on")) {

          String lockName = extractAfter(line, "blocked on");

          current.setLockName(lockName);

          LOGGER.trace("Thread '{}' blocked on {}", current.getThreadName(), lockName);

        } else if (line.contains("waiting on")) {

          String lockName = extractAfter(line, "waiting on");

          current.setLockName(lockName);

          LOGGER.trace("Thread '{}' waiting on {}", current.getThreadName(), lockName);
        }
      }

    } catch (Exception ex) {

      LOGGER.error("Failed while parsing thread dump.", ex);

      throw ex;
    }

    LOGGER.debug(
        "Thread dump parsed successfully. threads={} runnable={} blocked={} waiting={} timedWaiting={}",
        snapshot.getThreads().size(),
        snapshot.getSummary().getRunnable(),
        snapshot.getSummary().getBlocked(),
        snapshot.getSummary().getWaiting(),
        snapshot.getSummary().getTimedWaiting());

    return snapshot;
  }

  private static void incrementState(ThreadSummary summary, Thread.State state) {

    switch (state) {
      case RUNNABLE -> summary.setRunnable(summary.getRunnable() + 1);

      case BLOCKED -> summary.setBlocked(summary.getBlocked() + 1);

      case WAITING -> summary.setWaiting(summary.getWaiting() + 1);

      case TIMED_WAITING -> summary.setTimedWaiting(summary.getTimedWaiting() + 1);

      case TERMINATED -> summary.setTerminated(summary.getTerminated() + 1);

      default -> summary.setUnknown(summary.getUnknown() + 1);
    }
  }

  private static String extractAfter(String line, String token) {

    int index = line.indexOf(token);

    if (index == -1) {

      LOGGER.trace("Token '{}' not found in line '{}'", token, line);

      return null;
    }

    return line.substring(index + token.length()).trim();
  }
}
