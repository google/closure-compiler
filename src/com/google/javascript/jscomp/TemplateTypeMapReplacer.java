/*
 * Copyright 2013 The Closure Compiler Authors.
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

import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ModificationVisitor;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;

import java.util.ArrayDeque;

/**
 * Uses a TemplateTypeMap to replace TemplateTypes with their associated JSType
 * values.
 *
 * @author izaakr@google.com (Izaak Rubin)
 */
public class TemplateTypeMapReplacer extends ModificationVisitor {
  private final TemplateTypeMap replacements;
  private ArrayDeque<TemplateType> visitedTypes;

  TemplateTypeMapReplacer(
      JSTypeRegistry registry, TemplateTypeMap replacements) {
    super(registry);
    this.replacements = replacements;
    this.visitedTypes = new ArrayDeque<TemplateType>();
  }

  @Override
  public JSType caseTemplateType(TemplateType type) {
    if (replacements.hasTemplateKey(type)) {
      if (hasVisitedType(type)) {
        // If we have already encountered this TemplateType during replacement
        // (i.e. there is a reference loop), return the type itself.
        return type;
      } else {
        JSType replacement = replacements.getTemplateType(type);

        visitedTypes.push(type);
        JSType visitedReplacement = replacement.visit(this);
        visitedTypes.pop();

        return visitedReplacement;
      }
    } else {
      return type;
    }
  }

  /**
   * Checks if the specified type has already been visited during the Visitor's
   * traversal of a JSType.
   */
  private boolean hasVisitedType(TemplateType type) {
    for (TemplateType visitedType : visitedTypes) {
      if (visitedType == type) {
        return true;
      }
    }
    return false;
  }
}
