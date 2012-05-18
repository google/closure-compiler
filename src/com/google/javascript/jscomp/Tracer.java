/*
 * Copyright 2002 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Tracer provides a simple way to trace the handling of a request.
 *
 * By timing likely slow points in the code you can quickly pinpoint
 * why a request is slow.
 *
 * <p>Example usage:
 * <pre>
 * Tracer.initCurrentThreadTrace(); // must be called in each Thread
 * Tracer wholeRequest = new Tracer(null, "Request " + params);
 * try {
 *   ...
 *   t = new Tracer("Database", "getName()");
 *   try {
 *     name = database.getName();
 *   } finally {
 *     t.stop();
 *   }
 *   ...
 *   t = new Tracer(null, "call sendmail");
 *   try {
 *     sendMessage();
 *   } finally {
 *     t.stop();
 *   }
 *   ...
 *   t = new Tracer("Database", "updateinfo()");
 *   try {
 *     database.updateinfo("new info");
 *   } finally {
 *     t.stop();
 *   }
 *   ...
 * } finally {
 *   if (wholeRequest.stop() > 1000) {
 *     // more than a second, better log
 *     Tracer.logAndClearCurrentThreadTrace();
 *   } else {
 *     Tracer.clearCurrentThreadTrace();
 *   }
 * }
 * </pre>
 *
 * Now slow requests will produce a report like this:
 * <pre>
 *       10.452 Start        Request cmd=dostuff
 *     3 10.455 Start        [Database] getName()
 *    34 10.489 Done   34 ms [Database] getName()
 *     3 10.491 Start        call sendmail
 *  1042 11.533 Done 1042 ms call sendmail
 *     0 11.533 Start        [Database] updateinfo()
 *     3 11.536 Done    3 ms [Database] updateinfo()
 *    64 11.600 Done 1148 ms Request cmd=dostuff
 *   TOTAL Database 2 (37 ms)
 * </pre>
 *
 * If you enabled pretty-printing by calling {@link Tracer#setPrettyPrint},
 * it will print more easily readable reports that use indentation to visualize
 * the tracer hierarchy and dynamically adjusts the padding to handle large
 * durations. Like:
 * <pre>
 *       10.452 Start        Request cmd=dostuff
 *     3 10.455 Start        | [Database] getName()
 *    34 10.489 Done   34 ms | [Database] getName()
 *     3 10.491 Start        | call sendmail
 *  1042 11.533 Done 1042 ms | call sendmail
 *     0 11.533 Start        | [Database] updateinfo()
 *     3 11.536 Done    3 ms | [Database] updateinfo()
 *    64 11.600 Done 1148 ms Request cmd=dostuff
 *   TOTAL Database 2 (37 ms)
 * </pre>
 * Pretty-printing is an application global setting and should only be called
 * in the main setup of an application, not in library code.
 *
 * Now you can easily see that sendmail is causing your problems, not
 * the two database calls.
 *
 * You can easily add additional tracing statistics to your Trace output by
 * adding additional tracing statistics. Simply add to your initialization code:
 * <pre>
 *    Tracer.addTracingStatistic(myTracingStatistic)
 * </pre>
 * where myTracingStatistic implements the {@link TracingStatistic} interface.
 * The class com.google.monitoring.tracing.TracingStatistics contains
 * several useful statistics such as CPU time, wait time, and memory usage.
 * If you add your own tracing statistics, the output is not quite as pretty,
 * but includes additional useful information.

 * <p>If a Trace is given a type (the first argument to the constructor) and
 * multiple Traces are done on that type then a "TOTAL line will be
 * produced showing the total number of traces and the sum of the time
 * ("TOTAL Database 2 (37 ms)" in our example). These traces should be
 * mutually exclusive or else the sum won't make sense (the time will
 * be double counted if the second starts before the first ends).
 *
 * <p>It is also possible to have a "silent" Tracer which does not appear
 * in the trace because it was faster than the silence threshold. This
 * threshold can be set for the for the current ThreadTrace with
 * setDefaultSilenceThreshold(threshold), or on a per-Tracer basis with
 * t.stop(threshold). Silent tracers are still counted in the type
 * totals, so these events are not completely lost.
 *
 * <p><b>WARNING:</b> This code makes a big assumption that
 * everything for a given trace is done within a single thread.
 * It uses threads to identify requests. It is fine to have multiple
 * requests traced in multiple simultaneous threads but it is not ok
 * to have any given request traced in multiple threads. (the results
 * will be scattered across reports).
 *
 * Java objects do not support destructors (as in C++) so Tracer is not robust
 * when exceptions are thrown. Each Tracer object should be wrapped in a
 * try/finally block so that if an exception is thrown, the Tracer.stop()
 * method is guaranteed to be called.
 *
 * <p>A thread must call {@link Tracer#initCurrentThreadTrace()} to enable the
 * Tracer logging, otherwise Tracer does nothing.  The requirement to call
 * {@code initCurrentThreadTrace} avoids the situation where Tracer is called
 * without the explicit knowledge of the application authors because they
 * happen to use a class in another package that uses Tracer. If {@link
 * Tracer#logCurrentThreadTrace} is called without calling {@link
 * Tracer#initCurrentThreadTrace()}, then a Third Eye WARNING message is logged,
 * which should help track down the problem.
 *
 */
