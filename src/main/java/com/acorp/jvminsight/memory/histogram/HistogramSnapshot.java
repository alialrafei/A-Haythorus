package com.acorp.jvminsight.memory.histogram;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistogramSnapshot {

  /** Largest classes regardless of ownership. */
  private List<ClassHistogramEntry> topOverall;

  /** Classes belonging to configured application packages. */
  private List<ClassHistogramEntry> topApplication;

  /** Framework/library classes. */
  private List<ClassHistogramEntry> topFramework;

  /** Java/JDK runtime classes. */
  private List<ClassHistogramEntry> topJdk;

  /** JVM array classes such as byte[], char[], Object[]. */
  private List<ClassHistogramEntry> topArrays;

  /** Classes that could not be classified confidently. */
  private List<ClassHistogramEntry> topUnknown;
}
