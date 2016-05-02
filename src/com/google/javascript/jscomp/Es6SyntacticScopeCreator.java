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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.SyntacticScopeCreator.DefaultRedeclarationHandler;
import com.google.javascript.jscomp.SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * <p>The syntactic scope creator scans the parse tree to create a Scope object
 * containing all the variable declarations in that scope. This class adds supported
 * for block-level scopes introduced in ECMAScript 6.</p>
 *
 * <p>This implementation is not thread-safe.</p>
 *
 * @author moz@google.com (Michael Zhou)
 */
class Es6SyntacticScopeCreator implements ScopeCreator {
  private final AbstractCompiler compiler;
  private Scope scope;
  private InputId inputId;
  private final RedeclarationHandler redeclarationHandler;

  // The arguments variable is special, in that it's declared for every function,
  // but not explicitly declared.
  private static final String ARGUMENTS = "arguments";

  Es6SyntacticScopeCreator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.redeclarationHandler = new DefaultRedeclarationHandler();
  }

  Es6SyntacticScopeCreator(
      AbstractCompiler compiler, RedeclarationHandler redeclarationHandler) {
    this.compiler = compiler;
    this.redeclarationHandler = redeclarationHandler;
  }

  @Override
  public Scope createScope(Node n, Scope parent) {
    inputId = null;
    if (parent == null) {
      scope = Scope.createGlobalScope(n);
    } else {
      scope = new Scope(parent, n);
    }

    scanRoot(n);

    inputId = null;
    Scope returnedScope = scope;
    scope = null;
    return returnedScope;
  }

  private void scanRoot(Node n) {
    if (n.isFunction()) {
      if (inputId == null) {
        inputId = NodeUtil.getInputId(n);
        // TODO(johnlenz): inputId maybe null if the FUNCTION node is detached
        // from the AST.
        // Is it meaningful to build a scope for detached FUNCTION node?
      }

      final Node fnNameNode = n.getFirstChild();
      final Node args = fnNameNode.getNext();

      // Bleed the function name into the scope, if it hasn't
      // been declared in the outer scope.
      String fnName = fnNameNode.getString();
      if (!fnName.isEmpty() && NodeUtil.isFunctionExpression(n)) {
        declareVar(fnNameNode);
      }

      // Args: Declare function variables
      Preconditions.checkState(args.isParamList());
      declareLHS(scope, args);
      // Since we create a separate scope for body, stop scanning here

    } else if (n.isClass()) {
      if (scope.getParent() != null) {
        inputId = NodeUtil.getInputId(n);
      }

      final Node classNameNode = n.getFirstChild();
      // Bleed the class name into the scope, if it hasn't
      // been declared in the outer scope.
      if (!classNameNode.isEmpty()) {
        if (NodeUtil.isClassExpression(n)) {
          declareVar(classNameNode);
        }
      }
    } else if (n.isBlock() || n.isFor() || n.isForOf() || n.isSwitch()) {
      if (scope.getParent() != null) {
        inputId = NodeUtil.getInputId(n);
      }
      scanVars(n);
    } else {
      // n is the global SCRIPT node
      Preconditions.checkState(scope.getParent() == null);
      scanVars(n);
    }
  }

  private void declareLHS(Scope declarationScope, Node n) {
    for (Node lhs : NodeUtil.getLhsNodesOfDeclaration(n)) {
      declareVar(declarationScope, lhs);
    }
  }

  /**
    * Scans and gather variables declarations under a Node
    */
  private void scanVars(Node n) {
    switch (n.getType()) {
      case Token.VAR:
        declareLHS(scope.getClosestHoistScope(), n);
        return;

      case Token.LET:
      case Token.CONST:
        // Only declare when scope is the current lexical scope
        if (!isNodeAtCurrentLexicalScope(n)) {
          return;
        }
        declareLHS(scope, n);
        return;

      case Token.FUNCTION:
        if (NodeUtil.isFunctionExpression(n) || !isNodeAtCurrentLexicalScope(n)) {
          return;
        }

        String fnName = n.getFirstChild().getString();
        if (fnName.isEmpty()) {
          // This is invalid, but allow it so the checks can catch it.
          return;
        }
        declareVar(n.getFirstChild());
        return;   // should not examine function's children

      case Token.CLASS:
        if (NodeUtil.isClassExpression(n) || !isNodeAtCurrentLexicalScope(n)) {
          return;
        }
        String className = n.getFirstChild().getString();
        if (className.isEmpty()) {
          // This is invalid, but allow it so the checks can catch it.
          return;
        }
        declareVar(n.getFirstChild());
        return;  // should not examine class's children

      case Token.CATCH:
        Preconditions.checkState(n.getChildCount() == 2, n);
        // the first child is the catch var and the second child
        // is the code block
        if (isNodeAtCurrentLexicalScope(n)) {
          declareLHS(scope, n);
        }
        // A new scope is not created for this BLOCK because there is a scope
        // created for the BLOCK above the CATCH
        final Node block = n.getSecondChild();
        scanVars(block);
        return;  // only one child to scan

      case Token.SCRIPT:
        inputId = n.getInputId();
        Preconditions.checkNotNull(inputId);
        break;
    }

    // Variables can only occur in statement-level nodes, so
    // we only need to traverse children in a couple special cases.
    if (NodeUtil.isControlStructure(n) || NodeUtil.isStatementBlock(n)) {
      for (Node child = n.getFirstChild();
           child != null;) {
        Node next = child.getNext();
        scanVars(child);
        child = next;
      }
    }
  }

  private void declareVar(Node n) {
    declareVar(scope, n);
  }

  /**
   * Declares a variable.
   *
   * @param s The scope to declare the variable in.
   * @param n The node corresponding to the variable name.
   */
  private void declareVar(Scope s, Node n) {
    Preconditions.checkState(n.isName() || n.isStringKey(),
        "Invalid node for declareVar: %s", n);

    String name = n.getString();
    // Because of how we scan the variables, it is possible to encounter
    // the same var declared name node twice. Bail out in this case.
    if (s.getVar(name) != null && s.getVar(name).getNode() == n) {
      return;
    }

    CompilerInput input = compiler.getInput(inputId);
    if (s.isDeclared(name, false) || (s.isLocal() && name.equals(ARGUMENTS))) {
      redeclarationHandler.onRedeclaration(s, name, n, input);
    } else {
      s.declare(name, n, input);
    }
  }

  /**
   * Determines whether the name should be declared at current lexical scope.
   * Assume the parent node is a BLOCK, FOR, FOR_OF, SCRIPT or LABEL.
   *
   * @param n The declaration node to be checked
   * @return whether the name should be declared at current lexical scope
   */
  private boolean isNodeAtCurrentLexicalScope(Node n) {
    Node parent = n.getParent();
    Node grandparent = parent.getParent();
    Preconditions.checkState(parent.isBlock() || parent.isFor() || parent.isForOf()
        || parent.isScript() || parent.isLabel(), parent);

    if (parent.isSyntheticBlock()
        && grandparent != null && (grandparent.isCase() || grandparent.isDefaultCase())) {
      Node switchNode = grandparent.getParent();
      return scope.getRootNode() == switchNode;
    }

    if (parent == scope.getRootNode() || parent.isScript()
        || (grandparent.isCatch()
            && parent.getGrandparent() == scope.getRootNode())) {
      return true;
    }

    while (parent.isLabel()) {
      if (parent.getParent() == scope.getRootNode()) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  @Override
  public boolean hasBlockScope() {
    return true;
  }
}