final class Tracer {
  // package-private for access from unit tests
  static final Logger logger =
      Logger.getLogger(Tracer.class.getName());

  /**
   * Whether pretty printing is enabled. This is intended to be set once
   * at application startup.
   */
  private static volatile boolean defaultPrettyPrint;

  /* This list is guaranteed to only increase in length.  It contains
   * a list of additional statistics that the user wants to keep track
   * of.
   */
  private static List<TracingStatistic> extraTracingStatistics =
      new CopyOnWriteArrayList<TracingStatistic>();

  /** Values returned by extraTracingStatistics */
  private long[] extraTracingValues;

  /** The type for grouping traces, may be null */
  private final @Nullable String type;

  /** A comment string for the report */
  private final String comment;

  /** Start time of the trace */
  private final long startTimeMs;

  /** Stop time of the trace, non-final */
  private long stopTimeMs;

  /**
   * Record our starter thread in order to trap Traces that are started in one
   * thread and stopped in another
   */
  final Thread startThread;

  /**
   * We limit the number of events in a Trace in order to catch memory
   * leaks (a thread that keeps logging events and never clears them).
   * This number is arbitrary and can be increased if necessary (though
   * if there are more than 1000 events then the Tracer is probably being
   * misused).
   */
  static final int MAX_TRACE_SIZE = 1000;

  /**
   * For unit testing. Can't use {@link com.google.common.time.Clock} because
   * this code is in base and has minimal dependencies.
   */
  static interface InternalClock {
    long currentTimeMillis();
  }

  /**
   * Default clock that calls through to the system clock. Can be overridden
   * in unit tests.
   */
  static InternalClock clock = new InternalClock() {
    @Override
    public long currentTimeMillis() {
      return System.currentTimeMillis();
    }
  };

