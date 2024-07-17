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
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.*;

import java.util.LinkedHashSet;
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
 */
final class ClosureOptimizePrimitives implements CompilerPass {
  static final DiagnosticType DUPLICATE_SET_MEMBER =
      DiagnosticType.warning("JSC_DUPLICATE_SET_MEMBER", "Found duplicate value ''{0}'' in set");

  private static final QualifiedName GOOG_OBJECT_CREATE = QualifiedName.of("goog.object.create");
  private static final QualifiedName GOOG_OBJECT_CREATESET =
      QualifiedName.of("goog.object.createSet");

  /** Reference to the JS compiler */
  private final AbstractCompiler compiler;

  /** Whether we can use Es6 syntax */
  private final boolean canUseEs6Syntax;

  /** Identifies all calls to closure primitive functions */
  private class FindPrimitives extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()) {
        Node fn = n.getFirstChild();
        ClosurePrimitive primitive =
            fn != null ? fn.getColor() != null ? fn.getColor().getClosurePrimitive() : null : null;
        // TODO(user): Once goog.object becomes a goog.module, remove "goog$object$create" and
        // related checks.
        if (compiler.getCodingConvention().isPropertyRenameFunction(fn)) {
          processRenamePropertyCall(n);
        } else if (ClosurePrimitive.OBJECT_CREATE == primitive
            || fn.matchesName("goog$object$create")
            || fn.matchesName("module$contents$goog$object_create")
            || GOOG_OBJECT_CREATE.matches(fn)) {
          processObjectCreateCall(n);
        } else if (ClosurePrimitive.OBJECT_CREATE_SET == primitive
            || fn.matchesName("goog$object$createSet")
            || fn.matchesName("module$contents$goog$object_createSet")
            || GOOG_OBJECT_CREATESET.matches(fn)) {
          processObjectCreateSetCall(n);
        }
      }
    }
  }

  /**
   * @param compiler The AbstractCompiler
   */
  ClosureOptimizePrimitives(AbstractCompiler compiler, boolean canUseEs6Syntax) {
    this.compiler = compiler;
    this.canUseEs6Syntax = canUseEs6Syntax;
  }

  @Override
  public void process(Node externs, Node root) {
    FindPrimitives pass = new FindPrimitives();
    NodeTraversal.traverse(compiler, root, pass);
  }

  /** Converts all of the given call nodes to object literals that are safe to do so. */
  private void processObjectCreateCall(Node callNode) {
    Node curParam = callNode.getSecondChild();
    if (canOptimizeObjectCreate(curParam)) {
      Node objNode = IR.objectlit().srcref(callNode);
      while (curParam != null) {
        Node keyNode = curParam;
        Node valueNode = curParam.getNext();
        curParam = valueNode.getNext();

        keyNode.detach();
        valueNode.detach();

        addKeyValueToObjLit(objNode, keyNode, valueNode, NodeUtil.getEnclosingScript(callNode));
      }
      callNode.replaceWith(objNode);
      compiler.reportChangeToEnclosingScope(objNode);
    }
  }

  /** Converts all of the given call nodes to object literals that are safe to do so. */
  private void processRenamePropertyCall(Node callNode) {
    Node nameNode = callNode.getFirstChild();
    if (nameNode.matchesQualifiedName(NodeUtil.JSC_PROPERTY_NAME_FN)) {
      return;
    }

    Node newTarget = IR.name(NodeUtil.JSC_PROPERTY_NAME_FN).srcref(nameNode);
    newTarget.setOriginalName(nameNode.getOriginalQualifiedName());

    nameNode.replaceWith(newTarget);
    callNode.putBooleanProp(Node.FREE_CALL, true);
    compiler.reportChangeToEnclosingScope(callNode);
  }

  /** Returns whether the given call to goog.object.create can be converted to an object literal. */
  private boolean canOptimizeObjectCreate(Node firstParam) {
    Node curParam = firstParam;
    while (curParam != null) {
      if (!isOptimizableKey(curParam)) {
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

  /** Converts all of the given call nodes to object literals that are safe to do so. */
  private void processObjectCreateSetCall(Node callNode) {
    Node curParam = callNode.getSecondChild();
    if (canOptimizeObjectCreateSet(curParam)) {
      Node objNode = IR.objectlit().srcref(callNode);
      while (curParam != null) {
        Node keyNode = curParam;
        Node valueNode = IR.trueNode().srcref(keyNode);

        curParam = curParam.getNext();
        keyNode.detach();

        addKeyValueToObjLit(objNode, keyNode, valueNode, NodeUtil.getEnclosingScript(callNode));
      }
      callNode.replaceWith(objNode);
      compiler.reportChangeToEnclosingScope(objNode);
    }
  }

  /**
   * Returns whether the given call to goog.object.createSet can be converted to an object literal.
   */
  private boolean canOptimizeObjectCreateSet(Node firstParam) {
    if (firstParam != null
        && firstParam.getNext() == null
        && !(firstParam.isNumber() || firstParam.isStringLit())) {
      // if there is only one argument, and it's an array, then the method uses the array elements
      // as keys. Don't optimize it to {[arr]: true}. We only special-case number and string
      // arguments in order to not regress ES5-out behavior
      return false;
    }

    Node curParam = firstParam;
    Set<String> keys = new LinkedHashSet<>();
    while (curParam != null) {
      // All keys must be strings or numbers, otherwise we can't optimize the call.
      if (!isOptimizableKey(curParam)) {
        return false;
      }
      if (curParam.isStringLit() || curParam.isNumber()) {
        String key =
            curParam.isStringLit() ? curParam.getString() : numberToString(curParam.getDouble());
        if (!keys.add(key)) {
          compiler.report(JSError.make(firstParam.getPrevious(), DUPLICATE_SET_MEMBER, key));
          return false;
        }
      }
      curParam = curParam.getNext();
    }
    return true;
  }

  private void addKeyValueToObjLit(Node objNode, Node keyNode, Node valueNode, Node scriptNode) {
    if (keyNode.isNumber() || keyNode.isStringLit()) {
      if (keyNode.isNumber()) {
        keyNode = IR.string(numberToString(keyNode.getDouble())).srcref(keyNode);
      }
      // It isn't valid for a `STRING_KEY` to be marked as parenthesized.
      keyNode.setIsParenthesized(false);
      keyNode.setToken(Token.STRING_KEY);
      keyNode.setQuotedStringKey();
      objNode.addChildToBack(IR.propdef(keyNode, valueNode));
    } else {
      objNode.addChildToBack(IR.computedProp(keyNode, valueNode).srcref(keyNode));
      NodeUtil.addFeatureToScript(scriptNode, Feature.COMPUTED_PROPERTIES, compiler);
    }
  }

  private boolean isOptimizableKey(Node curParam) {
    if (this.canUseEs6Syntax) {
      return !NodeUtil.isStatement(curParam);
    } else {
      // Not ES6, all keys must be strings or numbers.
      return curParam.isStringLit() || curParam.isNumber();
    }
  }
}
