/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CodeChangeHandler.RecentChange;
import com.google.javascript.rhino.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 */
public class PerformanceTracker {

  private final Node jsRoot;
  private final boolean trackGzippedSize;

  // Keeps track of AST changes and computes code size estimation
  // if there is any.
  private final RecentChange codeChange = new RecentChange();

  private int curCodeSizeEstimate = -1;
  private int curZippedCodeSizeEstimate = -1;

  private Deque<String> currentRunningPass = new ArrayDeque<String>();

  /** Maps pass name to total time spend. */
  private final Map<String, Long> runtimeRecord = Maps.newHashMap();

  /** Maps pass name to total size reduction. */
  private final Map<String, Integer> codeSizeRecord = Maps.newHashMap();

  /** Maps pass name to total size reduction. */
  private final Map<String, Integer> zippedCodeSizeRecord = Maps.newHashMap();

  PerformanceTracker(Node jsRoot, boolean trackGzippedSize) {
    this.jsRoot = jsRoot;
    this.trackGzippedSize = trackGzippedSize;
  }

  CodeChangeHandler getCodeChangeHandler() {
    return codeChange;
  }

  void recordPassStart(String passName) {
    currentRunningPass.push(passName);
    codeChange.reset();
  }

  /**
   * Record that a pass has stopped.
   *
   * @param passName Short name of the pass.
   * @param result Execution time.
   */
  void recordPassStop(String passName, long result) {
    String currentPassName = currentRunningPass.pop();
    if (!passName.equals(currentPassName)) {
      throw new RuntimeException(passName + " is not running.");
    }
    Long total = runtimeRecord.get(passName);
    if (total == null) {
      total = 0L;
    }
    total = total.longValue() + result;
    runtimeRecord.put(passName, total);

    if (codeChange.hasCodeChanged()) {
      CodeSizeEstimatePrinter printer = estimateCodeSize(jsRoot);
      curCodeSizeEstimate = recordSizeChange(curCodeSizeEstimate,
          printer.calcSize(), passName, codeSizeRecord);
      curZippedCodeSizeEstimate = recordSizeChange(curZippedCodeSizeEstimate,
          printer.calcZippedSize(), passName, zippedCodeSizeRecord);
    }
  }

  /**
   * Record the size change in the given record for that given pass.
   *
   * @return The new estimated size.
   */
  private static int recordSizeChange(int oldSize, int newSize, String passName,
      Map<String, Integer> record) {
    if (oldSize != -1) {
      int delta = oldSize - newSize;
      Integer reduction = record.get(passName);
      if (delta > 0) {
        if (reduction == null) {
          reduction = delta;
        } else {
          reduction = reduction + delta;
        }
        record.put(passName, reduction);
      }
    }
    return newSize;
  }


  public ImmutableMap<String, Long> getRuntimeRecord() {
    return ImmutableMap.copyOf(runtimeRecord);
  }

  public ImmutableMap<String, Integer> getCodeSizeRecord() {
    return ImmutableMap.copyOf(codeSizeRecord);
  }

  public ImmutableMap<String, Integer> getZippedCodeSizeRecord() {
    return ImmutableMap.copyOf(zippedCodeSizeRecord);
  }

  private final CodeSizeEstimatePrinter estimateCodeSize(Node root) {
    CodeSizeEstimatePrinter cp = new CodeSizeEstimatePrinter(trackGzippedSize);
    CodeGenerator cg = new CodeGenerator(cp);
    cg.add(root);
    return cp;
  }

  /**
   * Purely use to get a code size estimate and not generate any code at all.
   */
  private static final class CodeSizeEstimatePrinter extends CodeConsumer {
    private final boolean trackGzippedSize;
    private int size = 0;
    private char lastChar = '\0';
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final GZIPOutputStream stream;

    private CodeSizeEstimatePrinter(boolean trackGzippedSize) {
      this.trackGzippedSize = trackGzippedSize;

      try {
        stream = new GZIPOutputStream(output);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    void append(String str) {
      int len = str.length();
      if (len > 0) {
        size += len;
        lastChar = str.charAt(len - 1);
        if (trackGzippedSize) {
          try {
            stream.write(str.getBytes());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }

    @Override
    char getLastChar() {
      return lastChar;
    }

    private int calcSize() {
      return size;
    }

    private int calcZippedSize() {
      if (trackGzippedSize) {
        try {
          stream.finish();
          stream.flush();
          stream.close();
          return output.size();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        return -1;
      }
    }
  }
}