  /**
   * Create and start a tracer.
   * Both type and comment may be null. See class comment for usage.
   *
   * @param type The type for totaling
   * @param comment Comment about this tracer
   */
  Tracer(@Nullable String type, @Nullable String comment) {
    this.type = type;
    this.comment = comment == null ? "" : comment;
    startTimeMs = clock.currentTimeMillis();
    startThread = Thread.currentThread();
    if (!extraTracingStatistics.isEmpty()) {
      int size = extraTracingStatistics.size();
      extraTracingValues = new long[size];
      int i = 0;
      for (TracingStatistic tracingStatistic : extraTracingStatistics) {
        extraTracingValues[i] = tracingStatistic.start(startThread);
        i++;
      }
    }

    ThreadTrace trace = getThreadTrace();

    // Do nothing if the current thread trace wasn't initialized.
    if (!trace.isInitialized()) {
      return;
    }

    // Check if we are creating too many Tracers.
    if (trace.events.size() >= MAX_TRACE_SIZE) {
      logger.log(Level.WARNING,
                  "Giant thread trace. Too many Tracers created. "
                    + "Clearing to avoid memory leak.",
                  new Throwable(trace.toString()));
      trace.truncateEvents();
    }

    // Check if we forgot to close the Tracers.
    if (trace.outstandingEvents.size() >= MAX_TRACE_SIZE) {
      logger.log(Level.WARNING,
                  "Too many outstanding Tracers. Tracer.stop() is missing "
                    + "or Tracer.stop() is not wrapped in a "
                    + "try/finally block. "
                    + "Clearing to avoid memory leak.",
                  new Throwable(trace.toString()));
      trace.truncateOutstandingEvents();
    }

    trace.startEvent(this);
  }

  /**
   * Create a tracer that isn't summed as part of a total
   *
   * @param comment  Comment about this tracer
   */
  Tracer(String comment) {
    this(null, comment);
  }

  /**
   * Construct a tracer whose type is based on the short name of the object
   * @param object   Object to use as type name
   * @param comment  A comment
   * @return  new Tracer.
   */
  static Tracer shortName(Object object, String comment) {
    if (object == null) {
      return new Tracer(comment);
    }
    return new Tracer(object.getClass().getSimpleName(), comment);
  }

  /**
   * Converts 'v' to a string and pads it with up to 16 spaces for
   * improved alignment.
   * @param v The value to convert.
   * @param digits_column_width The desired with of the string.
   */
  private static String longToPaddedString(long v, int digits_column_width) {
    int digit_width = numDigits(v);
    StringBuilder sb = new StringBuilder();
    appendSpaces(sb, digits_column_width - digit_width);
    sb.append(v);
    return sb.toString();
  }

  /**
   * Gets the number of digits in an integer when printed in base 10. Assumes
   * a positive integer.
   * @param n The value.
   * @return The number of digits in the string.
   */
  private static int numDigits(long n) {
    int i = 0;
    do {
      i++;
      n = n / 10;
    } while (n > 0);
    return i;
  }

  /**
   * Gets a string of spaces of the length specified.
   * @param sb The string builder to append to.
   * @param numSpaces The number of spaces in the string.
   */
  @VisibleForTesting
  static void appendSpaces(StringBuilder sb, int numSpaces) {
    if (numSpaces > 16) {
      logger.warning("Tracer.appendSpaces called with large numSpaces");
      // Avoid long loop in case some bug in the caller
      numSpaces = 16;
    }
    while (numSpaces >= 5) {
      sb.append("     ");
      numSpaces -= 5;
    }

    // We know it's less than 5 now
    switch (numSpaces) {
      case 1:
        sb.append(" ");
        break;
      case 2:
        sb.append("  ");
        break;
      case 3:
        sb.append("   ");
        break;
      case 4:
        sb.append("    ");
        break;
    }
  }

  /**
   * Adds a new tracing statistic to a trace
   *
   * @param tracingStatistic to enable a run
   * @return The index of this statistic (for use with stat.extraInfo()), or
   *         -1 if the statistic is not enabled.
   */
  static int addTracingStatistic(TracingStatistic tracingStatistic) {
    // Check to see if we can enable the tracing statistic before actually
    // adding it.
    if (tracingStatistic.enable()) {
      // No synchronization needed, since this is a copy-on-write array.
      extraTracingStatistics.add(tracingStatistic);
      // 99.9% of the time, this will be O(1) and return
      // extraTracingStatistics.length - 1
      return extraTracingStatistics.lastIndexOf(tracingStatistic);
    } else {
      return -1;
    }
  }

  /**
   * For testing purposes only.  These removes all current tracers.
   * Severe errors can occur if there are any active tracers going on
   * when this is called.
   *
   * The test suite uses this to remove any tracers that it has added.
   */
  @VisibleForTesting
  static void clearTracingStatisticsTestingOnly() {
    extraTracingStatistics.clear();
  }

