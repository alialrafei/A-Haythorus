package com.acorp.jvminsight.snapshotcollection.service.delta.strategy;

import com.acorp.jvminsight.snapshotcollection.dto.JvmSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.IoDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.dto.delta.JvmDeltaSnapshot;
import com.acorp.jvminsight.snapshotcollection.service.delta.DeltaComputationStrategy;
import java.time.Duration;

/** Computes Linux per-process I/O movement and throughput between consecutive snapshots. */
public final class IoDeltaStrategy implements DeltaComputationStrategy {

  @Override
  public void compute(JvmSnapshot previous, JvmSnapshot current, JvmDeltaSnapshot delta) {
    if (previous.getProcessIo() == null || current.getProcessIo() == null) {
      return;
    }

    long readBytesDelta =
        Math.max(0L, current.getProcessIo().readBytes() - previous.getProcessIo().readBytes());
    long writeBytesDelta =
        Math.max(0L, current.getProcessIo().writeBytes() - previous.getProcessIo().writeBytes());

    long readCharactersDelta =
        Math.max(
            0L,
            current.getProcessIo().readCharacters() - previous.getProcessIo().readCharacters());
    long writeCharactersDelta =
        Math.max(
            0L,
            current.getProcessIo().writeCharacters() - previous.getProcessIo().writeCharacters());
    long readSyscallsDelta =
        Math.max(0L, current.getProcessIo().readSyscalls() - previous.getProcessIo().readSyscalls());
    long writeSyscallsDelta =
        Math.max(
            0L, current.getProcessIo().writeSyscalls() - previous.getProcessIo().writeSyscalls());

    double intervalSeconds = 0.0;
    if (previous.getTimestamp() != null && current.getTimestamp() != null) {
      intervalSeconds =
          Duration.between(previous.getTimestamp(), current.getTimestamp()).toNanos()
              / 1_000_000_000.0;
    }

    IoDeltaSnapshot io = new IoDeltaSnapshot();
    io.setReadBytesDelta(readBytesDelta);
    io.setWriteBytesDelta(writeBytesDelta);
    io.setReadCharactersDelta(readCharactersDelta);
    io.setWriteCharactersDelta(writeCharactersDelta);
    io.setReadSyscallsDelta(readSyscallsDelta);
    io.setWriteSyscallsDelta(writeSyscallsDelta);
    io.setReadBytesPerSecond(intervalSeconds <= 0 ? 0.0 : readBytesDelta / intervalSeconds);
    io.setWriteBytesPerSecond(intervalSeconds <= 0 ? 0.0 : writeBytesDelta / intervalSeconds);

    delta.setIoDelta(io);
  }
}
