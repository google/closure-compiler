/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * Rewrites "let"s and "const"s as "var"s, renaming declarations when necessary.
 *
 * @author moz@google.com (Michael Zhou)
 */
public class Es6RewriteLetConst extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;
  private int uId;
  private final Map<Node, Map<String, String>> renameMap = new HashMap<>();

  public Es6RewriteLetConst(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  private String newUniqueName(String name) {
    return name + "$" + uId++;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    // TODO(moz): Add support for renaming classes.
    if (!n.isLet() && !n.isConst()) {
      return;
    }

    Scope scope = t.getScope();
    Node nameNode = n.getFirstChild();
    if (!nameNode.hasChildren()) {
      nameNode.addChildToFront(IR.name("undefined")
          .useSourceInfoIfMissingFrom(nameNode));
    }

    String oldName = nameNode.getString();
    n.setType(Token.VAR);
    Scope hoistScope = scope.getClosestHoistScope();
    if (scope != hoistScope) {
      boolean doRename = hoistScope.isDeclared(oldName, true);
      String newName = doRename ? newUniqueName(oldName) : oldName;
      Var oldVar = scope.getVar(oldName);
      scope.undeclare(oldVar);
      hoistScope.declare(newName, nameNode, null, oldVar.input);
      if (doRename) {
        nameNode.setString(newName);
        Node scopeRoot = scope.getRootNode();
        if (!renameMap.containsKey(scopeRoot)) {
          renameMap.put(scopeRoot, new HashMap<String, String>());
        }
        renameMap.get(scopeRoot).put(oldName, newName);
      }
    }
    t.getCompiler().reportCodeChange();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(
        compiler, Lists.newArrayList(externs, root), new DetectLoopClosures());
    NodeTraversal.traverseRoots(compiler, Lists.newArrayList(externs, root), this);
    NodeTraversal.traverseRoots(
        compiler, Lists.newArrayList(externs, root), this.new RenameReferences());
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this.new DetectLoopClosures());
    NodeTraversal.traverse(compiler, scriptRoot, this);
    NodeTraversal.traverse(compiler, scriptRoot, this.new RenameReferences());
  }

  /**
   * Renames references when necessary.
   */
  class RenameReferences extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isReferenceName(n)) {
        return;
      }
      Scope scope = t.getScope();
      String oldName = n.getString();
      Scope current = scope;
      if (current.isDeclared(oldName, false)) {
        return;
      }
      boolean doRename = false;
      String newName = null;
      while (current != null) {
        Map<String, String> renamesAtCurrentLevel = renameMap.get(current.getRootNode());
        if (renamesAtCurrentLevel != null
            && renamesAtCurrentLevel.containsKey(oldName)) {
          doRename = true;
          newName = renamesAtCurrentLevel.get(oldName);
          break;
        } else {
          current = current.getParent();
        }
      }
      if (doRename) {
        n.setString(newName);
        t.getCompiler().reportCodeChange();
      }
    }
  }

  /**
   * Detects loop closures.
   */
  class DetectLoopClosures extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!NodeUtil.isReferenceName(n)) {
        return;
      }
      String name = n.getString();
      Scope referencedIn = t.getScope();
      Var var = referencedIn.getVar(name);
      if (var == null) {
        return;
      }

      // Traverse nodes up from let/const declaration:
      // If we hit a function or the root before a loop - Not a loop closure.
      // if we hit a loop first - maybe loop closure.
      Scope declaredIn = var.getScope();
      for (Scope s = declaredIn;
          !(NodeUtil.isLoopStructure(s.getRootNode())
              || (s.getRootNode().getParent() != null
                  && NodeUtil.isLoopStructure(s.getRootNode().getParent())));
          s = s.getParent()) {
        if (s.isFunctionBlockScope() || s.isGlobal()) {
          return;
        }
      }

      // Traverse scopes from reference scope to declaration scope.
      // If we hit a function, report loop closure detected.
      for (Scope s = referencedIn; s != declaredIn; s = s.getParent()) {
        if (s.isFunctionBlockScope()) {
          t.report(s.getRootNode(),
              Es6ToEs3Converter.CANNOT_CONVERT_YET, "Loop closure detected");
        }
      }
    }
  }

}