  /**
   * Stop the trace.
   * This may only be done once and must be done from the same thread
   * that started it.
   * @param silence_threshold Traces for time less than silence_threshold
   * ms will be left out of the trace report. A value of -1 indicates
   * that the current ThreadTrace silence_threshold should be used.
   * @return The time that this trace actually ran
   */
  long stop(int silence_threshold) {
    Preconditions.checkState(Thread.currentThread() == startThread);

    ThreadTrace trace = getThreadTrace();
    // Do nothing if the thread trace was not initialized.
    if (!trace.isInitialized()) {
      return 0;
    }

    stopTimeMs = clock.currentTimeMillis();
    if (extraTracingValues != null) {
      // We use extraTracingValues.length rather than
      // extraTracingStatistics.size() because a new statistic may
      // have been added
      for (int i = 0; i < extraTracingValues.length; i++) {
        long value = extraTracingStatistics.get(i).stop(startThread);
        extraTracingValues[i] = value - extraTracingValues[i];
      }
    }

    // Do nothing if the thread trace was not initialized.
    if (!trace.isInitialized()) {
      return 0;
    }

    trace.endEvent(this, silence_threshold);
    return stopTimeMs - startTimeMs;
  }

  /** Stop the trace using the default silence_threshold
   *
   * @return  The time that this trace actually ran.
   */
  long stop() {
    return stop(-1);
  }

  @Override public String toString() {
    if (type == null) {
      return comment;
    } else {
      return "[" + type + "] " + comment;
    }
  }

  static void setDefaultSilenceThreshold(int threshold) {
    getThreadTrace().defaultSilenceThreshold = threshold;
  }

  /**
   * Initialize the trace associated with the current thread by clearing
   * out any existing trace. There shouldn't be a trace so if one is
   * found we log it as an error.
   */
  static void initCurrentThreadTrace() {
    ThreadTrace events = getThreadTrace();
    if (!events.isEmpty()) {
      logger.log(Level.WARNING,
                 "Non-empty timer log:\n" + events,
                 new Throwable());
      clearThreadTrace();

      // Grab a new thread trace if we find a previous non-empty ThreadTrace.
      events = getThreadTrace();
    }

    // Mark the thread trace as initialized.
    events.init();
  }

  static void initCurrentThreadTrace(int default_silence_threshold) {
    initCurrentThreadTrace();
    setDefaultSilenceThreshold(default_silence_threshold);
  }

  /**
   * Returns a timer report similar to the one described in the class comment.
   *
   * @return The timer report as a string
   */
  static String getCurrentThreadTraceReport() {
    return getThreadTrace().toString();
  }

  /**
   * Logs a timer report similar to the one described in the class comment.
   */
  static void logCurrentThreadTrace() {
    ThreadTrace trace = getThreadTrace();

    // New threads must call Tracer.initCurrentThreadTrace() before Tracer
    // statistics are gathered. This is a recent change (Jun 2007) that
    // prevents spurious Third Eye messages when an application uses a class in
    // a different package that happens to call Tracer without knowledge of the
    // application authors.
    if (!trace.isInitialized()) {
      logger.log(Level.WARNING,
                 "Tracer log requested for this thread but was not "
                 + "initialized using Tracer.initCurrentThreadTrace().",
                 new Throwable());
      return;
    }

    if (!trace.isEmpty()) {
      logger.log(Level.WARNING, "timers:\n{0}", getCurrentThreadTraceReport());
    }
  }

  /**
   * Throw away any Trace associated with the current thread.
   */
  static void clearCurrentThreadTrace() {
    clearThreadTrace();
  }

  /**
   * logCurrentThreadTrace() then clearCurrentThreadTrace()
   */
  static void logAndClearCurrentThreadTrace() {
    logCurrentThreadTrace();
    clearThreadTrace();
  }

