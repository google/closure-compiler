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

import com.google.javascript.jscomp.serialization.ColorDeserializer.InvalidSerializedFormatException;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
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
    if (jsdoc.isDefine()) {
      builder.addKind(JsdocTag.JSDOC_DEFINE);
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
    OptimizationJsdoc result = builder.build();
    if (OptimizationJsdoc.getDefaultInstance().equals(result)) {
      return null;
    }
    return checkNotNull(result);
  }

  // Optimizations shouldn't care about the contents of JSTypeExpressions but some JSDoc APIs
  // expect their presense, so just create a dummy '?' type.
  private static JSTypeExpression createUnknown() {
    return new JSTypeExpression(new Node(Token.QMARK), "<synthetic serialized type>");
  }

  static JSDocInfo deserializeJsdoc(OptimizationJsdoc serializedJsdoc) {
    if (serializedJsdoc == null) {
      return null;
    }
    JSDocInfoBuilder builder = JSDocInfo.builder();
    String license = serializedJsdoc.getLicenseText();
    if (!license.isEmpty()) {
      builder.addLicense(license);
    }
    TreeSet<String> modifies = new TreeSet<>();
    for (JsdocTag tag : serializedJsdoc.getKindList()) {
      switch (tag) {
        case JSDOC_NO_INLINE:
          builder.recordNoInline();
          continue;
        case JSDOC_DEFINE:
          builder.recordDefineType(createUnknown());
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
          builder.recordThrowType(createUnknown());
          continue;
        case JSDOC_CONSTRUCTOR:
          builder.recordConstructor();
          continue;
        case JSDOC_INTERFACE:
          builder.recordInterface();
          continue;

        case JSDOC_UNSPECIFIED:
        case UNRECOGNIZED:
          throw new InvalidSerializedFormatException(
              "Unsupported JSDoc tag can't be deserialized: " + tag);
      }
    }
    if (!modifies.isEmpty()) {
      builder.recordModifies(modifies);
    }
    return builder.build();
  }
}
