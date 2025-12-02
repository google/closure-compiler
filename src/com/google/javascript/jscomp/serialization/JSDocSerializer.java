/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** Utilities for serializing and deserializing JSDoc necessary for optimzations. */
public final class JSDocSerializer {

  private JSDocSerializer() {}

  /**
   * Returns a variant of input JSDocInfo where fields not needed for optimizations are removed
   *
   * <p>This uses the serialization / deserialization logic for JSDoc and ensures optimizations
   * can't accidentally depend on fields that we don't serialize.
   *
   * @return a new JSDocInfo object or null if no serializable fields are found
   */
  public static JSDocInfo convertJSDocInfoForOptimizations(JSDocInfo jsdoc) {
    StringPool.Builder stringPool = StringPool.builder();
    return deserializeJsdoc(serializeJsdoc(jsdoc, stringPool), stringPool.build());
  }

  static @Nullable OptimizationJsdoc serializeJsdoc(
      JSDocInfo jsdoc, StringPool.Builder stringPool) {
    if (jsdoc == null) {
      return null;
    }

    OptimizationJsdoc.Builder builder = OptimizationJsdoc.newBuilder();

    if (jsdoc.hasFileOverview()) {
      builder.addKind(JsdocTag.JSDOC_FILEOVERVIEW);
    }

    if (jsdoc.getLicense() != null) {
      builder.setLicenseTextPointer(stringPool.put(jsdoc.getLicense()));
    }

    if (jsdoc.isSassGeneratedCssTs()) {
      builder.addKind(JsdocTag.JSDOC_SASS_GENERATED_CSS_TS);
    }

    // Used by CoverageInstrumentationCallback
    if (jsdoc.isNoCoverage()) {
      builder.addKind(JsdocTag.JSDOC_NO_COVERAGE);
    }

    if (jsdoc.isNoInline()) {
      builder.addKind(JsdocTag.JSDOC_NO_INLINE);
    }

    if (jsdoc.isEncourageInlining()) {
      builder.addKind(JsdocTag.JSDOC_ENCOURAGE_INLINING);
    }

    if (jsdoc.isRequireInlining()) {
      builder.addKind(JsdocTag.JSDOC_REQUIRE_INLINING);
    }

    if (jsdoc.isNoCollapse()) {
      builder.addKind(JsdocTag.JSDOC_NO_COLLAPSE);
    }

    if (jsdoc.isProvideGoog()) {
      builder.addKind(JsdocTag.JSDOC_PROVIDE_GOOG);
    }

    if (jsdoc.isProvideAlreadyProvided()) {
      builder.addKind(JsdocTag.JSDOC_PROVIDE_ALREADY_PROVIDED);
    }

    if (jsdoc.isTypeSummary()) {
      builder.addKind(JsdocTag.JSDOC_TYPE_SUMMARY_FILE);
    }

    if (jsdoc.isPureOrBreakMyCode()) {
      builder.addKind(JsdocTag.JSDOC_PURE_OR_BREAK_MY_CODE);
    }
    if (jsdoc.isCollapsibleOrBreakMyCode()) {
      builder.addKind(JsdocTag.JSDOC_COLLAPSIBLE_OR_BREAK_MY_CODE);
    }
    if (jsdoc.hasThisType()) {
      builder.addKind(JsdocTag.JSDOC_THIS);
    }
    if (jsdoc.hasEnumParameterType()) {
      builder.addKind(JsdocTag.JSDOC_ENUM);
    }
    if (jsdoc.isDefine()) {
      builder.addKind(JsdocTag.JSDOC_DEFINE);
    }
    if (jsdoc.hasConstAnnotation()) {
      builder.addKind(JsdocTag.JSDOC_CONST);
    }
    if (jsdoc.isAnyIdGenerator()) {
      builder.addKind(serializeIdGenerator(jsdoc));
    }

    // Used by PureFunctionIdentifier
    if (jsdoc.isNoSideEffects()) {
      builder.addKind(JsdocTag.JSDOC_NO_SIDE_EFFECTS);
    }
    if (jsdoc.hasModifies()) {
      for (String modifies : jsdoc.getModifies()) {
        switch (modifies) {
          case "this" -> builder.addKind(JsdocTag.JSDOC_MODIFIES_THIS);
          case "arguments" -> builder.addKind(JsdocTag.JSDOC_MODIFIES_ARGUMENTS);
          default ->
              // Currently, anything other than "this" is considered a modification to arguments
              builder.addKind(JsdocTag.JSDOC_MODIFIES_ARGUMENTS);
        }
      }
    }
    if (!jsdoc.getThrowsAnnotations().isEmpty()) {
      builder.addKind(JsdocTag.JSDOC_THROWS);
    }

    // Used by DevirtualizeMethods and CollapseProperties
    if (jsdoc.isConstructor()) {
      builder.addKind(JsdocTag.JSDOC_CONSTRUCTOR);
    }
    if (jsdoc.isInterface()) {
      builder.addKind(JsdocTag.JSDOC_INTERFACE);
    }
    if (jsdoc.getSuppressions().contains("partialAlias")) {
      builder.addKind(JsdocTag.JSDOC_SUPPRESS_PARTIAL_ALIAS);
    }

    // Used by ClosureCodeRemoval
    if (jsdoc.isAbstract()) {
      builder.addKind(JsdocTag.JSDOC_ABSTRACT);
    }

    // Used by ReplaceMessages
    if (jsdoc.getDescription() != null) {
      builder.setDescriptionPointer(stringPool.put(jsdoc.getDescription()));
    }
    if (jsdoc.getAlternateMessageId() != null) {
      builder.setAlternateMessageIdPointer(stringPool.put(jsdoc.getAlternateMessageId()));
    }
    if (jsdoc.getMeaning() != null) {
      builder.setMeaningPointer(stringPool.put(jsdoc.getMeaning()));
    }
    if (jsdoc.getSuppressions().contains("messageConventions")) {
      builder.addKind(JsdocTag.JSDOC_SUPPRESS_MESSAGE_CONVENTION);
    }
    if (jsdoc.getSuppressions().contains("untranspilableFeatures")) {
      builder.addKind(JsdocTag.JSDOC_SUPPRESS_UNTRANSPILABLE_FEATURES);
    }
    if (jsdoc.isUsedViaDotConstructor()) {
      builder.addKind(JsdocTag.JSDOC_USED_VIA_DOT_CONSTRUCTOR);
    }

    OptimizationJsdoc result = builder.build();
    if (OptimizationJsdoc.getDefaultInstance().equals(result)) {
      return null;
    }
    return checkNotNull(result);
  }

