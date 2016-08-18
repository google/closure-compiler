/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.javascript.rhino.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/** A class to get a raw and gzip'ed size estimate; it doesn't generate code. */
final class PerformanceTrackerCodeSizeEstimator extends CodeConsumer {
  private int size = 0;
  private char lastChar = '\0';
  private final ByteArrayOutputStream output;
  private final GZIPOutputStream stream;
  private final boolean trackGzSize;

  static PerformanceTrackerCodeSizeEstimator estimate(Node jsRoot, boolean trackGzSize) {
    PerformanceTrackerCodeSizeEstimator estimator = new PerformanceTrackerCodeSizeEstimator(
        trackGzSize);
    CodeGenerator.forCostEstimation(estimator).add(jsRoot);
    return estimator;
  }

  private PerformanceTrackerCodeSizeEstimator(boolean trackGzSize) {
    this.trackGzSize = trackGzSize;
    if (trackGzSize) {
      try {
        output = new ByteArrayOutputStream();
        stream = new GZIPOutputStream(output);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      output = null;
      stream = null;
    }
  }

  @Override
  void append(String str) {
    int len = str.length();
    if (len > 0) {
      size += len;
      lastChar = str.charAt(len - 1);
      if (trackGzSize) {
        try {
          stream.write(str.getBytes(UTF_8));
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

  int getCodeSize() {
    return size;
  }

  int getZippedCodeSize() {
    if (trackGzSize) {
      try {
        stream.finish();
        stream.close();
        return output.size();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      return 0;
    }
  }
}
