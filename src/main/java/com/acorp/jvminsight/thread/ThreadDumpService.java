package com.acorp.jvminsight.thread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import javax.management.MBeanServerConnection;

public final class ThreadDumpService {

  private ThreadDumpService() {}

  public static String dumpAllThreads(MBeanServerConnection mbeanServer) throws Exception {

    ThreadMXBean threadBean =
        ManagementFactory.newPlatformMXBeanProxy(
            mbeanServer, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);

    ThreadInfo[] threads = threadBean.dumpAllThreads(true, true);

    StringBuilder sb = new StringBuilder();

    for (ThreadInfo ti : threads) {
      sb.append(ti.toString()).append("\n");
    }

    return sb.toString();
  }

  public static long[] findDeadlockedThreads(MBeanServerConnection mbeanServer) throws Exception {

    ThreadMXBean threadBean =
        ManagementFactory.newPlatformMXBeanProxy(
            mbeanServer, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);

    return threadBean.findDeadlockedThreads();
  }
}