  private static JsdocTag serializeIdGenerator(JSDocInfo doc) {
    if (doc.isConsistentIdGenerator()) {
      return JsdocTag.JSDOC_ID_GENERATOR_CONSISTENT;
    } else if (doc.isStableIdGenerator()) {
      return JsdocTag.JSDOC_ID_GENERATOR_STABLE;
    } else if (doc.isXidGenerator()) {
      return JsdocTag.JSDOC_ID_GENERATOR_XID;
    } else if (doc.isMappedIdGenerator()) {
      return JsdocTag.JSDOC_ID_GENERATOR_MAPPED;
    } else if (doc.isIdGenerator()) {
      return JsdocTag.JSDOC_ID_GENERATOR_INCONSISTENT;
    }
    throw new IllegalStateException("Failed to identify idGenerator inside JSDoc: " + doc);
  }

  // Optimizations shouldn't care about the contents of JSTypeExpressions but some JSDoc APIs
  // expect their presence, so create a placeholder type.
  private static final SourceFile SYNTHETIC_SOURCE =
      SourceFile.fromCode("JSDocSerializer_placeholder_source", "");

  private static final String PLACEHOLDER_TYPE_NAME = "JSDocSerializer_placeholder_type";

  private static JSTypeExpression createPlaceholderType() {
    // the BANG (!) token makes unit testing easier, as the JSDoc parser implicitly adds "!"
    // to some JSTypeExpressions
    Node name = IR.string(PLACEHOLDER_TYPE_NAME);
    Node bang = new Node(Token.BANG, name);
    name.setStaticSourceFile(SYNTHETIC_SOURCE);
    bang.setStaticSourceFile(SYNTHETIC_SOURCE);
    return new JSTypeExpression(bang, SYNTHETIC_SOURCE.getName());
  }

  private static final JSTypeExpression placeholderType = createPlaceholderType();

