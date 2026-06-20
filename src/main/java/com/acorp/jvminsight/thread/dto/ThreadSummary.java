package com.acorp.jvminsight.thread.dto;

import lombok.Data;

@Data
public class ThreadSummary {

  private int runnable;

  private int waiting;

  private int timedWaiting;

  private int blocked;

  private int terminated;

  private int unknown;
}
