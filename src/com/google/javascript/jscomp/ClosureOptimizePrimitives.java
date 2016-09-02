/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.javascript.rhino.dtoa.DToA.numberToString;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashSet;
import java.util.Set;

/**
 * Compiler pass that converts primitive calls:
 *
 * <p>Converts goog.object.create(key1, val1, key2, val2, ...) where all of the keys are literals
 * into object literals.
 *
 * <p>Converts goog.object.createSet(key1, key2, ...) into an object literal with the given keys,
 * where all the values are {@code true}.
 *
 * <p>Converts goog.reflect.objectProperty(propName, object) to JSCompiler_renameProperty
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
final class ClosureOptimizePrimitives implements CompilerPass {
  static final DiagnosticType DUPLICATE_SET_MEMBER =
      DiagnosticType.warning("JSC_DUPLICATE_SET_MEMBER", "Found duplicate value ''{0}'' in set");

  /** Reference to the JS compiler */
  private final AbstractCompiler compiler;

  /**
   * Identifies all calls to closure primitive functions
   */
  private class FindPrimitives extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        Node fn = n.getFirstChild();
        if (compiler
            .getCodingConvention()
            .isPropertyRenameFunction(fn.getOriginalQualifiedName())) {
          processRenamePropertyCall(n);
        } else if (fn.matchesQualifiedName("goog$object$create")
            || fn.matchesQualifiedName("goog.object.create")) {
          processObjectCreateCall(n);
        } else if (fn.matchesQualifiedName("goog$object$createSet")
            || fn.matchesQualifiedName("goog.object.createSet")) {
          processObjectCreateSetCall(n);
        }
      }
      maybeProcessDomTagName(n);
    }
  }

  /**
   * @param compiler The AbstractCompiler
   */
  ClosureOptimizePrimitives(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    FindPrimitives pass = new FindPrimitives();
    NodeTraversal.traverseEs6(compiler, root, pass);
  }

  /**
   * Converts all of the given call nodes to object literals that are safe to
   * do so.
   */
  private void processObjectCreateCall(Node callNode) {
    Node curParam = callNode.getSecondChild();
    if (canOptimizeObjectCreate(curParam)) {
      Node objNode = IR.objectlit().srcref(callNode);
      while (curParam != null) {
        Node keyNode = curParam;
        Node valueNode = curParam.getNext();
        curParam = valueNode.getNext();

        callNode.removeChild(keyNode);
        callNode.removeChild(valueNode);

        if (!keyNode.isString()) {
          keyNode = IR.string(NodeUtil.getStringValue(keyNode))
              .srcref(keyNode);
        }
        keyNode.setToken(Token.STRING_KEY);
        keyNode.setQuotedString();
        objNode.addChildToBack(IR.propdef(keyNode, valueNode));
      }
      callNode.getParent().replaceChild(callNode, objNode);
      compiler.reportCodeChange();
    }
  }

  /**
   * Converts all of the given call nodes to object literals that are safe to
   * do so.
   */
  private void processRenamePropertyCall(Node callNode) {
    Node nameNode = callNode.getFirstChild();
    if (nameNode.matchesQualifiedName(NodeUtil.JSC_PROPERTY_NAME_FN)) {
      return;
    }

    Node newTarget = IR.name(NodeUtil.JSC_PROPERTY_NAME_FN).useSourceInfoFrom(nameNode);
    newTarget.setOriginalName(nameNode.getOriginalQualifiedName());

    callNode.replaceChild(nameNode, newTarget);
    callNode.putBooleanProp(Node.FREE_CALL, true);
    compiler.reportCodeChange();
  }

  /**
   * Returns whether the given call to goog.object.create can be converted to an
   * object literal.
   */
  private static boolean canOptimizeObjectCreate(Node firstParam) {
    Node curParam = firstParam;
    while (curParam != null) {
      // All keys must be strings or numbers.
      if (!curParam.isString() && !curParam.isNumber()) {
        return false;
      }
      curParam = curParam.getNext();

      // Check for an odd number of parameters.
      if (curParam == null) {
        return false;
      }
      curParam = curParam.getNext();
    }
    return true;
  }

  /**
   * Converts all of the given call nodes to object literals that are safe to
   * do so.
   */
  private void processObjectCreateSetCall(Node callNode) {
    Node curParam = callNode.getSecondChild();
    if (canOptimizeObjectCreateSet(curParam)) {
      Node objNode = IR.objectlit().srcref(callNode);
      while (curParam != null) {
        Node keyNode = curParam;
        Node valueNode = IR.trueNode().srcref(keyNode);

        curParam = curParam.getNext();
        callNode.removeChild(keyNode);

        if (!keyNode.isString()) {
          keyNode = IR.string(NodeUtil.getStringValue(keyNode))
              .srcref(keyNode);
        }
        keyNode.setToken(Token.STRING_KEY);
        keyNode.setQuotedString();
        objNode.addChildToBack(IR.propdef(keyNode, valueNode));
      }
      callNode.getParent().replaceChild(callNode, objNode);
      compiler.reportCodeChange();
    }
  }

  /**
   * Returns whether the given call to goog.object.createSet can be converted to an object literal.
   */
  private boolean canOptimizeObjectCreateSet(Node firstParam) {
    Node curParam = firstParam;
    Set<String> keys = new HashSet<>();
    while (curParam != null) {
      // All keys must be strings or numbers, otherwise we can't optimize the call.
      if (!curParam.isString() && !curParam.isNumber()) {
        return false;
      }
      String key =
          curParam.isString() ? curParam.getString() : numberToString(curParam.getDouble());
      if (!keys.add(key)) {
        compiler.report(JSError.make(firstParam.getPrevious(), DUPLICATE_SET_MEMBER, key));
        return false;
      }
      curParam = curParam.getNext();
    }
    return true;
  }

  /**
   * Converts the given node to string if it is safe to do so.
   */
  private void maybeProcessDomTagName(Node n) {
    if (NodeUtil.isLValue(n)) {
      return;
    }
    String prefix = "goog$dom$TagName$";
    String tagName;
    if (n.isName() && n.getString().startsWith(prefix)) {
      tagName = n.getString().substring(prefix.length());
    } else if (n.isGetProp() && !n.getParent().isGetProp()
        && n.getFirstChild().matchesQualifiedName("goog.dom.TagName")) {
      tagName = n.getSecondChild().getString()
          .replaceFirst(".*\\$", ""); // Added by DisambiguateProperties.
    } else {
      return;
    }
    Node stringNode = IR.string(tagName).srcref(n);
    n.getParent().replaceChild(n, stringNode);
    compiler.reportCodeChange();
  }
}
