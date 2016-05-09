/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This pass looks for properties that are never read.
 * These can be properties created using "this", or static properties of
 * constructors or interfaces. Explicitly ignored is the possibility that
 * these properties may be indirectly referenced using "for-in" or
 * "Object.keys".
 *
 * This class is based on RemoveUnusedClassProperties, some effort should
 * be made to extract the common pieces.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class CheckUnusedPrivateProperties
    implements HotSwapCompilerPass, NodeTraversal.Callback {

  static final DiagnosticType UNUSED_PRIVATE_PROPERTY =
      DiagnosticType.disabled(
          "JSC_UNUSED_PRIVATE_PROPERTY", "Private property {0} is never read");

  private final AbstractCompiler compiler;
  private Set<String> used = new HashSet<>();
  private List<Node> candidates = new ArrayList<>();

  CheckUnusedPrivateProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  private void reportUnused(NodeTraversal t) {
    for (Node n : candidates) {
      String propName = getPropName(n);
      if (!used.contains(propName)) {
        t.report(n, UNUSED_PRIVATE_PROPERTY, propName);
      }
    }
  }

  private String getPropName(Node n) {
    switch (n.getType()) {
      case Token.GETPROP:
        return n.getLastChild().getString();
      case Token.MEMBER_FUNCTION_DEF:
        return n.getString();
    }
    throw new RuntimeException("Unexpected node type: " + n);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      used.clear();
      candidates.clear();
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
     switch (n.getType()) {
       case Token.SCRIPT: {
         // exiting the script, report any privates not used in the file.
         reportUnused(t);
         break;
       }

       case Token.GETPROP: {
         String propName = n.getLastChild().getString();
         if (compiler.getCodingConvention().isExported(propName)
             || isPinningPropertyUse(n)
             || !isCandidatePropertyDefinition(n)) {
           used.add(propName);
         } else {
           // Only consider "private" properties.
           if (isCheckablePrivatePropDecl(n)) {
             candidates.add(n);
           }
         }
         break;
       }

       case Token.MEMBER_FUNCTION_DEF: {
         // Only consider "private" methods.
         if (isCheckablePrivatePropDecl(n)) {
           candidates.add(n);
         }
         break;
       }

       case Token.OBJECTLIT: {
         // Assume any object literal definition might be a reflection on the
         // class property.
         for (Node c : n.children()) {
           used.add(c.getString());
         }
         break;
       }

       case Token.CALL:
         // Look for properties referenced through "JSCompiler_propertyRename".
         Node target = n.getFirstChild();
         if (n.hasMoreThanOneChild()
             && target.isName()
             && target.getString().equals(NodeUtil.JSC_PROPERTY_NAME_FN)) {
           Node propName = target.getNext();
           if (propName.isString()) {
             used.add(propName.getString());
           }
         }
         break;
     }
  }

  private boolean isPrivatePropDecl(Node n) {
    // TODO(johnlenz): add support private by convention property definitions without JSDoc.
    // TODO(johnlenz): add support for checking protected properties in final classes
    // TODO(johnlenz): add support for checking "package" properties when checking an entire
    // library.
    JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
    return (info != null && info.getVisibility() == Visibility.PRIVATE);
  }

  private boolean isCheckablePrivatePropDecl(Node n) {
    // TODO(tbreisacher): Look for uses of the typedef/interface in type expressions; warn if there
    // are no uses.
    JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
    return isPrivatePropDecl(n) && !info.hasTypedefType() && !info.isInterface();
  }

  private boolean isCandidatePropertyDefinition(Node n) {
    Preconditions.checkState(n.isGetProp(), n);
    Node target = n.getFirstChild();
    return target.isThis()
        || (isConstructor(target))
        || (target.isGetProp()
            && target.getLastChild().getString().equals("prototype"));
  }

  private boolean isConstructor(Node n) {
    // If type checking is enabled (not just a per-file lint check),
    // we can check constructor properties too. But it isn't required.
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
      if (parent.isExprResult()) {
        // A stub declaration "this.x;" isn't a pinning use.
        return false;
      } else if (parent.isAssign()) {
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
