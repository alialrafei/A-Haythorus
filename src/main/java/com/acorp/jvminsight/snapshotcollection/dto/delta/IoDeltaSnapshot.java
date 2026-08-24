package com.acorp.jvminsight.snapshotcollection.dto.delta;

import lombok.Data;

@Data
public class IoDeltaSnapshot {
  private long readBytesDelta;
  private long writeBytesDelta;
  private long readCharactersDelta;
  private long writeCharactersDelta;
  private long readSyscallsDelta;
  private long writeSyscallsDelta;
  private double readBytesPerSecond;
  private double writeBytesPerSecond;
}
