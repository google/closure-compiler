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

/** Utilities for serializing and deserializing JSDoc necessary for optimzations. */
public final class JsdocSerializer {

  private JsdocSerializer() {}

  /**
   * Returns a variant of input JSDocInfo where fields not needed for optimizations are removed
   *
   * <p>This uses the serialization / deserialization logic for JSDoc and ensures optimizations
   * can't accidentally depend on fields that we don't serialize.
   *
   * @return a new JSDocInfo object or null if no serializable fields are found
   */
  public static JSDocInfo convertJSDocInfoForOptimizations(JSDocInfo jsdoc) {
    return deserializeJsdoc(serializeJsdoc(jsdoc));
  }

  static OptimizationJsdoc serializeJsdoc(JSDocInfo jsdoc) {
    if (jsdoc == null) {
      return null;
    }
    OptimizationJsdoc.Builder builder = OptimizationJsdoc.newBuilder();
    if (jsdoc.getLicense() != null) {
      builder.setLicenseText(jsdoc.getLicense());
    }

    if (jsdoc.isNoInline()) {
      builder.addKind(JsdocTag.JSDOC_NO_INLINE);
    }
    if (jsdoc.isNoCollapse()) {
      builder.addKind(JsdocTag.JSDOC_NO_COLLAPSE);
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
          case "this":
            builder.addKind(JsdocTag.JSDOC_MODIFIES_THIS);
            continue;
          case "arguments":
            // Currently, anything other than "this" is considered a modification to arguments
          default:
            builder.addKind(JsdocTag.JSDOC_MODIFIES_ARGUMENTS);
            continue;
        }
      }
    }
    if (!jsdoc.getThrownTypes().isEmpty()) {
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
    if (jsdoc.isHidden()) {
      builder.addKind(JsdocTag.JSDOC_HIDDEN);
    }
    if (jsdoc.getDescription() != null) {
      builder.setDescription(jsdoc.getDescription());
    }
    if (jsdoc.getAlternateMessageId() != null) {
      builder.setAlternateMessageId(jsdoc.getAlternateMessageId());
    }
    if (jsdoc.getMeaning() != null) {
      builder.setMeaning(jsdoc.getMeaning());
    }
    if (jsdoc.getSuppressions().contains("messageConventions")) {
      builder.addKind(JsdocTag.JSDOC_SUPPRESS_MESSAGE_CONVENTION);
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
      SourceFile.fromCode("JsdocSerializer_placeholder_source", "");

  private static JSTypeExpression createPlaceholderType() {
    // the BANG (!) token makes unit testing easier, as the JSDoc parser implicitly adds "!"
    // to some JSTypeExpressions
    Node name = IR.string("JsdocSerializer_placeholder_type");
    Node bang = new Node(Token.BANG, name);
    name.setStaticSourceFile(SYNTHETIC_SOURCE);
    bang.setStaticSourceFile(SYNTHETIC_SOURCE);
    return new JSTypeExpression(bang, SYNTHETIC_SOURCE.getName());
  }

  private static final JSTypeExpression placeholderType = createPlaceholderType();

  static JSDocInfo deserializeJsdoc(OptimizationJsdoc serializedJsdoc) {
    if (serializedJsdoc == null) {
      return null;
    }
    JSDocInfo.Builder builder = JSDocInfo.builder();
    String license = serializedJsdoc.getLicenseText();
    if (!license.isEmpty()) {
      builder.addLicense(license);
    }
    if (!serializedJsdoc.getMeaning().isEmpty()) {
      builder.recordMeaning(serializedJsdoc.getMeaning());
    }
    if (!serializedJsdoc.getDescription().isEmpty()) {
      builder.recordDescription(serializedJsdoc.getDescription());
    }
    if (!serializedJsdoc.getAlternateMessageId().isEmpty()) {
      builder.recordAlternateMessageId(serializedJsdoc.getAlternateMessageId());
    }

    TreeSet<String> modifies = new TreeSet<>();
    TreeSet<String> suppressions = new TreeSet<>();
    for (JsdocTag tag : serializedJsdoc.getKindList()) {
      switch (tag) {
        case JSDOC_CONST:
          builder.recordConstancy();
          continue;
        case JSDOC_ENUM:
          builder.recordEnumParameterType(placeholderType);
          continue;
        case JSDOC_THIS:
          builder.recordThisType(placeholderType);
          continue;
        case JSDOC_NO_COLLAPSE:
          builder.recordNoCollapse();
          continue;
        case JSDOC_NO_INLINE:
          builder.recordNoInline();
          continue;
        case JSDOC_PURE_OR_BREAK_MY_CODE:
          builder.recordPureOrBreakMyCode();
          continue;
        case JSDOC_COLLAPSIBLE_OR_BREAK_MY_CODE:
          builder.recordCollapsibleOrBreakMyCode();
          continue;
        case JSDOC_DEFINE:
          builder.recordDefineType(placeholderType);
          continue;
        case JSDOC_NO_SIDE_EFFECTS:
          builder.recordNoSideEffects();
          continue;
        case JSDOC_MODIFIES_THIS:
          modifies.add("this");
          continue;
        case JSDOC_MODIFIES_ARGUMENTS:
          modifies.add("arguments");
          continue;
        case JSDOC_THROWS:
          builder.recordThrowType(placeholderType);
          continue;
        case JSDOC_CONSTRUCTOR:
          builder.recordConstructor();
          continue;
        case JSDOC_INTERFACE:
          builder.recordInterface();
          continue;
        case JSDOC_SUPPRESS_PARTIAL_ALIAS:
          suppressions.add("partialAlias");
          continue;

        case JSDOC_ID_GENERATOR_CONSISTENT:
          builder.recordConsistentIdGenerator();
          continue;
        case JSDOC_ID_GENERATOR_STABLE:
          builder.recordStableIdGenerator();
          continue;
        case JSDOC_ID_GENERATOR_MAPPED:
          builder.recordMappedIdGenerator();
          continue;
        case JSDOC_ID_GENERATOR_XID:
          builder.recordXidGenerator();
          continue;
        case JSDOC_ID_GENERATOR_INCONSISTENT:
          builder.recordIdGenerator();
          continue;

        case JSDOC_ABSTRACT:
          builder.recordAbstract();
          continue;

        case JSDOC_HIDDEN:
          builder.recordHiddenness();
          continue;

          // TODO(lharker): stage 2 passes ideally shouldn't report diagnostics, so this could be
          // moved to stage 1.
        case JSDOC_SUPPRESS_MESSAGE_CONVENTION:
          suppressions.add("messageConventions");
          continue;

        case JSDOC_UNSPECIFIED:
        case UNRECOGNIZED:
          throw new MalformedTypedAstException(
              "Unsupported JSDoc tag can't be deserialized: " + tag);
      }
    }
    if (!modifies.isEmpty()) {
      builder.recordModifies(modifies);
    }
    if (!suppressions.isEmpty()) {
      builder.recordSuppressions(suppressions);
    }

    return builder.build();
  }
}