  /**
   * Sets whether pretty printing is enabled. See class-level comment. This
   * only affects tracers created after this is called.
   * @param enabled Whether to enable pretty printing.
   */
  static void setPrettyPrint(boolean enabled) {
    defaultPrettyPrint = enabled;
  }

  /** Statistics for a given tracer type */
  static final class Stat {
    private int count;
    private int silent;
    private int clockTime;
    private int[] extraInfo;

    /** total count of tracers of a type, including silent
     *
     * @return total count of tracers, including silent tracers
     */
    int getCount() { return count; }

    /** total count of silent tracers of a type
     *
     * @return total count of silent tracers
     */
    int getSilentCount() { return silent; }

    /** total time spent in tracers of a type, in ms
     *
     * @return total time spent in tracer, in ms
     */
    int getTotalTime() { return clockTime; }

    /** total time spent doing additional things that we are clocking */
    @VisibleForTesting
    int getExtraInfo(int index) {
      return index >= extraInfo.length ? 0 : extraInfo[index];
    }

  }

  /**
   * This map tracks counts of tracers for each type over all time.
   */
  private static @Nullable AtomicTracerStatMap typeToCountMap;

  /**
   * This map tracks counts of silent tracers for each type over all time.
   */
  private static @Nullable AtomicTracerStatMap typeToSilentMap;

  /**
   * This map tracks time (ms) for each type over all time.
   */
  private static @Nullable AtomicTracerStatMap typeToTimeMap;

  /**
   * This method MUST be called before getTypeToCountMap (and friends)
   * will return a valid map.  This is because computing this information
   * imposes a synchronization penalty on every Tracer that is stopped.
   */
  static synchronized void enableTypeMaps() {
    if (typeToCountMap == null) {
      typeToCountMap = new AtomicTracerStatMap();
      typeToSilentMap = new AtomicTracerStatMap();
      typeToTimeMap = new AtomicTracerStatMap();
    }
  }

  /**
   * Used for exporting this data via varz.  Accesses to this
   * map must be synchronized on the map.  If enableTypeMaps has not
   * been called, this will return null.
   */
  static @Nullable Map<String, Long> getTypeToCountMap() {
    return typeToCountMap != null ? typeToCountMap.getMap() : null;
  }

  /**
   * Used for exporting this data via varz.  Accesses to this
   * map must be synchronized on the map.  If enableTypeMaps has not
   * been called, this will return null.
   */
  static @Nullable Map<String, Long> getTypeToSilentMap() {
    return typeToSilentMap != null ? typeToSilentMap.getMap() : null;
  }

  /**
   * Used for exporting this data via varz.  Accesses to this
   * map must be synchronized on the map.  If enableTypeMaps has not
   * been called, this will return null.
   */
  static @Nullable Map<String, Long> getTypeToTimeMap() {
    return typeToTimeMap != null ? typeToTimeMap.getMap() : null;
  }

  /** Gets the Stat for a tracer type; never returns null */
  static Stat getStatsForType(String type) {
    Stat stat = getThreadTrace().stats.get(type);
    return stat != null ? stat : ZERO_STAT;
  }

  private static final Stat ZERO_STAT = new Stat();

  /** Return the sec.ms part of time (if time = "20:06:11.566",  "11.566") */
  private static String formatTime(long time) {
    int sec = (int) ((time / 1000) % 60);
    int ms = (int) (time % 1000);
    return String.format("%02d.%03d", sec, ms);
  }

  /** An event is created every time a Tracer is created or stopped */
  private static final class Event {
    boolean isStart;   // else is_stop
    Tracer tracer;

    Event(boolean start, Tracer t) {
      isStart = start;
      tracer = t;
    }

    long eventTime() {
      return isStart ? tracer.startTimeMs : tracer.stopTimeMs;
    }

