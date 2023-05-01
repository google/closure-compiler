/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * Inlines methods that take no arguments and have only a return statement returning a property.
 * Because it works on method names rather than type inference, a method with multiple definitions
 * will be inlined if each definition is identical.
 *
 * <pre>
 * A.prototype.foo = function() { return this.b; }
 * B.prototype.foo = function() { return this.b; }
 * </pre>
 *
 * will inline foo, but
 *
 * <pre>
 * A.prototype.foo = function() { return this.b; }
 * B.prototype.foo = function() { return this.c; }
 * </pre>
 *
 * will not.
 *
 * <p>Declarations are not removed because we do not find all possible call sites. For examples,
 * calls of the form foo["bar"] are not detected.
 *
 * <p>This pass is not on by default because it is not safe in simple mode. If the prototype method
 * is mutated and we don't detect that, inlining it is unsafe. We enable it whenever function
 * inlining is enabled.
 */
class InlineSimpleMethods implements CompilerPass {

  // List of property names that we know are not safe to inline
  // This includes:
  //   - extern properties (we can't see the actual method definition for externs)
  //   - non-method properties
  //   - methods with @noinline
  //   - methods with multiple, non-equivalent definitions
  private final Set<String> nonInlineableProperties = new HashSet<>();

  // Use a linked map here to keep the output deterministic.  Otherwise,
  // the choice of method bodies is random when multiple identical definitions
  // are found which causes problems in the source maps.
  private final SetMultimap<String, Node> methodDefinitions = LinkedHashMultimap.create();

  private final AbstractCompiler compiler;
  private final AstAnalyzer astAnalyzer;

  InlineSimpleMethods(AbstractCompiler compiler) {
    this.compiler = compiler;
    astAnalyzer = compiler.getAstAnalyzer();
    nonInlineableProperties.addAll(compiler.getExternProperties());
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized(), compiler.getLifeCycleStage());
    checkState(methodDefinitions.isEmpty());
    checkState(externs != null);

