/*
 * Copyright 2025 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.conformance;

import com.google.common.base.Optional;
import com.google.javascript.jscomp.JsCompilerConformanceReport.ConformanceViolation;
import com.google.javascript.jscomp.Requirement;
import com.google.javascript.jscomp.Requirement.WhitelistEntry;
import org.jspecify.annotations.Nullable;

/** Utility methods for writing out JSCompiler conformance reports. */
public final class ConformanceReporterUtil {

  public static ConformanceViolation toProto(
      Requirement req,
      Optional<WhitelistEntry> allowlistEntry,
      String sourceName,
      int lineNumber,
      int charno) {
    ConformanceViolation.Builder violation = ConformanceViolation.newBuilder();

    Requirement.Builder reqBuilder = Requirement.newBuilder();
    if (req.hasType()) {
      reqBuilder.setType(req.getType());
    }
    reqBuilder.addAllValue(req.getValueList());
    if (req.hasJavaClass()) {
      reqBuilder.setJavaClass(req.getJavaClass());
    }
    violation.setRequirement(reqBuilder);

    WhitelistEntry.Builder allowlistEntryBuilder = WhitelistEntry.newBuilder();
    if (allowlistEntry.isPresent() && allowlistEntry.get().hasReason()) {
      allowlistEntryBuilder.setReason(allowlistEntry.get().getReason());
    }

    violation
        .setAllowlistEntry(allowlistEntryBuilder)
        .setPath(sourceName)
        .setLineNo(lineNumber)
        .setColNo(charno);

    // Use the requirement's ruleId if it's available, or the name of the first config file
    // otherwise.
    if (req.hasRuleId()) {
      violation.setRuleId(req.getRuleId());
    } else if (req.getConfigFileCount() > 0) {
      String shortName = extractConfigFileShortName(req.getConfigFile(0));
      if (shortName != null) {
        violation.setRuleId(shortName);
      }
    }
    return violation.build();
  }

  private static final String CONFORMANCE_TEXTPB = ".conformance.textpb";

  /**
   * Get the short name of a conformance config file.
   *
   * <p>This is the same as regexing for ".*\\/(.+).conformance.textpb$" but measured to be ~200x
   * faster than using an actual regex (2021-08-25)
   */
  private static @Nullable String extractConfigFileShortName(String path) {
    if (!path.endsWith(CONFORMANCE_TEXTPB)) {
      return null;
    }

    int dotIndex = path.length() - CONFORMANCE_TEXTPB.length();
    int slashIndex = path.lastIndexOf('/', dotIndex);
    if (slashIndex < 0 || (dotIndex - slashIndex) < 2) {
      return null;
    }

    return path.substring(slashIndex + 1, dotIndex);
  }

  private ConformanceReporterUtil() {}
}