    /**
     * Converts the event to a formatted string.
     * @param prevEventTime The time of the previous event which appears at
     *     the left most part of the trace line.
     * @param indent The indentation to put before the tracer to show the
     *     hierarchy.
     * @param digitsColWidth How many characters the digits should use.
     * @return The formatted string.
     */
    String toString(long prevEventTime, String indent, int digitsColWidth) {
      StringBuilder sb = new StringBuilder(120);

      if (prevEventTime == -1) {
        appendSpaces(sb, digitsColWidth);
      } else {
        sb.append(longToPaddedString(
            eventTime() - prevEventTime, digitsColWidth));
      }

      sb.append(' ');
      sb.append(formatTime(eventTime()));
      if (isStart) {
        sb.append(" Start ");
        appendSpaces(sb, digitsColWidth);
        sb.append("   ");
      } else {
        sb.append(" Done ");
        long delta = tracer.stopTimeMs - tracer.startTimeMs;
        sb.append(longToPaddedString(delta, digitsColWidth));
        sb.append(" ms ");
        if (tracer.extraTracingValues != null) {
          for (int i = 0; i < tracer.extraTracingValues.length; i++) {
            delta = tracer.extraTracingValues[i];
            sb.append(String.format("%4d", delta));
            sb.append(extraTracingStatistics.get(i).getUnits());
            sb.append(";  ");
          }
        }
      }
      sb.append(indent);
      sb.append(tracer.toString());
      return sb.toString();
    }
  }

  /** Stores a thread's Trace */
  static final class ThreadTrace {

    /** Events taking less than this number of milliseconds are not reported. */
    int defaultSilenceThreshold; // non-final

    /** The Events corresponding to each startEvent/stopEvent */
    final ArrayList<Event> events = new ArrayList<Event>();

    /** Tracers that have not had their .stop() called */
    final HashSet<Tracer> outstandingEvents = new HashSet<Tracer>();

    /** Map from type to Stat object */
    final Map<String, Stat> stats = new HashMap<String, Stat>();

    /**
     * True if {@code outstandingEvents} has been cleared because we exceeded
     * the max trace limit.
     */
    boolean isOutstandingEventsTruncated = false;

    /**
     * True if {@code events} has been cleared because we exceeded the max
     * trace limit.
     */
    boolean isEventsTruncated = false;

    /**
     * Set to true if {@link Tracer#initCurrentThreadTrace()} was called by
     * the current thread.
     */
    boolean isInitialized = false;

    /**
     * Whether pretty printing is enabled for the trace.
     */
    boolean prettyPrint = false;

    /** Initialize the trace.  */
    void init() {
      isInitialized = true;
    }

    /** Is initialized? */
    boolean isInitialized() {
      return isInitialized;
    }

    /**
     * Called by the constructor {@link Tracer#Tracer(String, String)} to create
     * a start event.
     */
    void startEvent(Tracer t) {
      events.add(new Event(true, t));
      boolean notAlreadyOutstanding = outstandingEvents.add(t);
      Preconditions.checkState(notAlreadyOutstanding);
    }

