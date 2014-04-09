/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser.util;

import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

import java.io.PrintStream;
import java.util.HashMap;

// TODO: stack timers to get inclusive/exclusive times
public class Timer {

  private final String name;
  private final long start;

  public Timer(String name) {
    this.name = name;
    this.start = getCurrentTime();
  }

  public void end() {
    logTime(this.name, getCurrentTime() - this.start);
  }

  private static long getCurrentTime() {
    return System.currentTimeMillis();
  }

  public static class Entry {
    public final String name;
    public int count;
    public long elapsedTime;

    public Entry(String name) {
      this.name = name;
    }
  }

  private static final HashMap<String, Entry> entries = new HashMap<>();

  public static void logTime(String name, long elapsedTime) {
    Entry entry = getEntry(name);
    entry.count++;
    entry.elapsedTime += elapsedTime;
  }

  private static Entry getEntry(String name) {
    Entry entry;
    if (!entries.containsKey(name)) {
      entry = new Entry(name);
      entries.put(name, entry);
    } else {
      entry = entries.get(name);
    }
    return entry;
  }

  public static Iterable<Entry> getEntries() {
    return entries.values();
  }

  public static void clearEntries() {
    entries.clear();
  }

  public static void dumpEntries(PrintStream out) {
    for (Entry entry : getEntries()) {
      out.println(SimpleFormat.format(
          "Time '%s'(%d): %fms",
          entry.name, entry.count, entry.elapsedTime / 1000.0));
    }
    clearEntries();
  }
}
