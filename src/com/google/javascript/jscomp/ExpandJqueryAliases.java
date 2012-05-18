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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Replace known jQuery aliases and methods with standard
 * conventions so that the compiler recognizes them. Expected
 * replacements include:
 *  - jQuery.fn -> jQuery.prototype
 *  - jQuery.extend -> expanded into direct object assignments
 *  - jQuery.expandedEach -> expand into direct assignments
 *
 * @author chadkillingsworth@missouristate.edu (Chad Killingsworth)
 */
class ExpandJqueryAliases extends AbstractPostOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private static final Logger logger =
      Logger.getLogger(ExpandJqueryAliases.class.getName());

  static final DiagnosticType JQUERY_UNABLE_TO_EXPAND_INVALID_LIT_ERROR =
      DiagnosticType.warning("JSC_JQUERY_UNABLE_TO_EXPAND_INVALID_LIT",
          "jQuery.expandedEach call cannot be expanded because the first " +
          "argument must be an object literal or an array of strings " +
          "literal.");

  static final DiagnosticType JQUERY_UNABLE_TO_EXPAND_INVALID_NAME_ERROR =
      DiagnosticType.error("JSC_JQUERY_UNABLE_TO_EXPAND_INVALID_NAME",
          "jQuery.expandedEach expansion would result in the invalid " +
          "property name \"{0}\".");

  static final DiagnosticType JQUERY_USELESS_EACH_EXPANSION =
      DiagnosticType.warning("JSC_JQUERY_USELESS_EACH_EXPANSION",
          "jQuery.expandedEach was not expanded as no valid property " +
          "assignments were encountered. Consider using jQuery.each instead.");

  private static final Set<String> JQUERY_EXTEND_NAMES = ImmutableSet.of(
      "jQuery.extend", "jQuery.fn.extend", "jQuery.prototype.extend");

  private static final String JQUERY_EXPANDED_EACH_NAME =
      "jQuery.expandedEach";

  private final PeepholeOptimizationsPass peepholePasses;

  ExpandJqueryAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();

    // All of the "early" peephole optimizations.
    // These passes should make the code easier to analyze.
    // Passes, such as StatementFusion, are omitted for this reason.
    final boolean late = false;
    this.peepholePasses = new PeepholeOptimizationsPass(compiler,
        new PeepholeSubstituteAlternateSyntax(late),
        new PeepholeReplaceKnownMethods(late),
        new PeepholeRemoveDeadCode(),
        new PeepholeFoldConstants(late),
        new PeepholeCollectPropertyAssignments());
  }

  /**
   * Check that Node n is a call to one of the jQuery.extend methods that we
   * can expand. Valid calls are single argument calls where the first argument
   * is an object literal or two argument calls where the first argument
   * is a name and the second argument is an object literal.
   */
  public static boolean isJqueryExtendCall(Node n, String qname,
      AbstractCompiler compiler) {
    if (JQUERY_EXTEND_NAMES.contains(qname)) {
      Node firstArgument = n.getNext();
      if (firstArgument == null) {
        return false;
      }

      Node secondArgument = firstArgument.getNext();
      if ((firstArgument.isObjectLit() && secondArgument == null) ||
          (firstArgument.isName() || NodeUtil.isGet(firstArgument) &&
          !NodeUtil.mayHaveSideEffects(firstArgument, compiler) &&
          secondArgument != null && secondArgument.isObjectLit() &&
          secondArgument.getNext() == null)) {
        return true;
      }
    }
    return false;
  }

  public boolean isJqueryExpandedEachCall(Node call, String qName) {
    Preconditions.checkArgument(call.isCall());
    if (call.getFirstChild() != null &&
        JQUERY_EXPANDED_EACH_NAME.equals(qName)) {
      return true;
    }
    return false;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isGetProp() && convention.isPrototypeAlias(n)) {
      maybeReplaceJqueryPrototypeAlias(n);

    } else if (n.isCall()) {
      Node callTarget = n.getFirstChild();
      String qName = callTarget.getQualifiedName();

      if (isJqueryExtendCall(callTarget, qName, this.compiler)) {
        maybeExpandJqueryExtendCall(n);

      } else if (isJqueryExpandedEachCall(n, qName)) {
        maybeExpandJqueryEachCall(t, n);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    logger.fine("Expanding Jquery Aliases");

    NodeTraversal.traverse(compiler, root, this);
  }

  private void maybeReplaceJqueryPrototypeAlias(Node n) {
    // Check to see if this is the assignment of the original alias.
    // If so, leave it intact.
    if(NodeUtil.isLValue(n)) {
      Node maybeAssign = n.getParent();
      while (!NodeUtil.isStatement(maybeAssign) && !maybeAssign.isAssign()) {
        maybeAssign = maybeAssign.getParent();
      }

      if (maybeAssign.isAssign()) {
        maybeAssign = maybeAssign.getParent();
        if (maybeAssign.isBlock() || maybeAssign.isScript() ||
            NodeUtil.isStatement(maybeAssign)) {
          return;
        }
      }
    }

    Node fn = n.getLastChild();
    if (fn != null) {
      n.replaceChild(fn, IR.string("prototype"));
      compiler.reportCodeChange();
    }
  }

  /**
   * Expand jQuery.extend (and derivative) calls into direct object assignments
   * Example: jQuery.extend(obj1, {prop1: val1, prop2: val2}) ->
   *   obj1.prop1 = val1;
   *   obj1.prop2 = val2;
   */
  private void maybeExpandJqueryExtendCall(Node n) {
    Node callTarget = n.getFirstChild();
    Node objectToExtend = callTarget.getNext(); // first argument
    Node extendArg = objectToExtend.getNext(); // second argument
    boolean ensureObjectDefined = true;

    if (extendArg == null) {
      // Only one argument was specified, so extend jQuery namespace
      extendArg = objectToExtend;
      objectToExtend = callTarget.getFirstChild();
      ensureObjectDefined = false;
    } else if (objectToExtend.isGetProp() &&
          (objectToExtend.getLastChild().getString().equals("prototype") ||
          convention.isPrototypeAlias(objectToExtend))) {
      ensureObjectDefined = false;
    }

    // Check for an empty object literal
    if (!extendArg.hasChildren()) {
      return;
    }

    // Since we are expanding jQuery.extend calls into multiple statements,
    // encapsulate the new statements in a new block.
    Node fncBlock = IR.block().srcref(n);

    if (ensureObjectDefined) {
      Node assignVal = IR.or(objectToExtend.cloneTree(),
          IR.objectlit().srcref(n)).srcref(n);
      Node assign = IR.assign(objectToExtend.cloneTree(), assignVal).srcref(n);
      fncBlock.addChildrenToFront(IR.exprResult(assign).srcref(n));
    }

    while (extendArg.hasChildren()) {
      Node currentProp = extendArg.removeFirstChild();
      currentProp.setType(Token.STRING);

      Node propValue = currentProp.removeFirstChild();

      Node newProp;
      if(currentProp.isQuotedString()) {
        newProp = IR.getelem(objectToExtend.cloneTree(),
            currentProp).srcref(currentProp);
      } else {
        newProp = IR.getprop(objectToExtend.cloneTree(),
            currentProp).srcref(currentProp);
      }

      Node assignNode = IR.assign(newProp, propValue).srcref(currentProp);
      fncBlock.addChildToBack(IR.exprResult(assignNode).srcref(currentProp));
    }

    // Check to see if the return value is used. If not, replace the original
    // call with new block. Otherwise, wrap the statements in an
    // immediately-called anonymous function.
    if (n.getParent().isExprResult()) {
      Node parent = n.getParent();
      parent.getParent().replaceChild(parent, fncBlock);
    } else {
      Node targetVal;
      if ("jQuery.prototype".equals(objectToExtend.getQualifiedName())) {
        // When extending the jQuery prototype, return the jQuery namespace.
        // This is not commonly used.
        targetVal = objectToExtend.removeFirstChild();
      } else {
        targetVal = objectToExtend.detachFromParent();
      }
      fncBlock.addChildToBack(IR.returnNode(targetVal).srcref(targetVal));

      Node fnc = IR.function(IR.name("").srcref(n),
          IR.paramList().srcref(n),
          fncBlock);
      n.replaceChild(callTarget, fnc);
      n.putBooleanProp(Node.FREE_CALL, true);

      // remove any other pre-existing call arguments
      while(fnc.getNext() != null) {
        n.removeChildAfter(fnc);
      }
    }
    compiler.reportCodeChange();
  }

  /**
   * Expand a jQuery.expandedEach call
   *
   * Expanded jQuery.expandedEach calls will replace the GETELEM nodes of a
   * property assignment with GETPROP nodes to allow for renaming.
   */
  private void maybeExpandJqueryEachCall(NodeTraversal t, Node n) {
    Node objectToLoopOver = n.getChildAtIndex(1);

    if (objectToLoopOver == null) {
      return;
    }

    Node callbackFunction = objectToLoopOver.getNext();
    if (callbackFunction == null || !callbackFunction.isFunction()) {
      return;
    }

    // Run the peephole optimizations on the first argument to handle
    // cases like ("a " + "b").split(" ")
    peepholePasses.process(null, n.getChildAtIndex(1));

    // Create a reference tree
    Node nClone = n.cloneTree();

    objectToLoopOver = nClone.getChildAtIndex(1);

    // Check to see if the first argument is something we recognize and can
    // expand.
    if (!objectToLoopOver.isObjectLit() &&
        !(objectToLoopOver.isArrayLit() &&
        isArrayLitValidForExpansion(objectToLoopOver))) {
      t.report(n, JQUERY_UNABLE_TO_EXPAND_INVALID_LIT_ERROR, (String)null);
      return;
    }

    // Find all references to the callback function arguments
    List<Node> keyNodeReferences = Lists.newArrayList();
    List<Node> valueNodeReferences = Lists.newArrayList();

    NodeTraversal.traverse(compiler,
        NodeUtil.getFunctionBody(callbackFunction),
        new FindCallbackArgumentReferences(callbackFunction,
            keyNodeReferences, valueNodeReferences,
            objectToLoopOver.isArrayLit()));

    if(keyNodeReferences.size() == 0) {
     // We didn't do anything useful ...
      t.report(n, JQUERY_USELESS_EACH_EXPANSION, (String)null);
      return;
    }

    Node fncBlock = tryExpandJqueryEachCall(t, nClone, callbackFunction,
        keyNodeReferences, valueNodeReferences);

    if (fncBlock != null && fncBlock.hasChildren()) {
        replaceOriginalJqueryEachCall(n, fncBlock);
    } else {
      // We didn't do anything useful ...
      t.report(n, JQUERY_USELESS_EACH_EXPANSION, (String)null);
    }
  }

  private Node tryExpandJqueryEachCall(NodeTraversal t, Node n,
      Node callbackFunction, List<Node> keyNodes, List<Node> valueNodes) {

    Node callTarget = n.getFirstChild();
    Node objectToLoopOver = callTarget.getNext();

    // New block to contain the expanded statements
    Node fncBlock = IR.block().srcref(callTarget);

    boolean isValidExpansion = true;

    // Expand the jQuery.expandedEach call
    Node key = objectToLoopOver.getFirstChild(), val = null;
    for(int i = 0; key != null; key = key.getNext(), i++) {
      if (key != null) {
        if (objectToLoopOver.isArrayLit()) {
          // Arrays have a value of their index number
          val = IR.number(i).srcref(key);
        } else {
          val = key.getFirstChild();
        }
      }

      // Keep track of the replaced nodes so we can reset the tree
      List<Node> newKeys = Lists.newArrayList();
      List<Node> newValues = Lists.newArrayList();
      List<Node> origGetElems = Lists.newArrayList();
      List<Node> newGetProps = Lists.newArrayList();

      // Replace all of the key nodes with the prop name
      for (int j = 0; j < keyNodes.size(); j++) {
        Node origNode = keyNodes.get(j);
        Node ancestor = origNode.getParent();

        Node newNode = IR.string(key.getString()).srcref(key);
        newKeys.add(newNode);
        ancestor.replaceChild(origNode, newNode);

        // Walk up the tree to see if the key is used in a GETELEM
        // assignment
        while (ancestor != null && !NodeUtil.isStatement(ancestor) &&
            !ancestor.isGetElem()) {
          ancestor = ancestor.getParent();
        }

        // Convert GETELEM nodes to GETPROP nodes so that they can be
        // renamed or removed.
        if (ancestor != null && ancestor.isGetElem()) {

          Node propObject = ancestor;
          while (propObject.isGetProp() || propObject.isGetElem()) {
            propObject = propObject.getFirstChild();
          }

          Node ancestorClone = ancestor.cloneTree();
          // Run the peephole passes to handle cases such as
          // obj['lit' + key] = val;
          peepholePasses.process(null, ancestorClone.getChildAtIndex(1));
          Node prop = ancestorClone.getChildAtIndex(1);

          if (prop.isString() &&
            NodeUtil.isValidPropertyName(prop.getString())) {
            Node target = ancestorClone.getFirstChild();
            Node newGetProp = IR.getprop(target.detachFromParent(),
                prop.detachFromParent());
            newGetProps.add(newGetProp);
            origGetElems.add(ancestor);
            ancestor.getParent().replaceChild(ancestor, newGetProp);
          } else {
            if (prop.isString() &&
                !NodeUtil.isValidPropertyName(prop.getString())) {
              t.report(n,
                  JQUERY_UNABLE_TO_EXPAND_INVALID_NAME_ERROR,
                  prop.getString());
            }
            isValidExpansion = false;
          }
        }
      }

      if (isValidExpansion) {
        // Replace all of the value nodes with the prop value
        for (int j = 0; val != null && j < valueNodes.size(); j++) {
          Node origNode = valueNodes.get(j);
          Node newNode = val.cloneTree();
          newValues.add(newNode);
          origNode.getParent().replaceChild(origNode, newNode);
        }

        // Wrap the new tree in an anonymous function call
        Node fnc = IR.function(IR.name("").srcref(key),
            IR.paramList().srcref(key),
            callbackFunction.getChildAtIndex(2).cloneTree()).srcref(key);
        Node call = IR.call(fnc).srcref(key);
        call.putBooleanProp(Node.FREE_CALL, true);
        fncBlock.addChildToBack(IR.exprResult(call).srcref(call));
      }

      // Reset the source tree
      for (int j = 0; j < newGetProps.size(); j++) {
        newGetProps.get(j).getParent().replaceChild(newGetProps.get(j),
            origGetElems.get(j));
      }
      for (int j = 0; j < newKeys.size(); j++) {
        newKeys.get(j).getParent().replaceChild(newKeys.get(j),
            keyNodes.get(j));
      }
      for (int j = 0; j < newValues.size(); j++) {
        newValues.get(j).getParent().replaceChild(newValues.get(j),
            valueNodes.get(j));
      }

      if (!isValidExpansion) {
        return null;
      }
    }
    return fncBlock;
  }

  private void replaceOriginalJqueryEachCall(Node n, Node expandedBlock) {
    // Check to see if the return value of the original jQuery.expandedEach
    // call is used. If so, we need to wrap each loop expansion in an anonymous
    // function and return the original objectToLoopOver.
    if (n.getParent().isExprResult()) {
      Node parent = n.getParent();
      Node grandparent = parent.getParent();
      Node insertAfter = parent;
      while (expandedBlock.hasChildren()) {
        Node child = expandedBlock.getFirstChild().detachFromParent();
        grandparent.addChildAfter(child, insertAfter);
        insertAfter = child;
      }
      grandparent.removeChild(parent);
    } else {
      // Return the original object
      Node callTarget = n.getFirstChild();
      Node objectToLoopOver = callTarget.getNext();

      objectToLoopOver.detachFromParent();
      Node ret = IR.returnNode(objectToLoopOver).srcref(callTarget);
      expandedBlock.addChildToBack(ret);

      // Wrap all of the expanded loop calls in a new anonymous function
      Node fnc = IR.function(IR.name("").srcref(callTarget),
          IR.paramList().srcref(callTarget),
          expandedBlock);
      n.replaceChild(callTarget, fnc);
      n.putBooleanProp(Node.FREE_CALL, true);

      // remove any other pre-existing call arguments
      while(fnc.getNext() != null) {
        n.removeChildAfter(fnc);
      }
    }
    compiler.reportCodeChange();
  }

  private boolean isArrayLitValidForExpansion(Node n) {
    Iterator<Node> iter = n.children().iterator();
    while (iter.hasNext()) {
      Node child = iter.next();
      if (!child.isString()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Given a jQuery.expandedEach callback function, traverse it and collect any
   * references to its parameter names.
   */
  class FindCallbackArgumentReferences extends AbstractPostOrderCallback
      implements ScopedCallback {

    private final String keyName;
    private final String valueName;
    private Scope startingScope;
    private List<Node> keyReferences;
    private List<Node> valueReferences;

    FindCallbackArgumentReferences(Node functionRoot, List<Node> keyReferences,
        List<Node> valueReferences, boolean useArrayMode) {
      Preconditions.checkState(functionRoot.isFunction());

      String keyString = null, valueString = null;
      Node callbackParams = NodeUtil.getFunctionParameters(functionRoot);
      Node param = callbackParams.getFirstChild();
      if (param != null) {
        Preconditions.checkState(param.isName());
        keyString = param.getString();

        param = param.getNext();
        if (param != null) {
          Preconditions.checkState(param.isName());
          valueString = param.getString();
        }
      }

      this.keyName = keyString;
      this.valueName = valueString;

      // For arrays, the keyString is the index number of the element.
      // We're interested in the value of the element instead
      if (useArrayMode) {
        this.keyReferences = valueReferences;
        this.valueReferences = keyReferences;
      } else {
        this.keyReferences = keyReferences;
        this.valueReferences = valueReferences;
      }

      this.startingScope = null;
    }

    private boolean isShadowed(String name, Scope scope) {
      Var nameVar = scope.getVar(name);
      return nameVar != null &&
          nameVar.getScope() != this.startingScope;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // In the top scope, "this" is a reference to "value"
      boolean isThis = false;
      if (t.getScope() == this.startingScope) {
        isThis = n.isThis();
      }

      if (isThis || n.isName() && !isShadowed(n.getString(), t.getScope())) {
        String nodeValue = isThis ? null : n.getString();
        if (!isThis && keyName != null && nodeValue.equals(keyName)) {
          keyReferences.add(n);
        } else if (isThis || (valueName != null &&
            nodeValue.equals(valueName))) {
          valueReferences.add(n);
        }
      }
    }

    /**
     * As we enter each scope, make sure that the scope doesn't define
     * a local variable with the same name as our original callback method
     * parameter names.
     */
    @Override
    public void enterScope(NodeTraversal t) {
      if (this.startingScope == null) {
        this.startingScope = t.getScope();
      }
    }

    @Override
    public void exitScope(NodeTraversal t) { }
  }
}
