package com.acorp.jvminsight.util;

/**
 * Utility class for computing percentage growth between two values.
 *
 * <p>Examples:
 *
 * <pre>
 * previous = 100
 * current  = 150
 * result   = 50.0
 *
 * previous = 100
 * current  = 80
 * result   = 0.0
 *
 * previous = 0
 * current  = 20
 * result   = 100.0
 * </pre>
 *
 * <p>Negative growth is treated as zero since we are interested in growth rather than shrinkage for
 * leak detection purposes.
 */
public final class GrowthCalculator {

  private GrowthCalculator() {}

  /**
   * Computes percentage growth between two values.
   *
   * @param previous previous value
   * @param current current value
   * @return growth percentage in the range [0, +∞)
   */
  public static double percentageGrowth(long previous, long current) {

    if (previous <= 0) {
      return current > 0 ? 100.0 : 0.0;
    }

    long delta = current - previous;

    if (delta <= 0) {
      return 0.0;
    }

    return ((double) delta / previous) * 100.0;
  }

  /**
   * Computes percentage growth and caps the result at 100.
   *
   * <p>Useful for leak scoring.
   *
   * @param previous previous value
   * @param current current value
   * @return growth percentage in the range [0,100]
   */
  public static double percentageGrowthCapped(long previous, long current) {

    return Math.min(percentageGrowth(previous, current), 100.0);
  }

  /**
   * Computes the absolute delta.
   *
   * @param previous previous value
   * @param current current value
   * @return current - previous
   */
  public static long delta(long previous, long current) {

    return current - previous;
  }
}
