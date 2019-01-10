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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Requirement.Severity;
import com.google.javascript.rhino.Node;

/** Creates or updates conformance whitelist entries. */
@GwtIncompatible("Conformance")
public class ConformanceWhitelister {
  private ConformanceWhitelister() {}

  public static ImmutableSet<String> getViolatingPaths(
      Compiler compiler, Node externs, Node ast, Requirement requirement) {
    return getConformanceErrors(compiler, externs, ast, requirement)
        .stream()
        .map(e -> e.sourceName)
        .collect(ImmutableSet.toImmutableSet());
  }

  public static ImmutableSet<Node> getViolatingNodes(
      Compiler compiler, Node externs, Node ast, Requirement requirement) {
    return getConformanceErrors(compiler, externs, ast, requirement)
        .stream()
        .map(e -> e.node)
        .collect(ImmutableSet.toImmutableSet());
  }

  public static ImmutableList<JSError> getConformanceErrors(
      Compiler compiler, Node externs, Node ast, Requirement requirement) {
    Requirement cleanedRequirement =
        requirement
            .toBuilder()
            .clearWhitelist()
            .clearWhitelistRegexp()
            .clearWhitelistEntry()
            .setSeverity(Severity.ERROR)
            .build(); // So we only have one type of error.
    ConformanceConfig cleanedConfig =
        ConformanceConfig.newBuilder().addRequirement(cleanedRequirement).build();

    ErrorManager oldErrorManager = compiler.getErrorManager();
    final ImmutableList.Builder<JSError> errors = ImmutableList.builder();
    try {
      // TODO(bangert): handle invalid conformance requirements
      compiler.setErrorManager(
          new ThreadSafeDelegatingErrorManager(oldErrorManager) {
            @Override
            public synchronized boolean shouldReportConformanceViolation(
                Requirement requirement,
                Optional<Requirement.WhitelistEntry> whitelistEntry,
                JSError diagnostic) {
              errors.add(diagnostic);
              return false;
            }
          });
      CheckConformance check = new CheckConformance(compiler, ImmutableList.of(cleanedConfig));
      check.process(externs, ast);
    } finally {
      compiler.setErrorManager(oldErrorManager);
    }
    return errors.build();
  }
}
