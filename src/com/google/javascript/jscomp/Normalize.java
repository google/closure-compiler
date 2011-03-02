/*
 * Copyright 2008 The Closure Compiler Authors.
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.BoilerplateRenamer;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Map;
import java.util.Set;

/**
 * The goal with this pass is to simplify the other passes,
 * by making less complex statements.
 *
 * Starting with statements like:
 *   var a = 0, b = foo();
 *
 * Which become:
 *   var a = 0;
 *   var b = foo();
 *
 * The key here is only to break down things that help the other passes
 * and can be put back together in a form that is at least as small when
 * all is said and done.
 *
 * This pass currently does the following:
 * 1) Simplifies the AST by splitting var statements, moving initializiers
 *    out of for loops, and converting whiles to fors.
 * 2) Moves hoisted functions to the top of function scopes.
 * 3) Rewrites unhoisted named function declarations to be var declarations.
 * 4) Makes all variable names globally unique (extern or otherwise) so that
 *    no value is ever shadowed (note: "arguments" may require special
 *    handling).
 * 5) Removes duplicate variable declarations.
 * 6) Marks constants with the IS_CONSTANT_NAME annotation.
 *
 * @author johnlenz@google.com (johnlenz)
 */
// public for ReplaceDebugStringsTest
class Normalize implements CompilerPass {

  private final AbstractCompiler compiler;
  private final boolean assertOnChange;
  private static final boolean CONVERT_WHILE_TO_FOR = true;
  static final boolean MAKE_LOCAL_NAMES_UNIQUE = true;

  public static final DiagnosticType CATCH_BLOCK_VAR_ERROR =
    DiagnosticType.error(
        "JSC_CATCH_BLOCK_VAR_ERROR",
        "The use of scope variable {0} is not allowed within a catch block " +
        "with a catch exception of the same name.");


  Normalize(AbstractCompiler compiler, boolean assertOnChange) {
    this.compiler = compiler;
    this.assertOnChange = assertOnChange;

    // TODO(nicksantos): assertOnChange should only be true if the tree
    // is normalized.
  }

  static Node parseAndNormalizeSyntheticCode(
      AbstractCompiler compiler, String code, String prefix) {
    Node js = compiler.parseSyntheticCode(code);
    NodeTraversal.traverse(compiler, js,
        new Normalize.NormalizeStatements(compiler, false));
    NodeTraversal.traverse(
        compiler, js,
        new MakeDeclaredNamesUnique(
            new BoilerplateRenamer(
                compiler.getUniqueNameIdSupplier(),
                prefix)));
    return js;
  }

  static Node parseAndNormalizeTestCode(
      AbstractCompiler compiler, String code, String prefix) {
    Node js = compiler.parseTestCode(code);
    NodeTraversal.traverse(compiler, js,
        new Normalize.NormalizeStatements(compiler, false));
    NodeTraversal.traverse(
        compiler, js,
        new MakeDeclaredNamesUnique());
    return js;
  }

  private void reportCodeChange(String changeDescription) {
    if (assertOnChange) {
      throw new IllegalStateException(
          "Normalize constraints violated:\n" + changeDescription);
    }
    compiler.reportCodeChange();
  }

  @Override
  public void process(Node externs, Node root) {
    new NodeTraversal(
        compiler, new NormalizeStatements(compiler, assertOnChange))
        .traverseRoots(externs, root);
    if (MAKE_LOCAL_NAMES_UNIQUE) {
      MakeDeclaredNamesUnique renamer = new MakeDeclaredNamesUnique();
      NodeTraversal t = new NodeTraversal(compiler, renamer);
      t.traverseRoots(externs, root);
    }
    // It is important that removeDuplicateDeclarations runs after
    // MakeDeclaredNamesUnique in order for catch block exception names to be
    // handled properly. Specifically, catch block exception names are
    // only valid within the catch block, but our currect Scope logic
    // has no concept of this and includes it in the containing function
    // (or global scope). MakeDeclaredNamesUnique makes the catch exception
    // names unique so that removeDuplicateDeclarations() will properly handle
    // cases where a function scope variable conflict with a exception name:
    //   function f() {
    //      try {throw 0;} catch(e) {e; /* catch scope 'e'*/}
    //      var e = 1; // f scope 'e'
    //   }
    // otherwise 'var e = 1' would be rewritten as 'e = 1'.
    // TODO(johnlenz): Introduce a seperate scope for catch nodes.
    removeDuplicateDeclarations(externs, root);
    new PropagateConstantAnnotationsOverVars(compiler, assertOnChange)
        .process(externs, root);

    if (!compiler.getLifeCycleStage().isNormalized()) {
      compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    }
  }

