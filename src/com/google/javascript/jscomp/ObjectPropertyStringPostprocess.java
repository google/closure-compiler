/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Rewrites
 * <code>new JSCompiler_ObjectPropertyString(window, foo.prototype.bar)</code>
 * to <code>new JSCompiler_ObjectPropertyString(foo.prototype, 'bar')</code>
 *
 * Rewrites
 * <code>new JSCompiler_ObjectPropertyString(window, foo[bar])</code>
 * to <code>new JSCompiler_ObjectPropertyString(foo, bar)</code>

 * Rewrites
 * <code>new JSCompiler_ObjectPropertyString(window, foo$bar$baz)</code> to
 * <code>new JSCompiler_ObjectPropertyString(window, 'foo$bar$baz')</code>
 *
 * @see ObjectPropertyStringPreprocess
 *
 */
class ObjectPropertyStringPostprocess implements CompilerPass {
  private final AbstractCompiler compiler;

  public ObjectPropertyStringPostprocess(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, new Callback());
  }

  private class Callback extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isNew()) {
        return;
      }

      Node objectName = n.getFirstChild();

      if (!objectName.matchesQualifiedName(
          SimpleDefinitionFinder.EXTERN_OBJECT_PROPERTY_STRING)) {
        return;
      }

      Node firstArgument = objectName.getNext();
      Node secondArgument = firstArgument.getNext();
      int secondArgumentType = secondArgument.getType();
      if (secondArgumentType == Token.GETPROP) {
        // Rewrite "new goog.testing.ObjectPropertyString(window, foo.bar)"
        // as "new goog.testing.ObjectPropertyString(foo, 'bar')".
        Node newChild = secondArgument.getFirstChild();
        secondArgument.removeChild(newChild);
        n.replaceChild(firstArgument, newChild);
        n.replaceChild(secondArgument,
            IR.string(secondArgument.getFirstChild().getString()));
      } else if (secondArgumentType == Token.GETELEM) {
        // Rewrite "new goog.testing.ObjectPropertyString(window, foo[bar])"
        // as "new goog.testing.ObjectPropertyString(foo, bar)".
        Node newFirstArgument = secondArgument.getFirstChild();
        secondArgument.removeChild(newFirstArgument);
        Node newSecondArgument = secondArgument.getLastChild();
        secondArgument.removeChild(newSecondArgument);
        n.replaceChild(firstArgument, newFirstArgument);
        n.replaceChild(secondArgument, newSecondArgument);
      } else {
        // Rewrite "new goog.testing.ObjectPropertyString(window, foo)" as
        // "new goog.testing.ObjectPropertyString(window, 'foo')"
        n.replaceChild(secondArgument,
            IR.string(secondArgument.getString()));
      }
      compiler.reportCodeChange();
    }
  }
}
