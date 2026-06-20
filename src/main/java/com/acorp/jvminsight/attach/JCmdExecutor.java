package com.acorp.jvminsight.attach;

import com.sun.tools.attach.VirtualMachine;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public final class JCmdExecutor {

  private JCmdExecutor() {}

  public static String execute(VirtualMachine vm, String command) {
    try {
      Method m = findExecuteJCmdMethod(vm.getClass());

      String[] args = command.split("\\s+");

      try (InputStream is = (InputStream) m.invoke(vm, (Object) args)) {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }

    } catch (Exception e) {
      throw new IllegalStateException("Failed to execute jcmd: " + command, e);
    }
  }

  /** Walks class hierarchy to find executeJCmd(String[]) */
  private static Method findExecuteJCmdMethod(Class<?> clazz) throws NoSuchMethodException {

    Class<?> current = clazz;

    while (current != null) {
      try {
        Method m = current.getDeclaredMethod("executeJCmd", String[].class);
        m.setAccessible(true);
        return m;
      } catch (NoSuchMethodException ignored) {
        current = current.getSuperclass();
      }
    }

    throw new NoSuchMethodException("executeJCmd(String[]) not found in class hierarchy");
  }
}
