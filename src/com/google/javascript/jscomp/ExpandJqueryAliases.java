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

import java.util.Set;
import java.util.logging.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Replace known jQuery aliases and methods with standard
 * conventions so that the compiler recognizes them. Expected
 * replacements include:
 *  - jQuery.fn -> jQuery.prototype
 *  - jQuery.extend -> expanded into direct object assignments
 *
 * @author chadkillingsworth@missouristate.edu (Chad Killingsworth)
 */
class ExpandJqueryAliases extends AbstractPostOrderCallback
    implements CompilerPass {
  private final AbstractCompiler compiler;
  private static final Logger logger =
      Logger.getLogger(ExpandJqueryAliases.class.getName());
  private static final Set<String> JqueryExtendNames = ImmutableSet.of(
      "jQuery.extend", "jQuery.fn.extend", "jQuery.prototype.extend");

  ExpandJqueryAliases(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public static boolean isJqueryExtendReference(Node n, String qname) {
    if (JqueryExtendNames.contains(qname)) {
      Node firstArgument = n.getNext();
      if (firstArgument == null) {
        return false;
      }

      Node secondArgument = firstArgument.getNext();
      if ((firstArgument.isObjectLit() && secondArgument == null) ||
          (firstArgument.isName() && secondArgument != null &&
          secondArgument.isObjectLit() && secondArgument.getNext() == null)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (compiler.getCodingConvention().isPrototypeAlias(n)) {
      replaceJqueryPrototypeAlias(n);
    } else if (n.isCall()) {
      Node callTarget = n.getFirstChild();
      String qName = callTarget.getQualifiedName();

      if (isJqueryExtendReference(callTarget, qName)) {
        replaceJqueryExtendCall(n);
      }
    }
  }

  @Override
  public void process(Node externs, Node root) {
    logger.fine("Expanding Jquery Aliases");

    // Traverse the tree and collect strings
    NodeTraversal.traverse(compiler, root, this);
  }

  private void replaceJqueryPrototypeAlias(Node n) {
    Node fn = n.getLastChild();
    if (fn != null) {
      n.replaceChild(fn, IR.string("prototype"));
      compiler.reportCodeChange();
    }
  }

  private void replaceJqueryExtendCall(Node n) {
    Node callTarget = n.getFirstChild();
    Node objectToExtend = callTarget.getNext(); //first argument
    Node extendArg = objectToExtend.getNext(); //second argument

    if (extendArg == null) {
      //Only one argument was specified, so extend jQuery namespace
      extendArg = objectToExtend;
      objectToExtend = callTarget.getFirstChild();
    }

    //Check for an empty object literal
    if (!extendArg.hasChildren())
      return;

    /* Since we are expanding jQuery.extend calls into multiple statements,
     * encapsulate the new statements in an immediately executed anonymous
     * function that returns the extended object.
     */
    Node fncBlock = IR.block().srcref(n);

    while (extendArg.hasChildren()) {
      Node currentProp = extendArg.removeFirstChild();
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

    Node targetVal;
    if ("jQuery.prototype".equals(objectToExtend.getQualifiedName())) {
      /* When extending the jQuery prototype, return the jQuery namespace.
       * No known uses of the return value exist for this case.
       * TODO(Chad Killingsworth): Check jQuery plugins
       */
      targetVal = objectToExtend.getFirstChild().cloneTree();
    } else {
      targetVal = objectToExtend.cloneTree();
    }
    fncBlock.addChildToBack(IR.returnNode(targetVal).srcref(targetVal));

    Node fnc = IR.function(IR.name("").srcref(n),
        IR.paramList().srcref(n),
        fncBlock);
    n.replaceChild(callTarget, fnc);
    n.putBooleanProp(Node.FREE_CALL, true);

    //remove any other pre-existing call arguments
    while(fnc.getNext() != null) {
      n.removeChildAfter(fnc);
    }

    compiler.reportCodeChange();
  }
}
