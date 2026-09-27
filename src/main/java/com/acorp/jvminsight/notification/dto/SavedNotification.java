package com.acorp.jvminsight.notification.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SavedNotification implements Serializable {
  private String id;
  private String message;
  private List<Instant> instances;
}