    NodeTraversal.traverseRoots(compiler, new GatherSignatures(), externs, root);
    NodeTraversal.traverse(compiler, root, new InlineTrivialAccessors());
  }

  /** For each method call, see if it is a candidate for inlining, and do the inlining if so. */
  private class InlineTrivialAccessors extends InvocationsCallback {

    @Override
    void visit(NodeTraversal t, Node callNode, Node parent, String callName) {
      if (nonInlineableProperties.contains(callName)) {
        return;
      }

      Set<Node> definitions = methodDefinitions.get(callName);
      if (definitions == null || definitions.isEmpty()) {
        return;
      }

      // Exit early if all method definitions are not equivalent, and mark this method as not
      // inlineable.
      // NOTE: we could also cache the 'good' result of this method having all equivalent
      // definitions and avoid recalculating later, but profile data suggests that's not too useful
      if (definitions.size() > 1 && !allDefinitionsEquivalent(definitions)) {
        nonInlineableProperties.add(callName);
        return;
      }
      // Exit early if any definitions are annotated with @noinline, and mark this method as not
      // inlineable.
      // NOTE: we could also cache the 'good' result of this method not being marked @noinline and
      // avoid recalculating later, but profile data suggests that's not too useful.
      if (anyDefinitionsNoInline(definitions)) {
        nonInlineableProperties.add(callName);
        return;
      }

      // Do check of arity, complexity, and consistency in what we think is
      // the order from least to most complex
      Node firstDefinition = definitions.iterator().next();

      // Do not inline if the callsite is a derived class calling a base method using `super.`
      if (!argsMayHaveSideEffects(callNode) && !NodeUtil.referencesSuper(callNode)) {
        // Verify this is a trivial return
        Node returned = returnedExpression(firstDefinition);
        if (returned != null) {
          if (isPropertyTree(returned) && !firstDefinition.isArrowFunction()) {
            inlinePropertyReturn(callNode, returned);
          } else if (NodeUtil.isLiteralValue(returned, false)
              && !astAnalyzer.mayHaveSideEffects(callNode.getFirstChild())) {
            inlineConstReturn(callNode, returned);
          }
        } else if (isEmptyMethod(firstDefinition)
            && !astAnalyzer.mayHaveSideEffects(callNode.getFirstChild())) {
          inlineEmptyMethod(t, parent, callNode);
        }
      }
    }
  }

  /**
   * Returns true if the provided node is a getprop for
   * which the left child is this or a valid property tree
   * and for which the right side is a string.
   */
  private static boolean isPropertyTree(Node expectedGetprop) {
    if (!expectedGetprop.isGetProp()) {
      return false;
    }

    Node getpropLhs = expectedGetprop.getFirstChild();
    return getpropLhs.isThis() || isPropertyTree(getpropLhs);
  }

  /**
   * Finds the occurrence of "this" in the provided property tree and replaces
   * it with replacement
   */
  private static void replaceThis(Node expectedGetprop, Node replacement) {
    Node leftChild = expectedGetprop.getFirstChild();
    if (leftChild.isThis()) {
      leftChild.replaceWith(replacement);
    } else {
      replaceThis(leftChild, replacement);
    }
  }

  /**
   * Return the node that represents the expression returned by the method, given a FUNCTION node.
   */
  private static @Nullable Node returnedExpression(Node fn) {
    Node expectedBlock = NodeUtil.getFunctionBody(fn);
    if (!expectedBlock.hasOneChild()) {
      return null;
    }

    Node expectedReturn = expectedBlock.getFirstChild();
    if (!expectedReturn.isReturn()) {
      return null;
    }

    if (!expectedReturn.hasOneChild()) {
      return null;
    }

    return expectedReturn.getOnlyChild();
  }


  /**
   * Return whether the given FUNCTION node is an empty method definition.
   *
   * Must be private, or moved to NodeUtil.
   */
  private static boolean isEmptyMethod(Node fn) {
    return NodeUtil.isEmptyBlock(NodeUtil.getFunctionBody(fn));
  }

  /** Given a set of method definitions, verify they are the same. */
  private boolean allDefinitionsEquivalent(Set<Node> definitions) {
    Node first = null;
    for (Node n : definitions) {
      if (first == null) {
        first = n;
      } else if (!compiler.areNodesEqualForInlining(first, n)) {
        return false;
      } // else continue
    }
    return true;
  }

  private boolean anyDefinitionsNoInline(Set<Node> definitions) {
    for (Node n : definitions) {
      JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(n.getParent());
      if (jsDocInfo != null && jsDocInfo.isNoInline()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Replace the provided method call with the tree specified in returnedValue
   *
   * <p>Parse tree of a call is name call getprop obj string
   */
  private void inlinePropertyReturn(Node call, Node returnedValue) {
    Node getProp = returnedValue.cloneTree();
    replaceThis(getProp, call.getFirstChild().removeFirstChild());
    call.replaceWith(getProp);
    compiler.reportChangeToEnclosingScope(getProp);
  }

  /**
   * Replace the provided object and its method call with the tree specified in returnedValue.
   * Should be called only if the object reference has no side effects.
   */
  private void inlineConstReturn(Node call, Node returnedValue) {
    Node retValue = returnedValue.cloneTree();
    call.replaceWith(retValue);
    compiler.reportChangeToEnclosingScope(retValue);
  }

  /**
   * Remove the provided object and its method call.
   */
  private void inlineEmptyMethod(NodeTraversal t, Node parent, Node call) {
    // If the return value of the method call is read,
    // replace it with "void 0". Otherwise, remove the call entirely.

    if (NodeUtil.isExprCall(parent)) {
      parent.replaceWith(IR.empty());
      NodeUtil.markFunctionsDeleted(parent, compiler);
    } else {
      Node srcLocation = call;
      call.replaceWith(NodeUtil.newUndefinedNode(srcLocation));
      NodeUtil.markFunctionsDeleted(call, compiler);
    }
    t.reportCodeChange();
  }

  /**
   * Check whether the given method call's arguments have side effects.
   * @param call The call node of a method invocation.
   */
  private boolean argsMayHaveSideEffects(Node call) {
    for (Node currentChild = call.getSecondChild();
         currentChild != null;
         currentChild = currentChild.getNext()) {
      if (astAnalyzer.mayHaveSideEffects(currentChild)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Adds a node that may represent a function signature (if it's a function itself or the name of a
   * function).
   */
  private void addPossibleSignature(String name, Node node) {
    if (node != null && node.isFunction()) {
      // The node we're looking at is a function, so we can add it directly
      addSignature(name, node);
    } else {
      nonInlineableProperties.add(name);
    }
  }

  private void addSignature(String name, Node function) {
    if (nonInlineableProperties.contains(name)) {
      return;
    }

    methodDefinitions.put(name, function);
  }

  /** Gather signatures from the source to be compiled. */
  private class GatherSignatures extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
          // TODO(b/251573710): Handle ES2015+ language features that can reference properties
        case GETPROP:
        case GETELEM:
          String name = getPropName(n);
          if (name == null) {
            return;
          }

          if (name.equals("prototype")) {
            processPrototypeParent(parent);
          } else {
            // Static methods of the form Foo.bar = function() {} or
            // Static methods of the form Foo.bar = baz (where baz is a
            // function name). Parse tree looks like:
            // assign                 <- parent
            //      getprop           <- n
            //          name Foo
            //          string bar
            //      function or name  <- n.getNext()
            if (parent.isAssign() && n.isFirstChildOf(parent)) {
              addPossibleSignature(name, n.getNext());
            }
          }
          break;

        case OBJECTLIT:
        case CLASS_MEMBERS:
          for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
            switch (key.getToken()) {
              case MEMBER_FUNCTION_DEF:
              case MEMBER_FIELD_DEF:
              case STRING_KEY:
                addPossibleSignature(key.getString(), key.getFirstChild());
                break;
              case SETTER_DEF:
              case GETTER_DEF:
                nonInlineableProperties.add(key.getString());
                break;
              case COMPUTED_PROP: // complicated
              case OBJECT_SPREAD:
              case COMPUTED_FIELD_DEF:
                break;
              default:
                throw new IllegalStateException("Unexpected " + n.getToken() + " key: " + key);
            }
          }
          break;
        case CALL:
          // If a goog.reflect.objectProperty is used for a method's name, we can't assume that the
          // method can be safely inlined.
          if (compiler.getCodingConvention().isPropertyRenameFunction(n.getFirstChild())) {
            // Other code guarantees that getSecondChild() is a STRINGLIT
            nonInlineableProperties.add(n.getSecondChild().getString());
          }
          break;
        default:
          break;
      }
    }

    /**
     * Processes the parent of a GETPROP prototype, which can either be another GETPROP (in the case
     * of Foo.prototype.bar), or can be an assignment (in the case of Foo.prototype = ...).
     */
    private void processPrototypeParent(Node n) {
      switch (n.getToken()) {
          // Foo.prototype.getBar = function() { ... } or
          // Foo.prototype.getBar = getBaz (where getBaz is a function)
          // parse tree looks like:
          // assign                          <- parent
          //     getprop                     <- n
          //         getprop
          //             name Foo
          //             string prototype
          //         string getBar
          //     function or name            <- assignee
        case GETPROP:
        case GETELEM:
          Node grandparent = n.getGrandparent();
          String name = getPropName(n);
          if (name != null && grandparent.isAssign()) {
            Node assignee = grandparent.getSecondChild();
            addPossibleSignature(name, assignee);
          }
          break;
        default:
          break;
      }
    }
  }

  private static @Nullable String getPropName(Node getPropElem) {
    if (getPropElem.isGetProp()) {
      return getPropElem.getString();
    } else if (getPropElem.getSecondChild().isStringLit()) {
      return getPropElem.getSecondChild().getString();
    } else {
      return null;
    }
  }
}