    /**
     * Called by {@link Tracer#stop()} to create a stop event.
     */
    void endEvent(Tracer t, int silenceThreshold) {
      boolean wasOutstanding = outstandingEvents.remove(t);
      if (!wasOutstanding) {
        if (isOutstandingEventsTruncated) {
          // The events stack overflowed and was truncated, so just log a
          // warning. Otherwise, we get an exception which is extremely
          // confusing.
          logger.log(Level.WARNING,
                      "event not found, probably because the event stack "
                          + "overflowed and was truncated",
                      new Throwable());
        } else {
          // throw an exception if the event was not found and the events stack
          // is pristine
          throw new IllegalStateException();
        }
      }

      long elapsed = t.stopTimeMs - t.startTimeMs;

      if (silenceThreshold == -1) {   // use default
        silenceThreshold = defaultSilenceThreshold;
      }

      if (elapsed < silenceThreshold) {
        // If this one is silent then we need to remove the start Event
        boolean removed = false;
        for (int i = 0; i < events.size(); i++) {
          Event e = events.get(i);
          if (e.tracer == t) {
            Preconditions.checkState(e.isStart);
            events.remove(i);
            removed = true;
            break;
          }
        }

        // Only assert if we didn't find the original and the events
        // weren't truncated.
        Preconditions.checkState(removed || isEventsTruncated);
      } else {
        events.add(new Event(false, t));
      }

      if (t.type != null) {
        Stat stat = stats.get(t.type);
        if (stat == null) {
          stat = new Stat();
          if (!extraTracingStatistics.isEmpty()) {
            stat.extraInfo = new int[extraTracingStatistics.size()];
          }
          stats.put(t.type, stat);
        }

        stat.count++;
        if (typeToCountMap != null) {
          typeToCountMap.incrementBy(t.type, 1);
        }

        stat.clockTime += elapsed;
        if (typeToTimeMap != null) {
          typeToTimeMap.incrementBy(t.type, elapsed);
        }

        if (stat.extraInfo != null && t.extraTracingValues != null) {
          int overlapLength =
              Math.min(stat.extraInfo.length, t.extraTracingValues.length);
          for (int i = 0; i < overlapLength; i++) {
            stat.extraInfo[i] += t.extraTracingValues[i];
            AtomicTracerStatMap map =
                extraTracingStatistics.get(i).getTracingStat();
            if (map != null) {
              map.incrementBy(t.type, t.extraTracingValues[i]);
            }
          }
        }

        if (elapsed < silenceThreshold) {
          stat.silent++;
          if (typeToSilentMap != null) {
            typeToSilentMap.incrementBy(t.type, 1);
          }
        }
      }
    }

    boolean isEmpty() {
      return events.size() == 0 && outstandingEvents.size() == 0;
    }

    void truncateOutstandingEvents() {
      isOutstandingEventsTruncated = true;
      outstandingEvents.clear();
    }

    void truncateEvents() {
      isEventsTruncated = true;
      events.clear();
    }

    /** Produces the lovely Trace seen in the class comments */
    // Nullness checker does not understand that prettyPrint => indent != null
    @SuppressWarnings("nullness")
    @Override public String toString() {

      int numDigits = getMaxDigits();
      StringBuilder sb = new StringBuilder();
      long etime = -1;
      LinkedList<String> indent = prettyPrint ? new LinkedList<String>() : null;
      for (Event e : events) {
        if (prettyPrint && !e.isStart && !indent.isEmpty()) {
          indent.pop();
        }
        sb.append(" ");
        if (prettyPrint) {
          sb.append(e.toString(etime, Joiner.on("").join(indent), numDigits));
        } else {
          sb.append(e.toString(etime, "", 4));
        }
        etime = e.eventTime();
        sb.append('\n');
        if (prettyPrint && e.isStart) {
          indent.push("|  ");
        }
      }

      if (outstandingEvents.size() != 0) {
        long now = clock.currentTimeMillis();

        sb.append(" Unstopped timers:\n");
        for (Tracer t : outstandingEvents) {
          sb.append("  ").
            append(t).
            append(" (").
            append(now - t.startTimeMs).
            append(" ms, started at ").
            append(formatTime(t.startTimeMs)).
            append(")\n");
        }
      }

      for (String key : stats.keySet()) {
        Stat stat = stats.get(key);
        if (stat.count > 1) {
          sb.append(" TOTAL ").
             append(key).
             append(" ").
             append(stat.count).
             append(" (").
             append(stat.clockTime).
             append(" ms");
          if (stat.extraInfo != null) {
            for (int i = 0; i < stat.extraInfo.length; i++) {
              sb.append("; ");
              sb.append(stat.extraInfo[i]).
                 append(' ').
                 append(extraTracingStatistics.get(i).getUnits());
            }
          }
          sb.append(")\n");
        }
      }
      return sb.toString();
    }

