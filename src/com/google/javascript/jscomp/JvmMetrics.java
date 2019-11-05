/*
 * Copyright 2012 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.annotations.GwtIncompatible;
import java.io.PrintStream;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * A class to report jvm/jmx statistics.
 * Borrowed from:
 * http://code.google.com/p/dart/source/browse/trunk/dart/compiler/java/com/google/dart/compiler/metrics/JvmMetrics.java
 */
@GwtIncompatible("Unneccesary")
class JvmMetrics {

  private static final int TABULAR_COLON_POS = 40;
  private static final long ONE_KILO_BYTE = 1L << 10L;
  private static final long ONE_MEGA_BYTE = 1L << 20L;
  private static final long ONE_GIGA_BYTE = 1L << 30L;

  public static void maybeWriteJvmMetrics(PrintStream out, String options) {
    if (options == null) {
      return;
    }

    boolean verboseMode = false;
    boolean prettyMode = false;
    StringTokenizer st = new StringTokenizer(options, ":");
    // options are grouped in order 'detail:format:types'
    if (st.hasMoreTokens()) {
      String mode = st.nextToken();
      if (mode.equalsIgnoreCase("verbose")) {
        verboseMode = true;
      }
    }

    if (st.hasMoreTokens()) {
      String mode = st.nextToken();
      if (mode.equalsIgnoreCase("pretty")) {
        prettyMode = true;
      }
    }

    if (st.hasMoreTokens()) {
      while (st.hasMoreTokens()) {
        String types = st.nextToken();
        StringTokenizer typeSt = new StringTokenizer(types, ",");
        while (typeSt.hasMoreElements()) {
          String type = typeSt.nextToken();
          writeMetrics(out, type, verboseMode, prettyMode);
        }
      }
    } else {
      // the default
      writeMetrics(out, "all", verboseMode, prettyMode);
    }
  }

  private static void writeMetrics(
      PrintStream out, String type, boolean verbose, boolean pretty) {

    if (type.equals("gc") || type.equalsIgnoreCase("all")) {
      writeGarbageCollectionStats(out, verbose, pretty);
    }
    if (type.equals("mem") || type.equalsIgnoreCase("all")) {
      writeMemoryMetrics(out, verbose, pretty);
    }
    if (type.equals("jit") || type.equalsIgnoreCase("all")) {
      writeJitMetrics(out, verbose, pretty);
    }
  }

  private static void writeJitMetrics(
      PrintStream out, boolean verbose, boolean pretty) {

    CompilationMXBean cBean = ManagementFactory.getCompilationMXBean();

    String name;
    if (verbose) {
      name = cBean.getName();
    } else {
      name = "total";
    }

    if (pretty) {
      out.println("\nJIT Stats");
      out.printf("\t%s jit time: %d ms%n", name, cBean.getTotalCompilationTime());
    } else {
      out.println(normalizeTabularColonPos(String.format("%s-jit-time-ms : %d",
          normalizeName(name), cBean.getTotalCompilationTime())));
    }
  }

  private static void writeOverallMemoryUsage(
      PrintStream out, MemoryUsage usage, String prefix, boolean pretty) {
    if (pretty) {
      out.format("\t%s\n", prefix);
      out.format("\t\tavailable         : %s\n", formatBytes(usage.getMax()));
      out.format("\t\tcurrent           : %s\n", formatBytes(usage.getUsed()));
    } else {
      prefix = normalizeName(prefix);
      out.println(normalizeTabularColonPos(
          String.format("%s-available-bytes : %d", prefix, usage.getMax())));
      out.println(normalizeTabularColonPos(
          String.format("%s-current-bytes : %d", prefix, usage.getUsed())));
    }
  }

  private static void writePoolMemoryUsage(PrintStream out, MemoryUsage usage,
      MemoryUsage peakUsage, String prefix, boolean pretty) {
    if (pretty) {
      out.format("\t\tavailable         : %s\n",
          formatBytes(usage.getMax()));
      out.format("\t\tpeak              : %s\n",
          formatBytes(peakUsage.getUsed()));
      out.format("\t\tcurrent           : %s\n",
          formatBytes(usage.getUsed()));
    } else {
      out.println(normalizeTabularColonPos(
          String.format("%s-available-bytes : %d", prefix, usage.getMax())));
      out.println(normalizeTabularColonPos(
          String.format("%s-peak-bytes : %d", prefix, peakUsage.getUsed())));
      out.println(normalizeTabularColonPos(
          String.format("%s-current-bytes : %d", prefix, usage.getUsed())));
    }
  }

