package com.acorp.jvminsight.container.dto;

public class PodInfo {
  public String name;
  public String namespace;
  public String node;
  public String app;

  public PodInfo() {}

  public void setName(String name) {
    this.name = name;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setNode(String node) {
    this.node = node;
  }

  public void setApp(String app) {
    this.app = app;
  }

  public String getName() {
    return name;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getNode() {
    return node;
  }

  public String getApp() {
    return app;
  }
}
