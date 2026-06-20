package com.acorp.jvminsight.attach;

import com.sun.tools.attach.VirtualMachine;
import java.util.Properties;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public final class JvmAttachClient {

  private JvmAttachClient() {}

  public static MBeanServerConnection attachAndGetMBeanServer(long pid) {
    VirtualMachine vm = null;
    try {
      vm = VirtualMachine.attach(String.valueOf(pid));

      Properties props = vm.getAgentProperties();
      String connectorAddress =
          props.getProperty("com.sun.management.jmxremote.localConnectorAddress");

      if (connectorAddress == null) {
        vm.startLocalManagementAgent();

        props = vm.getAgentProperties();
        connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
      }

      JMXServiceURL url = new JMXServiceURL(connectorAddress);
      JMXConnector connector = JMXConnectorFactory.connect(url);

      return connector.getMBeanServerConnection();

    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (vm != null) {
        try {
          vm.detach();
        } catch (Exception ignored) {
        }
      }
    }
  }
}