  private static void writeMemoryMetrics(
      PrintStream out, boolean verbose, boolean pretty) {
    if (pretty) {
      out.println("\nMemory usage");
    }

    // only show overall stats in verbose mode
    if (verbose) {
      MemoryMXBean overallMemBean = ManagementFactory.getMemoryMXBean();
      MemoryUsage usage = overallMemBean.getHeapMemoryUsage();
      writeOverallMemoryUsage(out, usage, "Heap", pretty);

      usage = overallMemBean.getNonHeapMemoryUsage();
      writeOverallMemoryUsage(out, usage, "Non-heap", pretty);
    }

    if (verbose) {
      List<MemoryPoolMXBean> mpBeans = ManagementFactory.getMemoryPoolMXBeans();
      for (MemoryPoolMXBean mpBean : mpBeans) {
        MemoryUsage currentUsage = mpBean.getUsage();
        MemoryUsage peakUsage = mpBean.getPeakUsage();
        if (pretty) {
          out.println("\tPool " + mpBean.getName());
          writePoolMemoryUsage(out, currentUsage, peakUsage, null, true);
        } else {
          writePoolMemoryUsage(out, currentUsage, peakUsage,
              "mem-pool-" + normalizeName(mpBean.getName()), false);
        }
      }
    } else {
      long available = 0;
      long current = 0;
      long peak = 0;
      List<MemoryPoolMXBean> mpBeans = ManagementFactory.getMemoryPoolMXBeans();
      for (MemoryPoolMXBean mpBean : mpBeans) {
        MemoryUsage currentUsage = mpBean.getUsage();
        available += currentUsage.getMax();
        current += currentUsage.getUsed();
        MemoryUsage peakUsage = mpBean.getPeakUsage();
        peak += peakUsage.getUsed();
      }
      MemoryUsage summaryUsage = new MemoryUsage(
          0, current, current, available);
      MemoryUsage summaryPeakUsage = new MemoryUsage(0, peak, peak, peak);
      if (pretty) {
        out.format("\tAggregate of %d memory pools\n", mpBeans.size());
        writePoolMemoryUsage(out, summaryUsage, summaryPeakUsage, null, true);
      } else {
        writePoolMemoryUsage(out, summaryUsage, summaryPeakUsage, "mem", false);
      }
    }
  }

  private static void writeGarbageCollectionStats(
      PrintStream out, boolean verbose, boolean pretty) {
    List<GarbageCollectorMXBean> gcBeans =
        ManagementFactory.getGarbageCollectorMXBeans();

    if (verbose) {
      if (pretty) {
        out.println("\nGarbage collection stats");
        for (GarbageCollectorMXBean gcBean : gcBeans) {
          out.println("\tCollector " + gcBean.getName());
          out.format(
              "\t\tcollection count   : %d\n", gcBean.getCollectionCount());
          out.format(
              "\t\tcollection time    : %d ms\n", gcBean.getCollectionTime());
        }
      } else {
        for (GarbageCollectorMXBean gcBean : gcBeans) {
          String name = normalizeName(gcBean.getName());
          out.println(normalizeTabularColonPos(String.format("gc-" + name
              + "-collection-count : %d", gcBean.getCollectionCount())));
          out.println(normalizeTabularColonPos(String.format("gc-" + name
              + "-collection-time-ms : %d", gcBean.getCollectionTime())));
        }
      }
    } else {
      long collectionCount = 0;
      long collectionTime = 0;
      int collectorCount = gcBeans.size();
      for (GarbageCollectorMXBean gcBean : gcBeans) {
        collectionCount += gcBean.getCollectionCount();
        collectionTime += gcBean.getCollectionTime();
      }
      if (pretty) {
        out.println("\nGarbage collection stats");
        out.format("\tAggregate of %d collectors\n", collectorCount);
        out.format("\t\tcollection count   : %d\n", collectionCount);
        out.format("\t\tcollection time    : %d ms\n", collectionTime);
      } else {
        String name = normalizeName("aggregate");
        out.println(normalizeTabularColonPos(
            String.format("gc-%s-collection-count : %d", name,
            collectionCount)));
        out.println(normalizeTabularColonPos(
            String.format("gc-%s-collection-time-ms : %d", name,
            collectionTime)));
      }
    }
  }

  private static String normalizeName(String name) {
    return name.replace(' ', '_').toLowerCase(Locale.ROOT);
  }

  private static String normalizeTabularColonPos(String string) {
    StringBuilder sb = new StringBuilder(string);
    int index = sb.indexOf(":");
    for (; index < TABULAR_COLON_POS; ++index) {
      sb.insert(index, ' ');
    }
    return sb.toString();
  }

  private static String formatBytes(long numBytes) {
    if (numBytes < ONE_KILO_BYTE) {
      return String.format("%d B", numBytes);
    } else if (numBytes < ONE_MEGA_BYTE) {
      return String.format("%d KB", numBytes / ONE_KILO_BYTE);
    } else if (numBytes < ONE_GIGA_BYTE) {
      return String.format("%d MB", numBytes / ONE_MEGA_BYTE);
    } else {
      return String.format("%d GB", numBytes / ONE_GIGA_BYTE);
    }
  }
}
