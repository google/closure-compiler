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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This pass looks for properties that are never read and removes them.
 * These can be properties created using "this", or static properties of
 * constructors or interfaces. Explicitly ignored is the possibility that
 * these properties may be indirectly referenced using "for-in" or
 * "Object.keys".  This is the same assumption used with
 * RemoveUnusedPrototypeProperties but is slightly wider in scope.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class RemoveUnusedClassProperties
    implements CompilerPass, NodeTraversal.Callback {
  private final AbstractCompiler compiler;
  private Set<String> used = new HashSet<>();
  private List<Node> candidates = new ArrayList<>();

  private final boolean removeUnusedConstructorProperties;

  RemoveUnusedClassProperties(
      AbstractCompiler compiler, boolean removeUnusedConstructorProperties) {
    this.compiler = compiler;
    used.addAll(compiler.getExternProperties());
    this.removeUnusedConstructorProperties = removeUnusedConstructorProperties;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
    removeUnused();
  }

  private void removeUnused() {
    for (Node n : candidates) {
      if (NodeUtil.isObjectLitKey(n)) {
        String propName = NodeUtil.getObjectLitKeyName(n);
        if (!used.contains(propName)) {
          // If the property definition has side-effect, finding a place for it
          // can be tricky so just leave it in place.
          if (!n.isStringKey()
              || !NodeUtil.mayHaveSideEffects(n.getFirstChild(), compiler)) {
            Node parent = n.getParent();
            parent.removeChild(n);
            compiler.reportChangeToEnclosingScope(parent);
          }
        }
      } else {
        Preconditions.checkState(n.isGetProp(), n);
        String propName = n.getLastChild().getString();
        if (!used.contains(propName)) {

          Node parent = n.getParent();
          Node replacement;
          if (NodeUtil.isAssignmentOp(parent)) {
            Node assign = parent;
            Preconditions.checkState(assign != null
                && NodeUtil.isAssignmentOp(assign)
                && assign.getFirstChild() == n);
            compiler.reportChangeToEnclosingScope(assign);
            // 'this.x = y' to 'y'
            replacement = assign.getLastChild().detachFromParent();
          } else if (parent.isInc() || parent.isDec()) {
            compiler.reportChangeToEnclosingScope(parent);
            replacement = IR.number(0).srcref(parent);
          } else {
            throw new IllegalStateException("unexpected: " + parent);
          }

          // If the property expression is complex preserve that part of the
          // expression.
          if (!n.isQualifiedName()) {
            Node preserved = n.getFirstChild();
            while (preserved.isGetProp()) {
              preserved = preserved.getFirstChild();
            }
            replacement = IR.comma(
                preserved.detachFromParent(),
                replacement)
                .srcref(parent);
          }

          compiler.reportChangeToEnclosingScope(parent);
          parent.getParent().replaceChild(parent, replacement);
        }
      }
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
     switch (n.getType()) {
       case Token.GETPROP: {
         String propName = n.getLastChild().getString();
         if (compiler.getCodingConvention().isExported(propName)
             || isPinningPropertyUse(n)
             || !isRemovablePropertyDefinition(n)) {
           used.add(propName);
         } else {
           // This is a definition of a property but it is only removable
           // if it is defined on "this".
           candidates.add(n);
         }
         break;
       }

       case Token.OBJECTLIT: {
         // Assume any object literal definition might be a reflection on the
         // class property.
         if (!NodeUtil.isObjectDefinePropertiesDefinition(n.getParent())) {
           for (Node c : n.children()) {
             used.add(c.getString());
           }
         }
         break;
       }

       case Token.CALL:
         // Look for properties referenced through "JSCompiler_renameProperty".
         Node target = n.getFirstChild();
         if (n.hasMoreThanOneChild()
             && compiler.getCodingConvention().isPropertyRenameFunction(target.getOriginalQualifiedName())) {
           Node propName = target.getNext();
           if (propName.isString()) {
             used.add(propName.getString());
           }
         } else if (NodeUtil.isObjectDefinePropertiesDefinition(n)) {
           if (n.getChildCount() == 3 && n.getLastChild().isObjectLit()) {
             Node objlit = n.getLastChild();
             for (Node c : objlit.children()) {
               if (!c.isQuotedString()) {
                 candidates.add(c);
               } else {
                 used.add(c.getString());
               }
             }
           }
         }
         break;
     }
  }

  private boolean isRemovablePropertyDefinition(Node n) {
    Preconditions.checkState(n.isGetProp(), n);
    Node target = n.getFirstChild();
    return target.isThis()
        || (this.removeUnusedConstructorProperties && isConstructor(target))
        || (target.isGetProp()
            && target.getLastChild().getString().equals("prototype"));
  }

  private boolean isConstructor(Node n) {
    TypeI type = n.getTypeI();
    return type != null && (type.isConstructor() || type.isInterface());
  }

  /**
   * @return Whether the property is used in a way that prevents its removal.
   */
  private static boolean isPinningPropertyUse(Node n) {
    // Rather than looking for cases that are uses, we assume all references are
    // pinning uses unless they are:
    //  - a simple assignment (x.a = 1)
    //  - a compound assignment or increment (x++, x += 1) whose result is
    //    otherwise unused

    Node parent = n.getParent();
    if (n == parent.getFirstChild()) {
      if (parent.isAssign()) {
        // A simple assignment doesn't pin the property.
        return false;
      } else if (NodeUtil.isAssignmentOp(parent)
            || parent.isInc() || parent.isDec()) {
        // In general, compound assignments are both reads and writes, but
        // if the property is never otherwise read we can consider it simply
        // a write.
        // However if the assign expression is used as part of a larger
        // expression, we must consider it a read. For example:
        //    x = (y.a += 1);
        return NodeUtil.isExpressionResultUsed(parent);
      }
    }
    return true;
  }
}
