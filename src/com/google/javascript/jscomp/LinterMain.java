/*
 * Copyright 2015 The Closure Compiler Authors.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Minimal binary that just runs the "lint" checks which can be run on a single file at a time. This
 * means some checks in the lintChecks DiagnosticGroup are skipped, since they depend on type
 * information.
 */
@GwtIncompatible("Unnecessary")
public final class LinterMain {
  @Option(name = "--fix", usage = "Fix lint warnings automatically")
  private boolean fix = false;

  @Argument private List<String> files = new ArrayList<>();

  public static void main(String[] args) throws IOException, CmdLineException {
    new LinterMain().run(args);
  }

  private void run(String[] args) throws IOException, CmdLineException {
    CmdLineParser parser = new CmdLineParser(this);
    parser.parseArgument(args);

    for (String filename : files) {
      if (fix) {
        Linter.fixRepeatedly(filename);
      } else {
        Linter.lint(filename);
      }
    }
  }
}