    /**
     * Gets the maximum number of digits that can appear in the tracer output
     * in the gaps between tracers or the duration of a tracer. This is used
     * by the pretty printing case so that all of the tracers are aligned.
     */
    private int getMaxDigits() {
      long etime = -1;
      long max_time = 0;
      for (Event e : events) {
        if (etime != -1) {
          long time = e.eventTime() - etime;
          max_time = Math.max(max_time, time);
        }
        if (!e.isStart) {
          long time = e.tracer.stopTimeMs - e.tracer.startTimeMs;
          max_time = Math.max(max_time, time);
        }
        etime = e.eventTime();
      }
      // Minimum is 3 to preserve an indent even when max is small.
      return Math.max(3, numDigits(max_time));
    }
  }

  /** Holds the ThreadTrace for each thread.  */
  private static ThreadLocal<ThreadTrace> traces =
      new ThreadLocal<ThreadTrace>();

  /**
   * Get the ThreadTrace for the current thread, creating one if necessary.
   */
  static ThreadTrace getThreadTrace() {
    ThreadTrace t = traces.get();
    if (t == null) {
      t = new ThreadTrace();
      t.prettyPrint = defaultPrettyPrint;
      traces.set(t);
    }
    return t;
  }

  /** Remove any ThreadTrace associated with the current thread */
  static void clearThreadTrace() {
    traces.remove();
  }

  /**
   * A TracingStatistic allows the program to add additional optional
   * statistics to the trace output.
   *
   * The class com.google.monitoring.tracing.TracingStatistics
   * contains several useful tracing statistics
   *
   */
  static interface TracingStatistic {
    /**
     * This method is called at the start of the trace.  It should
     * return a numeric result indicating the amount of the specific
     * resource in use before the call started
     * @param thread  The current thread
     * @return A numeric value indicating the amount of the resource
     * already used.
     */
    long start(Thread thread);

    /**
     * This method is called at the end of the trace.  It should
     * return a numeric result indicating the amount of the specific
     * resource in use after the call ends. The actual reported result
     * will be the result end() - start()
     * @param thread  The current thread
     * @return A numeric value indicating the amount of the resource
     * currently used.
     */
    long stop(Thread thread);

    /**
     * Called when this tracing statistic is first enabled.  A return
     * value of True indicates that this statistic can successfully
     * run in the current JVM.
     *
     * @return An indication of whether this statistic can be
     * implemented in the current JVM.
     */
    boolean enable();

    /** Returns this tracing statistic's trace map.
     *
     * @return This tracing statistic's trace map.
     */
    AtomicTracerStatMap getTracingStat();

    /** A string that should be appended to the numeric output
     * indicating what this is.
     *
     * @return  A string indicating the units of this statistic and what it is.
     */
    String getUnits();
  }

  /**
   * This class encapsulates a map for keeping track of tracing statistics.
   * It allows the caller to atomically increment named fields.
   *
   */
  static final class AtomicTracerStatMap {
    private ConcurrentMap<String, Long> map =
        new ConcurrentHashMap<String, Long>();

    /**
     * Atomically increment the specified field by the specified amount.
     *
     * @param key      the name of the field
     * @param delta    the amount by which to increment the field
     */
    // Nullness checker is not powerful enough to prove null-safety of
    // this method
    @SuppressWarnings("nullness")
        void incrementBy(String key, long delta) {
      // We use a compareAndSet strategy to update the map, which is much
      // faster when there isn't too much contention.  Look at a value, and
      // conditionally update the map if the value hasn't changed.
      // If it has changed, repeat.
      Long oldValue = map.get(key);
      if (oldValue == null) {
        // Currently, the slot is empty
        oldValue = map.putIfAbsent(key, delta);
        if (oldValue == null) {
          // The slot was still empty when we set it
          return;
        } else {
          // Someone filled in the slot behind our back.  oldValue has
          // its current value
        }
      }
      while (true) {
        if (map.replace(key, oldValue, oldValue + delta)) {
          break;
        }
        // Nullness checker doesn't understand that this cannot return null.
        oldValue = map.get(key);
      }
    }

    /**
     * Returns a map of key:value pairs.
     */
    Map<String, Long> getMap() {
      return map;
    }
  }
}
