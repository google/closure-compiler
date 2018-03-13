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

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Requirement.Severity;
import com.google.javascript.rhino.Node;

/** Creates or updates conformance whitelist entries. */
@GwtIncompatible("Conformance")
public class ConformanceWhitelister {
  private ConformanceWhitelister() {}

  public static ImmutableSet<String> getViolatingPaths(
      AbstractCompiler compiler, Node externs, Node ast, Requirement requirement) {
    ConformanceViolationRecordingCompiler recordingCompiler =
        new ConformanceViolationRecordingCompiler(compiler);
    // Remove existing prefix whitelist entries, but keep regexps (which we don't re-add either).
    // TODO(bangert): Check that each regex matches one entry?
    Requirement cleanedRequirement =
        requirement
            .toBuilder()
            .clearWhitelist()
            .setSeverity(Severity.ERROR)
            .build(); // So we only have one type of error.

    ConformanceConfig cleanedConfig =
        ConformanceConfig.newBuilder().addRequirement(cleanedRequirement).build();
    CheckConformance check =
        new CheckConformance(recordingCompiler, ImmutableList.of(cleanedConfig));
    check.process(externs, ast);

    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (JSError e : recordingCompiler.getConformanceErrors()) {
      result.add(e.sourceName);
    }
    return result.build();
  }

  private static class ConformanceViolationRecordingCompiler extends ForwardingCompiler {
    private final ImmutableList.Builder<JSError> conformanceErrors;

    private ConformanceViolationRecordingCompiler(AbstractCompiler abstractCompiler) {
      super(abstractCompiler);
      conformanceErrors = ImmutableList.builder();
    }

    ImmutableList<JSError> getConformanceErrors() {
      return conformanceErrors.build();
    }

    @Override
    public void report(JSError error) {
      if (error.getType().equals(CheckConformance.CONFORMANCE_ERROR)) {
        conformanceErrors.add(error);
      } else {
        super.report(error);
      }
    }

  }
}