  static @Nullable JSDocInfo deserializeJsdoc(
      OptimizationJsdoc serializedJsdoc, StringPool stringPool) {
    if (serializedJsdoc == null) {
      return null;
    }
    JSDocInfo.Builder builder = JSDocInfo.builder();
    if (serializedJsdoc.getLicenseTextPointer() != 0) {
      builder.addLicense(stringPool.get(serializedJsdoc.getLicenseTextPointer()));
    }
    if (serializedJsdoc.getMeaningPointer() != 0) {
      builder.recordMeaning(stringPool.get(serializedJsdoc.getMeaningPointer()));
    }
    if (serializedJsdoc.getDescriptionPointer() != 0) {
      builder.recordDescription(stringPool.get(serializedJsdoc.getDescriptionPointer()));
    }
    if (serializedJsdoc.getAlternateMessageIdPointer() != 0) {
      builder.recordAlternateMessageId(
          stringPool.get(serializedJsdoc.getAlternateMessageIdPointer()));
    }

    // lazily create these sets to save a few hundred ms for some large projects
    TreeSet<String> modifies = null;
    TreeSet<String> suppressions = null;

    for (int i = 0; i < serializedJsdoc.getKindCount(); i++) {
      JsdocTag tag = serializedJsdoc.getKindList().get(i);
      switch (tag) {
        case JSDOC_CONST -> {
          builder.recordConstancy();
          continue;
        }
        case JSDOC_ENUM -> {
          builder.recordEnumParameterType(placeholderType);
          continue;
        }
        case JSDOC_THIS -> {
          builder.recordThisType(placeholderType);
          continue;
        }
        case JSDOC_NO_COLLAPSE -> {
          builder.recordNoCollapse();
          continue;
        }
        case JSDOC_NO_INLINE -> {
          builder.recordNoInline();
          continue;
        }
        case JSDOC_REQUIRE_INLINING -> {
          builder.recordRequireInlining();
          continue;
        }
        case JSDOC_ENCOURAGE_INLINING -> {
          builder.recordEncourageInlining();
          continue;
        }
        case JSDOC_PROVIDE_GOOG -> {
          builder.recordProvideGoog();
          continue;
        }
        case JSDOC_PROVIDE_ALREADY_PROVIDED -> {
          builder.recordProvideAlreadyProvided();
          continue;
        }
        case JSDOC_TYPE_SUMMARY_FILE -> {
          builder.recordTypeSummary();
          continue;
        }
        case JSDOC_PURE_OR_BREAK_MY_CODE -> {
          builder.recordPureOrBreakMyCode();
          continue;
        }
        case JSDOC_COLLAPSIBLE_OR_BREAK_MY_CODE -> {
          builder.recordCollapsibleOrBreakMyCode();
          continue;
        }
        case JSDOC_DEFINE -> {
          builder.recordDefineType(placeholderType);
          continue;
        }
        case JSDOC_NO_SIDE_EFFECTS -> {
          builder.recordNoSideEffects();
          continue;
        }
        case JSDOC_MODIFIES_THIS -> {
          modifies = (modifies != null ? modifies : new TreeSet<>());
          modifies.add("this");
          continue;
        }
        case JSDOC_MODIFIES_ARGUMENTS -> {
          modifies = (modifies != null ? modifies : new TreeSet<>());
          modifies.add("arguments");
          continue;
        }
        case JSDOC_THROWS -> {
          builder.recordThrowsAnnotation("{!" + PLACEHOLDER_TYPE_NAME + "}");
          continue;
        }
        case JSDOC_CONSTRUCTOR -> {
          builder.recordConstructor();
          continue;
        }
        case JSDOC_INTERFACE -> {
          builder.recordInterface();
          continue;
        }
        case JSDOC_SUPPRESS_PARTIAL_ALIAS -> {
          suppressions = (suppressions != null ? suppressions : new TreeSet<>());
          suppressions.add("partialAlias");
          continue;
        }
        case JSDOC_ID_GENERATOR_CONSISTENT -> {
          builder.recordConsistentIdGenerator();
          continue;
        }
        case JSDOC_ID_GENERATOR_STABLE -> {
          builder.recordStableIdGenerator();
          continue;
        }
        case JSDOC_ID_GENERATOR_MAPPED -> {
          builder.recordMappedIdGenerator();
          continue;
        }
        case JSDOC_ID_GENERATOR_XID -> {
          builder.recordXidGenerator();
          continue;
        }
        case JSDOC_ID_GENERATOR_INCONSISTENT -> {
          builder.recordIdGenerator();
          continue;
        }
        case JSDOC_ABSTRACT -> {
          builder.recordAbstract();
          continue;
          // TODO(lharker): stage 2 passes ideally shouldn't report diagnostics, so this could be
          // moved to stage 1.
        }
        case JSDOC_SUPPRESS_MESSAGE_CONVENTION -> {
          suppressions = (suppressions != null ? suppressions : new TreeSet<>());
          suppressions.add("messageConventions");
          continue;
          // ReportUntranspilableFeatures pass will run in stage2 since it uses languageOut
          // information. It reports diagnostic {@code UNTRANSPILABLE_FEATURE_PRESENT} that can be
          // supppressed using `untranspilableFeatures` suppression tag.
          // Hence we must propagate it.
        }
        case JSDOC_SUPPRESS_UNTRANSPILABLE_FEATURES -> {
          suppressions = (suppressions != null ? suppressions : new TreeSet<>());
          suppressions.add("untranspilableFeatures");
          continue;
        }
        case JSDOC_FILEOVERVIEW -> {
          builder.recordFileOverview("");
          continue;
        }
        case JSDOC_NO_COVERAGE -> {
          builder.recordNoCoverage();
          continue;
        }
        case JSDOC_SASS_GENERATED_CSS_TS -> {
          builder.recordSassGeneratedCssTs();
          continue;
        }
        case JSDOC_USED_VIA_DOT_CONSTRUCTOR -> {
          var unused = builder.recordUsedViaDotConstructor();
          continue;
        }
        case JSDOC_UNSPECIFIED, UNRECOGNIZED ->
            throw new MalformedTypedAstException(
                "Unsupported JSDoc tag can't be deserialized: " + tag);
      }
    }
    if (modifies != null) {
      builder.recordModifies(modifies);
    }
    if (suppressions != null) {
      builder.recordSuppressions(suppressions);
    }

    return builder.build();
  }
}
