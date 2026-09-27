package com.acorp.jvminsight.notification.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SavedEvidence implements Serializable {
  private String notificationId;
  private Instant timestamp;
  private JsonNode payload;
}
