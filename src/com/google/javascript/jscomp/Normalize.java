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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.BoilerplateRenamer;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The goal with this pass is to simplify the other passes, by making less complex statements.
 *
 * <p>Starting with statements like: {@code var a = 0, b = foo();}
 *
 * <p>Which become: {@code var a = 0; var b = foo();}
 *
 * <p>The key here is only to break down things that help the other passes and can be put back
 * together in a form that is at least as small when all is said and done.
 *
 * <p>This pass currently does the following:
 *
 * <ol>
 *   <li>Simplifies the AST by splitting var/let/const statements, moving initializers out of for
 *       loops, and converting whiles to fors.
 *   <li>Moves hoisted functions to the top of function scopes.
 *   <li>Rewrites unhoisted named function declarations to be var declarations.
 *   <li>Makes all variable names globally unique (extern or otherwise) so that no value is ever
 *       shadowed (note: "arguments" may require special handling).
 *   <li>Removes duplicate variable declarations.
 *   <li>Marks constants with the IS_CONSTANT_NAME annotation.
 *   <li>Finds properties marked @expose, and rewrites them in [] notation.
 *   <li>Rewrite body of arrow function as a block.
 *   <li>Take var statements out from for-loop initializer.
 *       This: for(var a = 0;a<0;a++) {} becomes: var a = 0; for(var a;a<0;a++) {}
 * </ol>
 *
 * @author johnlenz@google.com (johnlenz)
 */
class Normalize implements CompilerPass {

  private final AbstractCompiler compiler;
  private final boolean assertOnChange;

  Normalize(AbstractCompiler compiler, boolean assertOnChange) {
    this.compiler = compiler;
    this.assertOnChange = assertOnChange;

    // TODO(nicksantos): assertOnChange should only be true if the tree
    // is normalized.
  }

  static void normalizeSyntheticCode(
      AbstractCompiler compiler, Node js, String prefix) {
    NodeTraversal.traverse(compiler, js,
        new Normalize.NormalizeStatements(compiler, false));
    NodeTraversal.traverse(
        compiler,
        js,
        new MakeDeclaredNamesUnique(
            new BoilerplateRenamer(
                compiler.getCodingConvention(),
                compiler.getUniqueNameIdSupplier(),
                prefix)));
  }

  static Node parseAndNormalizeTestCode(
      AbstractCompiler compiler, String code) {
    Node js = compiler.parseTestCode(code);
    NodeTraversal.traverse(compiler, js,
        new Normalize.NormalizeStatements(compiler, false));
    return js;
  }

  private void reportCodeChange(String changeDescription, Node n) {
    if (assertOnChange) {
      throw new IllegalStateException(
          "Normalize constraints violated:\n" + changeDescription);
    }
    compiler.reportChangeToEnclosingScope(n);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new RemoveEmptyClassMembers());
    NodeTraversal.traverseRoots(
        compiler, new NormalizeStatements(compiler, assertOnChange), externs, root);
    removeDuplicateDeclarations(externs, root);
    MakeDeclaredNamesUnique renamer = new MakeDeclaredNamesUnique();
    NodeTraversal.traverseRoots(compiler, renamer, externs, root);
    new PropagateConstantAnnotationsOverVars(compiler, assertOnChange)
        .process(externs, root);

    FindExposeAnnotations findExposeAnnotations = new FindExposeAnnotations();
    NodeTraversal.traverse(compiler, root, findExposeAnnotations);
    if (!findExposeAnnotations.exposedProperties.isEmpty()) {
      NodeTraversal.traverse(compiler, root,
          new RewriteExposedProperties(
              findExposeAnnotations.exposedProperties));
    }

