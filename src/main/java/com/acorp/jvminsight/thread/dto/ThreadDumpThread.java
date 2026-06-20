package com.acorp.jvminsight.thread.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ThreadDumpThread {

  private String threadName;

  private long threadId;

  private int priority;

  private boolean daemon;

  private boolean inNative;

  private Thread.State state;

  private String lockName;

  private Long lockOwnerId;

  private String lockOwnerName;

  private List<String> stackTrace = new ArrayList<>();
}
