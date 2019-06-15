/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.diagnostic;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * A simple interface for writing to a human readable log file.
 *
 * <p>This API is designed to be compatible with J2CL. In the future it may be worth implementing
 * this in terms of <a href="https://github.com/google/flogger">Flogger</a>; however, at the time of
 * writing, Flogger was not J2CL compatible.
 */
public abstract class LogFile implements AutoCloseable {

  @MustBeClosed
  public static LogFile createOrReopen(Path file) {
    return WritingLogFile.create(file);
  }

  public static LogFile createNoOp() {
    return new NoOpLogFile();
  }

  // All subclasses are package classes.
  LogFile() {}

  public abstract LogFile log(Object value);

  public abstract LogFile log(String value); // Provides a disambiguating overload.

  public abstract LogFile log(Supplier<String> value);

  @FormatMethod
  public abstract LogFile log(@FormatString String template, Object... values);

  @Override
  public abstract void close();
}