    if (!compiler.getLifeCycleStage().isNormalized()) {
      compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    }
  }

  private class RemoveEmptyClassMembers extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isEmpty() && parent.isClassMembers()) {
        reportCodeChange("empty member in class", n);
        n.detach();
      }
    }
  }

  /**
   * Find all the @expose annotations.
   */
  private static class FindExposeAnnotations extends AbstractPostOrderCallback {
    private final Set<String> exposedProperties = new HashSet<>();

    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (NodeUtil.isExprAssign(n)) {
        Node assign = n.getFirstChild();
        Node lhs = assign.getFirstChild();
        if (lhs.isGetProp() && isMarkedExpose(assign)) {
          exposedProperties.add(lhs.getLastChild().getString());
        }
      } else if (n.isStringKey() && isMarkedExpose(n)) {
        exposedProperties.add(n.getString());
      } else if (n.isGetProp() && n.getParent().isExprResult()
                  && isMarkedExpose(n)) {
        exposedProperties.add(n.getLastChild().getString());
      }
    }

    private static boolean isMarkedExpose(Node n) {
      JSDocInfo info = n.getJSDocInfo();
      return info != null && info.isExpose();
    }
  }

  /**
   * Rewrite all exposed properties in [] form.
   */
  private class RewriteExposedProperties
      extends AbstractPostOrderCallback {
    private final Set<String> exposedProperties;

    RewriteExposedProperties(Set<String> exposedProperties) {
      this.exposedProperties = exposedProperties;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isGetProp()) {
        String propName = n.getLastChild().getString();
        if (exposedProperties.contains(propName)) {
          Node obj = n.removeFirstChild();
          Node prop = n.removeFirstChild();
          compiler.reportChangeToEnclosingScope(n);
          n.replaceWith(IR.getelem(obj, prop));
        }
      } else if (n.isStringKey()) {
        String propName = n.getString();
        if (exposedProperties.contains(propName)) {
          if (!n.isQuotedString()) {
            compiler.reportChangeToEnclosingScope(n);
            n.setQuotedString();
          }
        }
      }
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
      NodeTraversal.traverseRoots(compiler, this, externs, root);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Note: Constant properties annotations are not propagated.
      if (n.isName() || n.isStringKey()) {
        if (n.getString().isEmpty()) {
          return;
        }

        JSDocInfo info = null;
        // Find the JSDocInfo for a top-level variable.
        Var var = t.getScope().getVar(n.getString());
        if (var != null) {
          info = var.getJSDocInfo();
        }

        boolean shouldBeConstant =
            (info != null && info.isConstant())
            || NodeUtil.isConstantByConvention(compiler.getCodingConvention(), n);
        boolean isMarkedConstant = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if (shouldBeConstant && !isMarkedConstant) {
          if (assertOnChange) {
            String name = n.getString();
            throw new IllegalStateException(
                "Unexpected const change.\n"
                + "  name: " + name + "\n"
                + "  parent:" + n.getParent().toStringTree());
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

    private final AbstractCompiler compiler;
    private final boolean checkUserDeclarations;

    VerifyConstants(AbstractCompiler compiler, boolean checkUserDeclarations) {
      this.compiler = compiler;
      this.checkUserDeclarations = checkUserDeclarations;
    }

    @Override
    public void process(Node externs, Node root) {
      Node externsAndJs = root.getParent();
      checkState(externsAndJs != null);
      checkState(externsAndJs.hasChild(externs));
      NodeTraversal.traverseRoots(compiler, this, externs, root);
    }

    private final Map<String, Boolean> constantMap = new HashMap<>();

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        String name = n.getString();
        if (n.getString().isEmpty()) {
          return;
        }

        boolean isConst = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if (checkUserDeclarations) {
          boolean expectedConst = false;
          CodingConvention convention = compiler.getCodingConvention();
          if (NodeUtil.isConstantName(n)
              || NodeUtil.isConstantByConvention(convention, n)) {
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
                "The name %s is not annotated as constant.", name);
          } else {
            Preconditions.checkState(expectedConst == isConst,
                "The name %s should not be annotated as constant.", name);
          }
        }

        Boolean value = constantMap.get(name);
        if (value == null) {
          constantMap.put(name, isConst);
        } else {
          Preconditions.checkState(value.booleanValue() == isConst,
              "The name %s is not consistently annotated as constant.", name);
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


    private void reportCodeChange(String changeDescription, Node n) {
      if (assertOnChange) {
        throw new IllegalStateException(
            "Normalize constraints violated:\n" + changeDescription);
      }
      compiler.reportChangeToEnclosingScope(n);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      doStatementNormalizations(n);

      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case WHILE:
          Node expr = n.getFirstChild();
          n.setToken(Token.FOR);
          Node empty = IR.empty();
          empty.useSourceInfoIfMissingFrom(n);
          n.addChildBefore(empty, expr);
          n.addChildAfter(empty.cloneNode(), expr);
          reportCodeChange("WHILE node", n);
          break;

        case FUNCTION:
          if (visitFunction(n, compiler)) {
            reportCodeChange("Function declaration", n);
          }
          break;

        case EXPORT:
          splitExportDeclaration(n);
          break;

        case NAME:
        case STRING:
        case GETTER_DEF:
        case SETTER_DEF:
          annotateConstantsByConvention(n, parent);
          break;

        case CAST:
          compiler.reportChangeToEnclosingScope(n);
          parent.replaceChild(n, n.removeFirstChild());
          break;

        default:
          break;
      }
    }

    /**
     * Mark names and properties that are constants by convention.
     */
    private void annotateConstantsByConvention(Node n, Node parent) {
      checkState(
          n.isName() || n.isString() || n.isStringKey() || n.isGetterDef() || n.isSetterDef());

      // Need to check that variables have not been renamed, to determine whether
      // coding conventions still apply.
      if (compiler.getLifeCycleStage().isNormalizedObfuscated()) {
        return;
      }

      // There are only two cases where a string token
      // may be a variable reference: The right side of a GETPROP
      // or an OBJECTLIT key.
      boolean isObjLitKey = NodeUtil.isObjectLitKey(n);
      boolean isProperty = isObjLitKey || (parent.isGetProp() && parent.getLastChild() == n);
      if (n.isName() || isProperty) {
        boolean isMarkedConstant = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if (!isMarkedConstant
            && NodeUtil.isConstantByConvention(compiler.getCodingConvention(), n)) {
          if (assertOnChange) {
            String name = n.getString();
            throw new IllegalStateException(
                "Unexpected const change.\n"
                    + "  name: "
                    + name
                    + "\n"
                    + "  parent:"
                    + n.getParent().toStringTree());
          }
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
      }
    }

    /**
     * Splits ES6 export combined with a variable or function declaration.
     *
     */
    private void splitExportDeclaration(Node n) {
      if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
        return;
      }
      Node c = n.getFirstChild();
      if (NodeUtil.isDeclaration(c)) {
        n.removeChild(c);

        Node exportSpecs = new Node(Token.EXPORT_SPECS).srcref(n);
        n.addChildToFront(exportSpecs);
        Iterable<Node> names;
        if (c.isClass() || c.isFunction()) {
          names = Collections.singleton(c.getFirstChild());
          n.getParent().addChildBefore(c, n);
        } else {
          names = NodeUtil.findLhsNodesInNode(c);
          // Split up var declarations onto separate lines.
          for (Node child : c.children()) {
            c.removeChild(child);
            Node newDeclaration = new Node(c.getToken(), child).srcref(n);
            n.getParent().addChildBefore(newDeclaration, n);
          }
        }

        for (Node name : names) {
          Node exportSpec = new Node(Token.EXPORT_SPEC).srcref(name);
          exportSpec.addChildToFront(name.cloneNode());
          exportSpec.addChildToFront(name.cloneNode());
          exportSpecs.addChildToBack(exportSpec);
        }

        compiler.reportChangeToEnclosingScope(n.getParent());
      }
    }

    /**
     * Rewrite named unhoisted functions declarations to a known
     * consistent behavior so we don't to different logic paths for the same
     * code.
     *
     * From:
     *    function f() {}
     * to:
     *    var f = function () {};
     * and move it to the top of the block. This actually breaks
     * semantics, but the semantics are also not well-defined
     * cross-browser.
     *
     * See <a href="https://github.com/google/closure-compiler/pull/429">#429</a>
     */
    static boolean visitFunction(Node n, AbstractCompiler compiler) {
      checkState(n.isFunction(), n);
      if (NodeUtil.isFunctionDeclaration(n) && !NodeUtil.isHoistedFunctionDeclaration(n)) {
        rewriteFunctionDeclaration(n, compiler);
        return true;
      } else if (n.isFunction() && !NodeUtil.getFunctionBody(n).isBlock()) {
        Node returnValue = NodeUtil.getFunctionBody(n);
        Node body = IR.block(IR.returnNode(returnValue.detach()));
        body.useSourceInfoIfMissingFromForTree(returnValue);
        n.addChildToBack(body);
        compiler.reportChangeToEnclosingScope(body);
      }
      return false;
    }

    /**
     * Rewrite the function declaration from:
     *   function x() {}
     *   FUNCTION
     *     NAME x
     *     PARAM_LIST
     *     BLOCK
     * to:
     *   var x = function() {};
     *   VAR
     *     NAME x
     *       FUNCTION
     *         NAME (w/ empty string)
     *         PARAM_LIST
     *         BLOCK
     */
    private static void rewriteFunctionDeclaration(Node n, AbstractCompiler compiler) {
      // Prepare a spot for the function.
      Node oldNameNode = n.getFirstChild();
      Node fnNameNode = oldNameNode.cloneNode();
      Node var = IR.var(fnNameNode).srcref(n);

      // Prepare the function
      oldNameNode.setString("");
      compiler.reportChangeToEnclosingScope(oldNameNode);

      // Move the function to the front of the parent
      Node parent = n.getParent();
      parent.removeChild(n);
      parent.addChildToFront(var);
      compiler.reportChangeToEnclosingScope(var);
      fnNameNode.addChildToFront(n);
    }

    /**
     * Do normalizations that introduce new siblings or parents.
     */
    private void doStatementNormalizations(Node n) {
      if (n.isLabel()) {
        normalizeLabels(n);
      }

      // Only inspect the children of SCRIPTs, BLOCKs and LABELs, as all these
      // are the only legal place for VARs and FOR statements.
      if (NodeUtil.isStatementBlock(n) || n.isLabel()) {
        extractForInitializer(n, null, null);
      }

      // Only inspect the children of SCRIPTs, BLOCKs, as all these
      // are the only legal place for VARs.
      if (NodeUtil.isStatementBlock(n)) {
        splitVarDeclarations(n);
      }

      if (n.isFunction()) {
        moveNamedFunctions(n.getLastChild());
      }

      if (NodeUtil.isCompoundAssignmentOp(n)) {
        normalizeAssignShorthand(n);
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
      checkArgument(n.isLabel());

      Node last = n.getLastChild();
      // TODO(moz): Avoid adding blocks for cases like "label: let x;"
      switch (last.getToken()) {
        case LABEL:
        case BLOCK:
        case FOR:
        case FOR_IN:
        case FOR_OF:
        case WHILE:
        case DO:
          return;
        default:
          Node block = IR.block();
          block.useSourceInfoIfMissingFrom(last);
          n.replaceChild(last, block);
          block.addChildToFront(last);
          reportCodeChange("LABEL normalization", n);
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
        switch (c.getToken()) {
          case LABEL:
            extractForInitializer(c, insertBefore, insertBeforeParent);
            break;
          case FOR_IN:
          case FOR_OF:
            Node first = c.getFirstChild();
            if (first.isVar()) {
              Node lhs = first.getFirstChild();
              if (lhs.isDestructuringLhs()) {
                // Transform:
                //    for (var [a, b = 3] in c) {}
                // to:
                //    var a; var b; for ([a, b = 3] in c) {}
                List<Node> lhsNodes = NodeUtil.findLhsNodesInNode(lhs);
                for (Node name : lhsNodes) {
                  // Add a declaration outside the for loop for the given name.
                  checkState(
                      name.isName(),
                      "lhs in destructuring declaration should be a simple name.",
                      name);
                  Node newName = IR.name(name.getString()).srcref(name);
                  Node newVar = IR.var(newName).srcref(name);
                  insertBeforeParent.addChildBefore(newVar, insertBefore);
                }

                // Transform for (var [a, b]... ) to for ([a, b]...
                Node destructuringPattern = lhs.removeFirstChild();
                c.replaceChild(first, destructuringPattern);
              } else {
                // Transform:
                //    for (var a = 1 in b) {}
                // to:
                //    var a = 1; for (a in b) {};
                Node newStatement = first;
                // Clone just the node, to remove any initialization.
                Node name = newStatement.getFirstChild().cloneNode();
                first.replaceWith(name);
                insertBeforeParent.addChildBefore(newStatement, insertBefore);
              }
              reportCodeChange("FOR-IN var declaration", n);
            }
            break;
          case FOR:
            if (!c.getFirstChild().isEmpty()) {
              Node init = c.getFirstChild();

              if (init.isLet() || init.isConst() || init.isClass() || init.isFunction()) {
                return;
              }

              Node empty = IR.empty();
              empty.useSourceInfoIfMissingFrom(c);
              c.replaceChild(init, empty);

              Node newStatement;
              // Only VAR statements, and expressions are allowed,
              // but are handled differently.
              if (init.isVar()) {
                newStatement = init;
              } else {
                newStatement = NodeUtil.newExpr(init);
              }

              insertBeforeParent.addChildBefore(newStatement, insertBefore);
              reportCodeChange("FOR initializer", n);
            }
            break;
          default:
            break;
        }
      }
    }

    /**
     * Split a var (or let or const) node such as:
     *   var a, b;
     * into individual statements:
     *   var a;
     *   var b;
     * @param n The whose children we should inspect.
     */
    private void splitVarDeclarations(Node n) {
      for (Node next, c = n.getFirstChild(); c != null; c = next) {
        next = c.getNext();
        if (NodeUtil.isNameDeclaration(c)) {
          if (assertOnChange && !c.hasChildren()) {
            throw new IllegalStateException("Empty VAR node.");
          }

          while (c.getFirstChild() != c.getLastChild()) {
            Node name = c.getFirstChild();
            c.removeChild(name);
            Node newVar = new Node(c.getToken(), name).srcref(n);
            n.addChildBefore(newVar, c);
            reportCodeChange("VAR with multiple children", n);
          }
        }
      }
    }

    /**
     * Move all the functions that are valid at the execution of the first
     * statement of the function to the beginning of the function definition.
     */
    private void moveNamedFunctions(Node functionBody) {
      checkState(functionBody.getParent().isFunction());
      Node insertAfter = null;
      Node current = functionBody.getFirstChild();
      // Skip any declarations at the beginning of the function body, they
      // are already in the right place.
      while (current != null && NodeUtil.isFunctionDeclaration(current)) {
        insertAfter = current;
        current = current.getNext();
      }

      // Find any remaining declarations and move them.
      while (current != null) {
        // Save off the next node as the current node maybe removed.
        Node next = current.getNext();
        if (NodeUtil.isFunctionDeclaration(current)) {
          // Remove the declaration from the body.
          functionBody.removeChild(current);

          // Read the function at the top of the function body (after any
          // previous declarations).
          insertAfter = addToFront(functionBody, current, insertAfter);
          reportCodeChange("Move function declaration not at top of function", functionBody);
        }
        current = next;
      }
    }

    private void normalizeAssignShorthand(Node shorthand) {
      if (shorthand.getFirstChild().isName()) {
        Node name = shorthand.getFirstChild();
        shorthand.setToken(NodeUtil.getOpFromAssignmentOp(shorthand));
        Node parent = shorthand.getParent();
        Node insertPoint = IR.empty();
        parent.replaceChild(shorthand, insertPoint);
        Node assign = IR.assign(name.cloneNode().useSourceInfoFrom(name), shorthand)
            .useSourceInfoFrom(shorthand);
        assign.setJSDocInfo(shorthand.getJSDocInfo());
        shorthand.setJSDocInfo(null);
        parent.replaceChild(insertPoint, assign);
        compiler.reportChangeToEnclosingScope(assign);
      }
    }

    /**
     * @param after The child node to insert the newChild after, or null if
     *     newChild should be added to the front of parent's child list.
     * @return The inserted child node.
     */
    private static Node addToFront(Node parent, Node newChild, Node after) {
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
    ScopeCreator scopeCreator =
        new Es6SyntacticScopeCreator(compiler, new DuplicateDeclarationHandler());
    NodeTraversal t = new NodeTraversal(compiler, tickler, scopeCreator);
    t.traverseRoots(externs, root);
  }

  /**
   * ScopeCreator duplicate declaration handler.
   */
  private final class DuplicateDeclarationHandler implements
      Es6SyntacticScopeCreator.RedeclarationHandler {

    private final Set<Var> hasOkDuplicateDeclaration = new HashSet<>();

    /**
     * Remove duplicate VAR declarations discovered during scope creation.
     */
    @Override
    public void onRedeclaration(Scope s, String name, Node n, CompilerInput input) {
      checkState(n.isName());
      Node parent = n.getParent();
      Var v = s.getVar(name);

      if (s.isGlobal()) {
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

      if (parent.isFunction()) {
        if (v.getParentNode().isVar()) {
          s.undeclare(v);
          s.declare(name, n, v.input);
          replaceVarWithAssignment(v.getNameNode(), v.getParentNode(),
              v.getParentNode().getParent());
        }
      } else if (parent.isVar()) {
        checkState(parent.hasOneChild());

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
    private void replaceVarWithAssignment(Node n, Node parent, Node grandparent) {
      if (n.hasChildren()) {
        // The  *  is being initialize, preserve the new value.
        parent.removeChild(n);
        // Convert "var name = value" to "name = value"
        Node value = n.getFirstChild();
        n.removeChild(value);
        Node replacement = IR.assign(n, value);
        replacement.setJSDocInfo(parent.getJSDocInfo());
        replacement.useSourceInfoIfMissingFrom(parent);
        Node statement = NodeUtil.newExpr(replacement);
        grandparent.replaceChild(parent, statement);
        reportCodeChange("Duplicate VAR declaration", statement);
      } else {
        // It is an empty reference remove it.
        if (NodeUtil.isStatementBlock(grandparent)) {
          grandparent.removeChild(parent);
        } else if (grandparent.isForIn() || grandparent.isForOf()) {
          // This is the "for (var a in b)..." case.  We don't need to worry
          // about initializers in "for (var a;;)..." as those are moved out
          // as part of the other normalizations.
          parent.removeChild(n);
          grandparent.replaceChild(parent, n);
        } else {
          // We should never get here. LABELs with a single VAR statement should
          // already have been normalized to have a BLOCK.
          checkState(grandparent.isLabel(), grandparent);
        }
        reportCodeChange("Duplicate VAR declaration", grandparent);
      }
    }
  }

  /**
   * A simple class that causes scope to be created.
   */
  private static final class ScopeTicklingCallback implements NodeTraversal.ScopedCallback {
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
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // Nothing to do.
    }
  }
}