  /**
   * Propagate constant annotations over the Var graph.
   */
  static class PropagateConstantAnnotationsOverVars
      extends AbstractPostOrderCallback
      implements CompilerPass {
    private final AbstractCompiler compiler;
    private final boolean assertOnChange;

    PropagateConstantAnnotationsOverVars(
        AbstractCompiler compiler, boolean forbidChanges) {
      this.compiler = compiler;
      this.assertOnChange = forbidChanges;
    }

    @Override
    public void process(Node externs, Node root) {
      new NodeTraversal(compiler, this).traverseRoots(externs, root);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Note: Constant properties annotations are not propagated.
      if (n.getType() == Token.NAME) {
        if (n.getString().isEmpty()) {
          return;
        }

        JSDocInfo info = null;
        // Find the JSDocInfo for a top level variable.
        Var var = t.getScope().getVar(n.getString());
        if (var != null) {
          info = var.getJSDocInfo();
        }

        boolean shouldBeConstant =
            (info != null && info.isConstant()) ||
            NodeUtil.isConstantByConvention(
                compiler.getCodingConvention(), n, parent);
        boolean isMarkedConstant = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if (shouldBeConstant && !isMarkedConstant) {
          if (assertOnChange) {
            String name = n.getString();
            throw new IllegalStateException(
                "Unexpected const change.\n" +
                "  name: "+ name + "\n" +
                "  parent:" + n.getParent().toStringTree());
          }
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
      }
    }
  }

  /**
   * Walk the AST tree and verify that constant names are used consistently.
   */
  static class VerifyConstants extends AbstractPostOrderCallback
      implements CompilerPass {

    final private AbstractCompiler compiler;
    final private boolean checkUserDeclarations;

    VerifyConstants(AbstractCompiler compiler, boolean checkUserDeclarations) {
      this.compiler = compiler;
      this.checkUserDeclarations = checkUserDeclarations;
    }

    @Override
    public void process(Node externs, Node root) {
      Node externsAndJs = root.getParent();
      Preconditions.checkState(externsAndJs != null);
      Preconditions.checkState(externsAndJs.hasChild(externs));

      NodeTraversal.traverseRoots(
          compiler, Lists.newArrayList(externs, root), this);
    }

    private Map<String, Boolean> constantMap = Maps.newHashMap();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.NAME) {
        String name = n.getString();
        if (n.getString().isEmpty()) {
          return;
        }

        boolean isConst = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if (checkUserDeclarations) {
          boolean expectedConst = false;
          CodingConvention convention = compiler.getCodingConvention();
          if (NodeUtil.isConstantName(n)
              || NodeUtil.isConstantByConvention(convention, n, parent)) {
            expectedConst = true;
          } else {
            expectedConst = false;

            JSDocInfo info = null;
            Var var = t.getScope().getVar(n.getString());
            if (var != null) {
              info = var.getJSDocInfo();
            }

            if (info != null && info.isConstant()) {
              expectedConst = true;
            } else {
              expectedConst = false;
            }
          }

          if (expectedConst) {
            Preconditions.checkState(expectedConst == isConst,
                "The name " + name + " is not annotated as constant.");
          } else {
            Preconditions.checkState(expectedConst == isConst,
                "The name " + name + " should not be annotated as constant.");
          }
        }

        Boolean value = constantMap.get(name);
        if (value == null) {
          constantMap.put(name, isConst);
        } else {
          Preconditions.checkState(value.booleanValue() == isConst,
              "The name " + name + " is not consistently annotated as " +
              "constant.");
        }
      }
    }
  }

  /**
   * Simplify the AST:
   *   - VAR declarations split, so they represent exactly one child
   *     declaration.
   *   - WHILEs are converted to FORs
   *   - FOR loop are initializers are moved out of the FOR structure
   *   - LABEL node of children other than LABEL, BLOCK, WHILE, FOR, or DO are
   *     moved into a block.
   *   - Add constant annotations based on coding convention.
   */
  static class NormalizeStatements implements Callback {
    private final AbstractCompiler compiler;
    private final boolean assertOnChange;

    NormalizeStatements(AbstractCompiler compiler, boolean assertOnChange) {
      this.compiler = compiler;
      this.assertOnChange = assertOnChange;
    }

    private void reportCodeChange(String changeDescription) {
      if (assertOnChange) {
        throw new IllegalStateException(
            "Normalize constraints violated:\n" + changeDescription);
      }
      compiler.reportCodeChange();
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      doStatementNormalizations(t, n, parent);

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.WHILE:
          if (CONVERT_WHILE_TO_FOR) {
            Node expr = n.getFirstChild();
            n.setType(Token.FOR);
            Node empty = new Node(Token.EMPTY);
            empty.copyInformationFrom(n);
            n.addChildBefore(empty, expr);
            n.addChildAfter(empty.cloneNode(), expr);
            reportCodeChange("WHILE node");
          }
          break;

        case Token.FUNCTION:
          normalizeFunctionDeclaration(n);
          break;

        case Token.NAME:
        case Token.STRING:
        case Token.GET:
        case Token.SET:
          if (!compiler.getLifeCycleStage().isNormalizedObfuscated()) {
            annotateConstantsByConvention(n, parent);
          }
          break;
      }
    }

    /**
     * Mark names and properties that are constants by convention.
     */
    private void annotateConstantsByConvention(Node n, Node parent) {
      Preconditions.checkState(
          n.getType() == Token.NAME
          || n.getType() == Token.STRING
          || n.getType() == Token.GET
          || n.getType() == Token.SET);

      // There are only two cases where a string token
      // may be a variable reference: The right side of a GETPROP
      // or an OBJECTLIT key.
      boolean isObjLitKey = NodeUtil.isObjectLitKey(n, parent);
      boolean isProperty = isObjLitKey ||
          (parent.getType() == Token.GETPROP &&
           parent.getLastChild() == n);
      if (n.getType() == Token.NAME || isProperty) {
        boolean isMarkedConstant = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if (!isMarkedConstant &&
            NodeUtil.isConstantByConvention(
                compiler.getCodingConvention(), n, parent)) {
          if (assertOnChange) {
            String name = n.getString();
            throw new IllegalStateException(
                "Unexpected const change.\n" +
                "  name: "+ name + "\n" +
                "  parent:" + n.getParent().toStringTree());
          }
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
      }
    }

    /**
     * Rewrite named unhoisted functions declarations to a known
     * consistent behavior so we don't to different logic paths for the same
     * code. From:
     *    function f() {}
     * to:
     *    var f = function () {};
     */
    private void normalizeFunctionDeclaration(Node n) {
      Preconditions.checkState(n.getType() == Token.FUNCTION);
      if (!NodeUtil.isFunctionExpression(n)
          && !NodeUtil.isHoistedFunctionDeclaration(n)) {
        rewriteFunctionDeclaration(n);
      }
    }

    /**
     * Rewrite the function declaration from:
     *   function x() {}
     *   FUNCTION
     *     NAME
     *     LP
     *     BLOCK
     * to:
     *   var x = function() {};
     *   VAR
     *     NAME
     *       FUNCTION
     *         NAME (w/ empty string)
     *         LP
     *         BLOCK
     */
    private void rewriteFunctionDeclaration(Node n) {
      // Prepare a spot for the function.
      Node oldNameNode = n.getFirstChild();
      Node fnNameNode = oldNameNode.cloneNode();
      Node var = new Node(Token.VAR, fnNameNode, n.getLineno(), n.getCharno());
      var.copyInformationFrom(n);

      // Prepare the function
      oldNameNode.setString("");

      // Move the function
      Node parent = n.getParent();
      parent.replaceChild(n, var);
      fnNameNode.addChildToFront(n);

      reportCodeChange("Function declaration");
    }

    /**
     * Do normalizations that introduce new siblings or parents.
     */
    private void doStatementNormalizations(
        NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.LABEL) {
        normalizeLabels(n);
      }

      // Only inspect the children of SCRIPTs, BLOCKs and LABELs, as all these
      // are the only legal place for VARs and FOR statements.
      if (NodeUtil.isStatementBlock(n) || n.getType() == Token.LABEL) {
        extractForInitializer(n, null, null);
      }

      // Only inspect the children of SCRIPTs, BLOCKs, as all these
      // are the only legal place for VARs.
      if (NodeUtil.isStatementBlock(n)) {
        splitVarDeclarations(n);
      }

      if (n.getType() == Token.FUNCTION) {
        moveNamedFunctions(n.getLastChild());
      }
    }

    // TODO(johnlenz): Move this to NodeTypeNormalizer once the unit tests are
    // fixed.
    /**
     * Limit the number of special cases where LABELs need to be handled. Only
     * BLOCK and loops are allowed to be labeled.  Loop labels must remain in
     * place as the named continues are not allowed for labeled blocks.
     */
    private void normalizeLabels(Node n) {
      Preconditions.checkArgument(n.getType() == Token.LABEL);

      Node last = n.getLastChild();
      switch (last.getType()) {
        case Token.LABEL:
        case Token.BLOCK:
        case Token.FOR:
        case Token.WHILE:
        case Token.DO:
          return;
        default:
          Node block = new Node(Token.BLOCK);
          block.copyInformationFrom(last);
          n.replaceChild(last, block);
          block.addChildToFront(last);
          reportCodeChange("LABEL normalization");
          return;
      }
    }

    /**
     * Bring the initializers out of FOR loops.  These need to be placed
     * before any associated LABEL nodes. This needs to be done from the top
     * level label first so this is called as a pre-order callback (from
     * shouldTraverse).
     *
     * @param n The node to inspect.
     * @param before The node to insert the initializer before.
     * @param beforeParent The parent of the node before which the initializer
     *     will be inserted.
     */
    private void extractForInitializer(
        Node n, Node before, Node beforeParent) {

      for (Node next, c = n.getFirstChild(); c != null; c = next) {
        next = c.getNext();
        Node insertBefore = (before == null) ? c : before;
        Node insertBeforeParent = (before == null) ? n : beforeParent;
        switch (c.getType()) {
          case Token.LABEL:
            extractForInitializer(c, insertBefore, insertBeforeParent);
            break;
          case Token.FOR:
            if (NodeUtil.isForIn(c)) {
              Node first = c.getFirstChild();
              if (first.getType() == Token.VAR) {
                // Transform:
                //    for (var a in b) {}
                // to:
                //    var a; for (a in b) {};
                Node newStatement = first.cloneTree();
                Node name = first.removeFirstChild();
                first.getParent().replaceChild(first, name);
                insertBeforeParent.addChildBefore(newStatement, insertBefore);
                reportCodeChange("FOR-IN var declaration");
              }
            } else if (c.getFirstChild().getType() != Token.EMPTY) {
              Node init = c.getFirstChild();
              Node empty = new Node(Token.EMPTY);
              empty.copyInformationFrom(c);
              c.replaceChild(init, empty);

              Node newStatement;
              // Only VAR statements, and expressions are allowed,
              // but are handled differently.
              if (init.getType() == Token.VAR) {
                newStatement = init;
              } else {
                newStatement = NodeUtil.newExpr(init);
              }

              insertBeforeParent.addChildBefore(newStatement, insertBefore);
              reportCodeChange("FOR initializer");
            }
            break;
        }
      }
    }

    /**
     * Split a var node such as:
     *   var a, b;
     * into individual statements:
     *   var a;
     *   var b;
     * @param n The whose children we should inspect.
     */
    private void splitVarDeclarations(Node n) {
      for (Node next, c = n.getFirstChild(); c != null; c = next) {
        next = c.getNext();
        if (c.getType() == Token.VAR) {
          if (assertOnChange && !c.hasChildren()) {
            throw new IllegalStateException("Empty VAR node.");
          }

          while (c.getFirstChild() != c.getLastChild()) {
            Node name = c.getFirstChild();
            c.removeChild(name);
            Node newVar = new Node(
                Token.VAR, name, n.getLineno(), n.getCharno());
            n.addChildBefore(newVar, c);
            reportCodeChange("VAR with multiple children");
          }
        }
      }
    }

    /**
     * Move all the functions that are valid at the execution of the first
     * statement of the function to the beginning of the function definition.
     */
    private void moveNamedFunctions(Node functionBody) {
      Preconditions.checkState(
          functionBody.getParent().getType() == Token.FUNCTION);
      Node previous = null;
      Node current = functionBody.getFirstChild();
      // Skip any declarations at the beginning of the function body, they
      // are already in the right place.
      while (current != null && NodeUtil.isFunctionDeclaration(current)) {
        previous = current;
        current = current.getNext();
      }

      // Find any remaining declarations and move them.
      Node insertAfter = previous;
      while (current != null) {
        // Save off the next node as the current node maybe removed.
        Node next = current.getNext();
        if (NodeUtil.isFunctionDeclaration(current)) {
          // Remove the declaration from the body.
          Preconditions.checkNotNull(previous);
          functionBody.removeChildAfter(previous);

          // Readd the function at the top of the function body (after any
          // previous declarations).
          insertAfter = addToFront(functionBody, current, insertAfter);
          reportCodeChange("Move function declaration not at top of function");
        } else {
          // Update the previous only if the current node hasn't been moved.
          previous = current;
        }
        current = next;
      }
    }

    /**
     * @param after The child node to insert the newChild after, or null if
     *     newChild should be added to the front of parent's child list.
     * @return The inserted child node.
     */
    private Node addToFront(Node parent, Node newChild, Node after) {
      if (after == null) {
        parent.addChildToFront(newChild);
      } else {
        parent.addChildAfter(newChild, after);
      }
      return newChild;
    }
  }

  /**
   * Remove duplicate VAR declarations.
   */
  private void removeDuplicateDeclarations(Node externs, Node root) {
    Callback tickler = new ScopeTicklingCallback();
    ScopeCreator scopeCreator =  new SyntacticScopeCreator(
        compiler, new DuplicateDeclarationHandler());
    NodeTraversal t = new NodeTraversal(compiler, tickler, scopeCreator);
    t.traverseRoots(externs, root);
  }

  /**
   * ScopeCreator duplicate declaration handler.
   */
  private final class DuplicateDeclarationHandler implements
      SyntacticScopeCreator.RedeclarationHandler {

    private Set<Var> hasOkDuplicateDeclaration = Sets.newHashSet();

    /**
     * Remove duplicate VAR declarations encountered discovered during
     * scope creation.
     */
    @Override
    public void onRedeclaration(
        Scope s, String name, Node n, CompilerInput input) {
      Preconditions.checkState(n.getType() == Token.NAME);
      Node parent = n.getParent();
      Var v = s.getVar(name);

      if (v != null && s.isGlobal()) {
        // We allow variables to be duplicate declared if one
        // declaration appears in source and the other in externs.
        // This deals with issues where a browser built-in is declared
        // in one browser but not in another.
        if (v.isExtern() && !input.isExtern()) {
          if (hasOkDuplicateDeclaration.add(v)) {
            return;
          }
        }
      }

      // If name is "arguments", Var maybe null.
      if (v != null && v.getParentNode().getType() == Token.CATCH) {
        // Redeclaration of a catch expression variable is hard to model
        // without support for "with" expressions.
        // The EcmaScript spec (section 12.14), declares that a catch
        // "catch (e) {}" is handled like "with ({'e': e}) {}" so that
        // "var e" would refer to the scope variable, but any following
        // reference would still refer to "e" of the catch expression.
        // Until we have support for this disallow it.
        // Currently the Scope object adds the catch expression to the
        // function scope, which is technically not true but a good
        // approximation for most uses.

        // TODO(johnlenz): Consider improving how scope handles catch
        // expression.

        // Use the name of the var before it was made unique.
        name = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName(
            name);
        compiler.report(
            JSError.make(
                input.getName(), n,
                CATCH_BLOCK_VAR_ERROR, name));
      } else if (v != null && parent.getType() == Token.FUNCTION) {
        if (v.getParentNode().getType() == Token.VAR) {
          s.undeclare(v);
          s.declare(name, n, n.getJSType(), v.input);
          replaceVarWithAssignment(v.getNameNode(), v.getParentNode(),
              v.getParentNode().getParent());
        }
      } else if (parent.getType() == Token.VAR) {
        Preconditions.checkState(parent.hasOneChild());

        replaceVarWithAssignment(n, parent, parent.getParent());
      }
    }

    /**
     * Remove the parent VAR. There are three cases that need to be handled:
     *   1) "var a = b;" which is replaced with "a = b"
     *   2) "label:var a;" which is replaced with "label:;". Ideally, the
     *      label itself would be removed but that is not possible in the
     *      context in which "onRedeclaration" is called.
     *   3) "for (var a in b) ..." which is replaced with "for (a in b)..."
     *      Cases we don't need to handle are VARs with multiple children,
     *      which have already been split into separate declarations, so there
     *      is no need to handle that here, and "for (var a;;);", which has
     *      been moved out of the loop.
     *      The result of this is that in each case the parent node is replaced
     *      which is generally dangerous in a traversal but is fine here with
     *      the scope creator, as the next node of interest is the parent's
     *      next sibling.
     */
    private void replaceVarWithAssignment(Node n, Node parent, Node gramps) {
      if (n.hasChildren()) {
        // The  *  is being initialize, preserve the new value.
        parent.removeChild(n);
        // Convert "var name = value" to "name = value"
        Node value = n.getFirstChild();
        n.removeChild(value);
        Node replacement = new Node(Token.ASSIGN, n, value);
        replacement.copyInformationFrom(parent);
        gramps.replaceChild(parent, NodeUtil.newExpr(replacement));
      } else {
        // It is an empty reference remove it.
        if (NodeUtil.isStatementBlock(gramps)) {
          gramps.removeChild(parent);
        } else if (gramps.getType() == Token.FOR) {
          // This is the "for (var a in b)..." case.  We don't need to worry
          // about initializers in "for (var a;;)..." as those are moved out
          // as part of the other normalizations.
          parent.removeChild(n);
          gramps.replaceChild(parent, n);
        } else {
          Preconditions.checkState(gramps.getType() == Token.LABEL);
          // We should never get here. LABELs with a single VAR statement should
          // already have been normalized to have a BLOCK.
          throw new IllegalStateException("Unexpected LABEL");
        }
      }
      reportCodeChange("Duplicate VAR declaration");
    }
  }

  /**
   * A simple class that causes scope to be created.
   */
  private final class ScopeTicklingCallback
      implements NodeTraversal.ScopedCallback {
    @Override
    public void enterScope(NodeTraversal t) {
      // Cause the scope to be created, which will cause duplicate
      // to be found.
      t.getScope();
    }

    @Override
    public void exitScope(NodeTraversal t) {
      // Nothing to do.
    }

    @Override
    public boolean shouldTraverse(
        NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Nothing to do.
    }
  }
}
