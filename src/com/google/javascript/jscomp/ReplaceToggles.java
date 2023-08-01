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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Replaces calls to id generators with ids.
 *
 * <p>Use this to get unique and short ids.
 */
class ReplaceToggles implements CompilerPass {
  static final DiagnosticType INVALID_TOGGLE_PARAMETER =
      DiagnosticType.error(
          "JSC_INVALID_TOGGLE_PARAMETER",
          "goog.readToggleInternalDoNotCallDirectly must be called with a string literal.");

  static final DiagnosticType INVALID_ORDINAL_MAPPING =
      DiagnosticType.error(
          "JSC_INVALID_ORDINAL_MAPPING",
          "_F_toggleOrdinals must be initialized with an object literal mapping strings to booleans"
              + " or unique whole numbers: {0}");

  static final DiagnosticType UNKNOWN_TOGGLE =
      DiagnosticType.error(
          "JSC_UNKNOWN_TOGGLE",
          "goog.readToggleInternalDoNotCallDirectly called with an unknown toggle. If a toggle"
              + " list is given, it must be exhaustive.");

  // NOTE: These values are chosen as negative integers because actual toggle ordinals must always
  // be non-negative (at least zero).  Any negative integers would do to distinguish them from real
  // toggle ordinals, but -1 and -2 are the simplest.
  @VisibleForTesting static final int TRUE_VALUE = -2;

  @VisibleForTesting static final int FALSE_VALUE = -1;

  private final AbstractCompiler compiler;
  private final AstFactory astFactory;
  private final boolean check;

  ReplaceToggles(AbstractCompiler compiler, boolean check) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.check = check;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Traversal());
  }

  private static final String ORDINAL_VAR_NAME = "_F_toggleOrdinals";
  private static final QualifiedName readToggleFunctionName =
      QualifiedName.of("goog.readToggleInternalDoNotCallDirectly");

  private class Traversal extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Look for `var _F_toggleOrdinals = {...};`.  Note that we only do this in check mode.
      // It's not our responsibility to delete the unused variable in optimized mode - if all
      // the calls to readToggle are deleted, then the bootstrap will be unused and deleted, too.
      if (check
          && NodeUtil.isNameDeclaration(n)
          && n.getFirstChild().matchesName(ORDINAL_VAR_NAME)) {
        Node rhs = n.getFirstFirstChild();

        if (rhs == null && n.getToken() == Token.VAR) {
          // An empty var is a type definition, which is OK.
          return;
        } else if (!rhs.isObjectLit()) {
          compiler.report(JSError.make(n, INVALID_ORDINAL_MAPPING, "not an object literal"));
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
        compiler.setToggleOrdinalMapping(ImmutableMap.copyOf(mapping));

        // NOTE: We do not support a simple assignment without `var` since reassignment or later
        // augmentation is not allowed.
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

      ImmutableMap<String, Integer> toggles = compiler.getToggleOrdinalMapping();
      if (check) {
        if (toggles != null && !toggles.containsKey(arg.getString())) {
          compiler.report(JSError.make(n, UNKNOWN_TOGGLE));
        }
        return;
      }

      Integer ordinal = toggles != null ? toggles.get(arg.getString()) : null;
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
