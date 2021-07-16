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

import java.util.function.Supplier;

/** An implementation that does nothing to allow logging calls to be cheap when disabled. */
final class NoOpLogFile extends LogFile {

  @Override
  public LogFile log(Object value) {
    return this;
  }

  @Override
  public LogFile log(String value) {
    return this;
  }

  @Override
  public LogFile log(Supplier<String> value) {
    return this;
  }

  @Override
  public LogFile log(String template, Object... values) {
    return this;
  }

  @Override
  public LogFile logJson(Object value) {
    return this;
  }

  @Override
  public LogFile logJson(Supplier<Object> value) {
    return this;
  }

  @Override
  public void close() {}
}
