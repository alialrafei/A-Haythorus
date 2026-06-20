package com.acorp.jvminsight.thread.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ThreadDumpSnapshot {

  private List<ThreadDumpThread> threads = new ArrayList<>();

  private ThreadSummary summary = new ThreadSummary();
}
