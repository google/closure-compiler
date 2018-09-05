/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.SortingErrorManager.ErrorReportGenerator;
import java.io.PrintStream;

/** Fail early if someone tries to use the JSON otuput mode */
public class JsonErrorReportGenerator implements ErrorReportGenerator {
  public JsonErrorReportGenerator(PrintStream stream, SourceExcerptProvider sourceExcerptProvider) {
    throw new UnsupportedOperationException("JSON printing not (yet) supported in JS version");
  }

  @Override
  public void generateReport(SortingErrorManager manager) {}
}
