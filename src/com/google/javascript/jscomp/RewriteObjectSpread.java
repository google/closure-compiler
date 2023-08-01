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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.javascript.jscomp.AstFactory.type;

import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;

/**
 * Converts object spread to valid ES2017 code.
 *
 * <p>Currently this class converts Object spread properties as documented in tc39.
 * https://github.com/tc39/proposal-object-rest-spread. For example:
 *
 * <p>{@code const bar = {a: 1, ...foo};}
 *
 * <p>Note that object rest is handled by {@link Es6RewriteDestructuring}
 */
public final class RewriteObjectSpread implements NodeTraversal.Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.OBJECT_LITERALS_WITH_SPREAD);

  private final AstFactory astFactory;
  private final StaticScope namespace;

  public RewriteObjectSpread(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.namespace = this.compiler.getTranspilationNamespace();
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, root, transpiledFeatures);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case OBJECTLIT:
        visitObject(n);
        break;
      default:
        break;
    }
  }

  private void visitObject(Node obj) {
    for (Node child = obj.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isSpread()) {
        visitObjectWithSpread(obj);
        return;
      }
    }
  }

  /*
   * Convert '{first: b, c, ...spread, d: e, last}' to:
   *
   * Object.assign({}, {first:b, c}, spread, {d:e, last});
   */
  private void visitObjectWithSpread(Node obj) {
    checkArgument(obj.isObjectLit());

    // Add an empty target object literal so changes made by Object.assign will not affect any other
    // variables.
    Node result =
        astFactory.createObjectDotAssignCall(namespace, type(obj), astFactory.createObjectLit());

    // An indicator whether the current last thing in the param list is an object literal to which
    // properties may be added.  Initialized to null since nothing should be added to the empty
    // object literal in first position of the param list.
    Node trailingObjectLiteral = null;

    for (Node child = obj.getFirstChild(); child != null; ) {
      final Node next = child.getNext();
      if (child.isSpread()) {
        // Add the object directly to the param list.
        Node spreaded = child.removeFirstChild();
        result.addChildToBack(spreaded);

        // Properties should not be added to the trailing object.
        trailingObjectLiteral = null;
      } else {
        if (trailingObjectLiteral == null) {
          // Add a new object to which properties may be added.
          trailingObjectLiteral = astFactory.createObjectLit();
          result.addChildToBack(trailingObjectLiteral);
        }
        // Add the property to the object literal.
        trailingObjectLiteral.addChildToBack(child.detach());
      }
      child = next;
    }

    result.srcrefTreeIfMissing(obj);
    obj.replaceWith(result);
    compiler.reportChangeToEnclosingScope(result);
  }
}
