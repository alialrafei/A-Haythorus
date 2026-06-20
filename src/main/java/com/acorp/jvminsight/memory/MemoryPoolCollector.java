package com.acorp.jvminsight.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;
import javax.management.MBeanServerConnection;

public final class MemoryPoolCollector {

  private MemoryPoolCollector() {}

  public static List<MemoryPoolSnapshot> collect(MBeanServerConnection mbeanServer)
      throws Exception {

    List<MemoryPoolMXBean> pools =
        ManagementFactory.getPlatformMXBeans(mbeanServer, MemoryPoolMXBean.class);

    List<MemoryPoolSnapshot> result = new ArrayList<>();

    for (MemoryPoolMXBean pool : pools) {
      if (pool.getUsage() != null) {
        result.add(
            new MemoryPoolSnapshot(
                pool.getName(),
                pool.getUsage().getUsed(),
                pool.getUsage().getCommitted(),
                pool.getUsage().getMax()));
      }
    }

    return result;
  }
}
