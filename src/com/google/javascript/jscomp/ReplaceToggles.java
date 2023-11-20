/*
 * Copyright 2023 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Replaces calls to id generators with ids.
 *
 * <p>Use this to get unique and short ids.
 */
class ReplaceToggles implements CompilerPass {

  // NOTE: These diagnostics are only checked in Stage 2 (optimization), since none of this
  // code should ever be hand-written: the `CLOSURE_TOGGLE_ORDINALS` bootstrap and the calls to
  // `goog.readToggleInternalDoNotCallDirectly` are all generated automatically by the build
  // system, so it's unexpected that anyone should ever run into these diagnostics when doing
  // ordinary development.

  static final DiagnosticType INVALID_TOGGLE_PARAMETER =
      DiagnosticType.error(
          "JSC_INVALID_TOGGLE_PARAMETER",
          "goog.readToggleInternalDoNotCallDirectly must be called with a string literal.");

  static final DiagnosticType INVALID_ORDINAL_MAPPING =
      DiagnosticType.error(
          "JSC_INVALID_ORDINAL_MAPPING",
          "CLOSURE_TOGGLE_ORDINALS must be initialized with an object literal mapping strings to"
              + " booleans or unique whole numbers: {0}");

  // NOTE: These values are chosen as negative integers because actual toggle ordinals must always
  // be non-negative (at least zero).  Any negative integers would do to distinguish them from real
  // toggle ordinals, but -1 and -2 are the simplest.
  private static final int TRUE_VALUE = -2;
  private static final int FALSE_VALUE = -1;

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;

  private @Nullable ImmutableMap<String, Integer> ordinalMapping = null;

  ReplaceToggles(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Traversal());
  }

  private static final String ORDINAL_VAR_NAME = "CLOSURE_TOGGLE_ORDINALS";
  private static final QualifiedName readToggleFunctionName =
      QualifiedName.of("goog.readToggleInternalDoNotCallDirectly");

  private class Traversal extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Look for `var CLOSURE_TOGGLE_ORDINALS = {...};`, record the mapping, and then delete the
      // declaration from the AST since we should no longer need it (and the optimizer won't
      // delete it in case the global assignment was an intended side effect).
      if (NodeUtil.isNameDeclaration(n) && n.getFirstChild().matchesName(ORDINAL_VAR_NAME)) {
        Node rhs = n.getFirstFirstChild();

        if (rhs == null && n.isVar()) {
          // An empty var is fine; it should get deleted later.
          return;
        } else if (!rhs.isObjectLit()) {
          compiler.report(JSError.make(n, INVALID_ORDINAL_MAPPING, "not an object literal"));
          return;
        } else if (ordinalMapping != null) {
          compiler.report(JSError.make(n, INVALID_ORDINAL_MAPPING, "multiple initialized copies"));
          return;
        }

        Map<String, Integer> mapping = new LinkedHashMap<>();
        Set<Integer> ordinals = new HashSet<>();
        for (Node c = rhs.getFirstChild(); c != null; c = c.getNext()) {
          if (!c.isStringKey() && !c.isStringLit()) {
            compiler.report(JSError.make(c, INVALID_ORDINAL_MAPPING, "non-string key"));
            return;
          }
          String key = c.getString();
          if (mapping.containsKey(key)) {
            compiler.report(JSError.make(c, INVALID_ORDINAL_MAPPING, "duplicate key: " + key));
            return;
          }
          Node child = c.getFirstChild();
          Double doubleValue = NodeUtil.getNumberValue(child);
          int intValue = doubleValue != null ? doubleValue.intValue() : -1;
          if (child.isTrue() || child.isFalse()) {
            intValue = child.isTrue() ? TRUE_VALUE : FALSE_VALUE;
          } else if (!child.isNumber() || intValue < 0 || intValue != doubleValue) {
            compiler.report(
                JSError.make(
                    c, INVALID_ORDINAL_MAPPING, "value not a boolean or whole number literal"));
            return;
          } else if (ordinals.contains(intValue)) {
            compiler.report(
                JSError.make(c, INVALID_ORDINAL_MAPPING, "duplicate ordinal: " + intValue));
            return;
          }
          mapping.put(key, intValue);
          ordinals.add(intValue);
        }
        ReplaceToggles.this.ordinalMapping = ImmutableMap.copyOf(mapping);

        // NOTE: We do not support a simple assignment without `var` since reassignment or later
        // augmentation (i.e. `CLOSURE_TOGGLE_ORDINALS['foo'] = true`) is not allowed.
        return;
      }

      if (!n.isCall()) {
        return;
      }
      Node qname = NodeUtil.getCallTargetResolvingIndirectCalls(n);
      if (!readToggleFunctionName.matches(qname)) {
        return;
      }

      Node arg = n.getSecondChild();
      if (arg == null || !arg.isStringLit() || !n.hasTwoChildren()) {
        compiler.report(JSError.make(n, INVALID_TOGGLE_PARAMETER));
        return;
      }

      Integer ordinal = ordinalMapping != null ? ordinalMapping.get(arg.getString()) : null;
      if (ordinal == null || ordinal < 0) {
        // No ordinals given: hard-code `true` if explicitly set as true, or `false` otherwise.
        n.replaceWith(
            astFactory
                .createBoolean(ordinal != null && ordinal == TRUE_VALUE)
                .srcrefTreeIfMissing(n));
        t.reportCodeChange();
        return;
      }

      // Replace with a lookup into the data structure
      // Note: the choice of 30 here is per spec, since the bootstrap must be written in agreement
      // with this convention.  30 was chosen over 32 to ensure all numbers are SMI and will fit
      // neatly in 4 bytes, whereas larger ints require more space in the VM.
      int index = ordinal / 30;
      int bit = ordinal % 30;
      Node getElem =
          astFactory.createGetElem(
              astFactory.createQNameWithUnknownType("goog.TOGGLES_"),
              astFactory.createNumber(index));
      // There are two different ways to write the bitwiseAnd:
      //  1. `getElem & mask`
      //  2. `getElem >> bit & 1` (note: sign extension is irrelevant here, and `>>` is shorter)
      // where x is the `goog.TOGGLES_[index]` lookup, and `mask` is `1 << bit`.
      // When `bit < 14`, option 1 is shorter.  When `bit > 16`, option 2 is shorter.
      // We arbitrarily prefer option 2 in the break-even range 14..16.
      Node bitAnd;
      if (bit < 14) {
        bitAnd = astFactory.createBitwiseAnd(getElem, astFactory.createNumber(1 << bit));
      } else {
        bitAnd =
            astFactory.createBitwiseAnd(
                astFactory.createRightShift(getElem, astFactory.createNumber(bit)),
                astFactory.createNumber(1));
      }
      n.replaceWith(astFactory.createNot(astFactory.createNot(bitAnd)).srcrefTreeIfMissing(n));
      t.reportCodeChange();
    }
  }
}
