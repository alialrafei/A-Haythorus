package com.acorp.jvminsight.cluster.shard;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShardMetaData implements Serializable {
  private int shardId;
  private String shardName;
}
