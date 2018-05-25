/*
 * Copyright 2016 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * An optimization pass for J2CL-generated code to hoist some constant assignments out clinit method
 * to declaration phase so they could be used by other optimization passes for static evaluation.
 */
public class J2clConstantHoisterPass implements CompilerPass {

  private final AbstractCompiler compiler;

  J2clConstantHoisterPass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    if (!J2clSourceFileChecker.shouldRunJ2clPasses(compiler)) {
      return;
    }

    final Multimap<String, Node> fieldAssignments = ArrayListMultimap.create();
    final Set<Node> hoistableFunctions = new HashSet<>();
    NodeTraversal.traversePostOrder(
        compiler,
        root,
        (NodeTraversal t, Node node, Node parent) -> {
          // TODO(stalcup): don't gather assignments ourselves, switch to a persistent
          // DefinitionUseSiteFinder instead.
          if (parent != null && NodeUtil.isLValue(node)) {
            fieldAssignments.put(node.getQualifiedName(), parent);
          }

          // TODO(stalcup): convert to a persistent index of hoistable functions.
          if (isHoistableFunction(t, node)) {
            hoistableFunctions.add(node);
          }
        });

    for (Collection<Node> assignments : fieldAssignments.asMap().values()) {
      maybeHoistClassField(assignments, hoistableFunctions);
    }
  }

  /**
   * Returns whether the specified rValue is a function which does not receive any variables from
   * its containing scope, and is thus 'hoistable'.
   */
  private static boolean isHoistableFunction(NodeTraversal t, Node node) {
    // TODO(michaelthomas): This could be improved slightly by not assuming that any variable in the
    // outer scope is used in the function.
    return node.isFunction() && t.getScope().getVarCount() == 0;
  }

  private void maybeHoistClassField(
      Collection<Node> assignments, Collection<Node> hoistableFunctions) {
    // The field is only assigned twice:
    if (assignments.size() != 2) {
      return;
    }

    Node first = Iterables.get(assignments, 0);
    Node second = Iterables.get(assignments, 1);

    // One of them is the top level declaration and the other is the assignment in clinit.
    Node topLevelDeclaration = isClassFieldDeclaration(first) ? first : second;
    Node clinitAssignment = isClinitFieldAssignment(first) ? first : second;
    if (!isClassFieldDeclaration(topLevelDeclaration)
        || !isClinitFieldAssignment(clinitAssignment)) {
      return;
    }

    // And it is assigned to a literal value; hence could be used in static eval and safe to move:
    Node assignmentRhs = clinitAssignment.getSecondChild();
    if (!NodeUtil.isLiteralValue(assignmentRhs, true /* includeFunctions */)
        || (assignmentRhs.isFunction() && !hoistableFunctions.contains(assignmentRhs))) {
      return;
    }

    // And the assignment are in the same script:
    if (NodeUtil.getEnclosingScript(clinitAssignment)
        != NodeUtil.getEnclosingScript(topLevelDeclaration)) {
      return;
    }

    // At this point the only case some could observe the declaration value is the when you have
    // cycle between clinits and the field is accessed before initialization; which is almost always
    // a bug and GWT never assumed this state is observable in its optimization, yet nobody
    // complained. So it is safe to upgrade it to a constant.
    hoistConstantLikeField(clinitAssignment, topLevelDeclaration);
  }

  private void hoistConstantLikeField(Node clinitAssignment, Node topLevelDeclaration) {
    Node clinitAssignedValue = clinitAssignment.getSecondChild();
    Node declarationInClass = topLevelDeclaration.getFirstChild();
    Node declarationAssignedValue = declarationInClass.getFirstChild();

    Node clinitChangeScope = NodeUtil.getEnclosingChangeScopeRoot(clinitAssignment);
    // Remove the clinit initialization
    NodeUtil.removeChild(clinitAssignment.getParent(), clinitAssignment);


    clinitAssignedValue.detach();
    compiler.reportChangeToChangeScope(clinitChangeScope);
    if (declarationAssignedValue == null) {
      // Add the value from clinit to the stub declaration
      declarationInClass.addChildToFront(clinitAssignedValue);
      compiler.reportChangeToEnclosingScope(topLevelDeclaration);
    } else if (!declarationAssignedValue.isEquivalentTo(clinitAssignedValue)) {
      checkState(NodeUtil.isLiteralValue(declarationAssignedValue, false /* includeFunctions */));
      // Replace the assignment in declaration with the value from clinit
      declarationInClass.replaceChild(declarationAssignedValue, clinitAssignedValue);
      compiler.reportChangeToEnclosingScope(topLevelDeclaration);
    }
    declarationInClass.putBooleanProp(Node.IS_CONSTANT_VAR, true);
  }

  /**
   * Matches literal value declarations {@code var foo = 3;} or stub declarations like
   * {@code var bar;}.
   */
  private static boolean isClassFieldDeclaration(Node node) {
    return node.getParent().isScript()
        && node.isVar()
        && (!node.getFirstChild().hasChildren()
            || (node.getFirstFirstChild() != null
                && NodeUtil.isLiteralValue(
                    node.getFirstFirstChild(), false /* includeFunctions */)));
  }

  private static boolean isClinitFieldAssignment(Node node) {
    return node.getParent().isExprResult()
        && node.getGrandparent().isBlock()
        && isClinitMethod(node.getGrandparent().getParent());
  }

  // TODO(goktug): Create a utility to share this logic and start using getQualifiedOriginalName.
  private static boolean isClinitMethod(Node fnNode) {
    if (!fnNode.isFunction()) {
      return false;
    }

    String fnName = NodeUtil.getName(fnNode);
    return fnName != null && isClinitMethodName(fnName);
  }

  private static boolean isClinitMethodName(String methodName) {
    return methodName != null
        && (methodName.endsWith("$$0clinit") || methodName.endsWith(".$clinit"));
  }
}
