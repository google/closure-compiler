/*
 * Copyright 2009 The Closure Compiler Authors.
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NameReferenceGraph.Name;
import com.google.javascript.jscomp.NameReferenceGraph.Reference;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.InstanceObjectType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Constructs a name reference graph.
 *
 * @see NameReferenceGraph
 *
 */
class NameReferenceGraphConstruction implements CompilerPass {

  private final AbstractCompiler compiler;
  private final NameReferenceGraph graph;

  // Maps "foo" -> (curFuncName, unknownObject.foo) if we have no idea what
  // the unknown object is. After we finish one pass, we must go through all
  // the nodes that might have a name foo and connect that to the curFuncName.
  // The accuracy of the analysis will depend heavily on eliminating the need
  // to resort to this map.
  private final Multimap<String, NameUse> unknownNameUse =
      HashMultimap.create();

  // Should we continue even if we found a type checker bug.
  private static final boolean CONSERVATIVE = false;

  // The symbol for the current function so we can quickly create a reference
  // edge when we see a call: Example when this symbol is foo() and we see
  // bar(), we connect foo -> bar.
  private final ArrayList<Name> currentFunctionStack = new ArrayList<Name>();

  NameReferenceGraphConstruction(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.graph = new NameReferenceGraph(compiler);
  }

  NameReferenceGraph getNameReferenceGraph() {
    return this.graph;
  }

  public void process(Node externs, Node root) {
    // Use the MemoizedScopeCreator instance from TypeCheck if available
    // as FunctionTypeBuilder warns about existing types if TypedScopeCreator is
    // ran a second time.
    ScopeCreator scopeCreator = compiler.getScopeCreator();
    if (scopeCreator == null) {
      // The TypedScopeCreator gives us correct handling of namespaces,
      // while the default NodeTraversal only gives us a
      // SyntacticScopeCreator.
      scopeCreator = new TypedScopeCreator(compiler);
    }
    NodeTraversal externsTraversal = new NodeTraversal(compiler,
        new Traversal(true), scopeCreator);
    NodeTraversal codeTraversal = new NodeTraversal(compiler,
        new Traversal(false), scopeCreator);
    Scope topScope = compiler.getTopScope();
    if (topScope != null) {
      externsTraversal.traverseWithScope(externs, topScope);
      codeTraversal.traverseWithScope(root, topScope);
    } else {
      externsTraversal.traverse(externs);
      codeTraversal.traverse(root);
    }
    connectUnknowns();
  }

  private class Traversal implements ScopedCallback {

    final boolean isExtern;

    private Traversal(boolean isExtern) {
      this.isExtern = isExtern;
      pushContainingFunction(graph.MAIN);
    }

    @Override
    public void enterScope(NodeTraversal t) {
      Node root = t.getScopeRoot();
      Node parent = root.getParent();

      // When we are not in a {{GLOBAL MAIN}}, we need to determine what the
      // current function is.
      if (!t.inGlobalScope()) {

        // TODO(user): A global function foo() is treated as the same
        // function as a inner function named foo(). We should use some clever
        // naming scheme to avoid this lost of precision.
        String name = NodeUtil.getFunctionName(root);

        if (name == null) {
          // When the name is null, we have a function that is presumably not
          // reference-able again and should not be modeled in the name graph.
          // A common example would be (function() { ... })();
          pushContainingFunction(graph.UNKNOWN);
          return;
        }

        // If we've done type analysis, then we should be able to get the
        // correct JSFunctionType for the containing function.  If not,
        // we're probably going to get an unknown type here.
        JSType type = getType(root);

        Node gParent = parent.getParent();
        Node ggParent = gParent.getParent();
        if (NodeUtil.isAssign(parent) &&
            NodeUtil.isPrototypeProperty(parent.getFirstChild())) {
          pushContainingFunction(
              recordPrototypePropDefinition(t, parent.getFirstChild(), type,
                  parent, gParent, ggParent));
        } else {
          pushContainingFunction(
              recordStaticNameDefinition(
                t, name, type, root, parent, gParent, root.getLastChild()));
        }
      }
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (!t.inGlobalScope()) {
        popContainingFunction();
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.NAME:
        case Token.GETPROP:
          if (NodeUtil.isGetProp(parent) ||
              parent.getType() == Token.REF_SPECIAL) {
            // We will resolve this when we visit parent later in the traversal.
            return;
          } else if (NodeUtil.isFunction(parent)) {
            // Function declarations have been taken care of in enterScope();
            return;
          } else if (NodeUtil.isAssign(parent)) {
            // Handled below.
            return;
          }

          if (isLocalNameReference(t, n)) {
            // Ignore all local variable references unless is creates a closure.
            return;
          }

          if (isPrototypeNameReference(n)) {
            recordPrototypePropUse(t, n, parent);
          } else if (isStaticNameReference(n, t.getScope())) {
            recordStaticNameUse(t, n, parent);
          } else {
            recordUnknownUse(t, n, parent);
          }
          break;

        case Token.ASSIGN:
          Node lhs = n.getFirstChild();
          Node rhs = n.getLastChild();
          if (NodeUtil.isFunction(rhs)) {
            // These are recorded when entering the scope.
            return;
          }
          if (NodeUtil.isName(lhs) ||
              NodeUtil.isGetProp(lhs) ||
              NodeUtil.isGetProp(rhs)) {
            if (NodeUtil.isPrototypeProperty(lhs)) {
              Name name = recordPrototypePropDefinition(
                  t, lhs, getType(rhs), n, parent, parent.getParent());
              name.setAliased(true);
            }
          }
          maybeAliasNamesOnAssign(lhs, rhs);
          break;

        case Token.VAR:
          // var foo = bar;
          Node varName = n.getFirstChild();
          Node assignedValue = varName.getFirstChild();
          if (assignedValue == null) {
            return;
          }
          maybeAliasNamesOnAssign(varName, assignedValue);
          break;

        case Token.CALL:
          Node param = n.getFirstChild();
          // We need to alias every name that is passed as a parameter because
          // they have different names inside the function's scope.
          while ((param = param.getNext()) != null) {
            if (NodeUtil.isName(param) || NodeUtil.isGetProp(param)) {
              safeAlias(param);
            }
          }

          maybeRecordExport(n);
          break;
      }
    }

    private boolean containsName(Node n) {
      return NodeUtil.containsType(n, Token.NAME) ||
          NodeUtil.containsType(n, Token.GETELEM) ||
          NodeUtil.containsType(n, Token.GETPROP);
    }

    /**
     * Given a node, this alias all the names in the node that need aliasing.
     * This is safer than just calling getQualifiedName() because it can return
     * null it several situations.
     * @param n node to alias
     */
    private void safeAlias(Node n) {
      if (NodeUtil.isName(n) || NodeUtil.isGetProp(n)) {
        String name = n.getQualifiedName();
        // getQualifiedName can return null in cases like bar[0].baz
        if (name != null) {
          defineAndAlias(name);
          return;
        }
      }

      if (NodeUtil.isGetProp(n)) {
        // var foo = bar[0].baz;
        defineAndAlias(n.getLastChild().getString());
      } else if (NodeUtil.isAssign(n)) {
        // In case of nested assignment, we only consider the name of the
        // immediate neighbor.
        safeAlias(n.getFirstChild());
      } else if (n.hasChildren()) {
        Node cur = n.getFirstChild();
        do {
          safeAlias(cur);
        } while ((cur = cur.getNext()) != null);
      } else {
        // No name to alias
      }
    }

    private void maybeAliasNamesOnAssign(Node lhs, Node rhs) {
      if ((NodeUtil.isName(lhs) || NodeUtil.isGetProp(lhs)) &&
          containsName(rhs) &&
          !NodeUtil.isFunction(rhs) &&
          !NodeUtil.isNew(rhs)) {
        safeAlias(lhs);
        safeAlias(rhs);
      }
    }

    private void defineAndAlias(String name) {
      graph.defineNameIfNotExists(name, isExtern).setAliased(true);
    }

    private void maybeRecordExport(Node call) {
      Preconditions.checkArgument(NodeUtil.isCall(call));
      Node getProp = call.getFirstChild();
      if (!NodeUtil.isGetProp(getProp)) {
        return;
      }

      String propQName = getProp.getQualifiedName();

      if (propQName == null) {
        return;
      }

      // Keep track of calls to "call" and "apply" because they mess up the name
      // graph.
      if (propQName.endsWith(".call") || propQName.endsWith(".apply")) {
        graph.defineNameIfNotExists(getProp.getFirstChild().getQualifiedName(),
            isExtern).markExposedToCallOrApply();
      }

      if (!"goog.exportSymbol".equals(propQName)) {
        return;
      }

      Node symbol = getProp.getNext();
      if (!NodeUtil.isString(symbol)) {
        return;
      }

      Node obj = symbol.getNext();
      String qName = obj.getQualifiedName();

      if (qName == null || obj.getNext() != null) {
        return;
      }

      graph.defineNameIfNotExists(qName, false).markExported();
    }

    /**
     * @return true if n MUST be a local name reference.
     */
    private boolean isLocalNameReference(NodeTraversal t, Node n) {
      // TODO(user): What happen if it is a reference to an outer local
      // variable (closures)?
      if (NodeUtil.isName(n)) {
        Var v = t.getScope().getVar(n.getString());
        return v != null && v.isLocal();
      }
      return false;
    }

    /**
     * @return true if n MUST be a static name reference.
     */
    private boolean isStaticNameReference(Node n, Scope scope) {
      Preconditions.checkArgument(NodeUtil.isName(n) || NodeUtil.isGetProp(n));
      if (NodeUtil.isName(n)) {
        return true;
      }
      String qName = n.getQualifiedName();
      if (qName == null) {
        return false;
      }
      // TODO(user): This does not always work due to type system bugs.
      return scope.isDeclared(qName, true);
    }

    /**
     * @return true if n MUST be a prototype name reference.
     */
    private boolean isPrototypeNameReference(Node n) {
      if (!NodeUtil.isGetProp(n)) {
        return false;
      }
      JSType type = getType(n.getFirstChild());
      if (type.isUnknownType() || type.isUnionType()) {
        return false;
      }
      return (type instanceof InstanceObjectType || type.autoboxesTo() != null);
    }

    private Name recordStaticNameDefinition(NodeTraversal t, String name,
        JSType type, Node n, Node parent, Node gParent, Node rValue) {
      if (getNamedContainingFunction() != graph.MAIN) {
        // TODO(user): if A.B() defines A.C(), there is a dependence from
        // A.C() -> A.B(). However, this is not important in module code motion
        // and will be ignored (for now).
      }
      if (type.isFunctionType() && type.isConstructor()) {
        return recordClassConstructorOrInterface(
            name, (FunctionType) type, n, parent, parent.getParent(), rValue);
      } else {
        Name symbol = graph.defineNameIfNotExists(name, isExtern);
        symbol.setType(type);
        if (NodeUtil.isAssign(n)) {
          symbol.addAssignmentDeclaration(n);
        } else {
          symbol.addFunctionDeclaration(n);
        }
        return symbol;
      }
    }

    /**
     * @param assign The assignment node, null if it is just a "forward"
     *     declaration for recording the rValue's type.
     */
    private Name recordPrototypePropDefinition(
        NodeTraversal t, Node qName, JSType type,
        @Nullable Node assign, @Nullable Node parent, @Nullable Node gParent) {
      JSType constructor = getType(NodeUtil.getPrototypeClassName(qName));
      FunctionType classType = null;
      String className = null;

      if (constructor instanceof FunctionType && constructor.isConstructor()) {
        // Case where the class has been properly declared with @constructor
        classType = (FunctionType) constructor;
        className = classType.getReferenceName();
      } else {
        // We'll guess it is a constructor even if it didn't have @constructor
        classType = compiler.getTypeRegistry().getNativeFunctionType(
            JSTypeNative.U2U_CONSTRUCTOR_TYPE);
        className = NodeUtil.getPrototypeClassName(qName).getQualifiedName();
      }
      // In case we haven't seen the function yet.
      recordClassConstructorOrInterface(
          className, classType, null, null, null, null);

      String qNameStr = className + ".prototype." +
          NodeUtil.getPrototypePropertyName(qName);
      Name prototypeProp = graph.defineNameIfNotExists(qNameStr, isExtern);
      Preconditions.checkNotNull(
          prototypeProp, qNameStr + " should be in the name graph as a node.");
      if (assign != null) {
        prototypeProp.addAssignmentDeclaration(assign);
      }
      prototypeProp.setType(type);
      return prototypeProp;
    }

    private Reference recordStaticNameUse(
        NodeTraversal t, Node n, Node parent) {
      if (isExtern) {
        // Don't count reference in extern as a use.
        return null;
      } else {
        Reference reference = new Reference(n, parent);
        Name name = graph.defineNameIfNotExists(n.getQualifiedName(), isExtern);
        name.setType(getType(n));
        graph.connect(getNamedContainingFunction(), reference, name);
        return reference;
      }
    }

    private void recordPrototypePropUse(
        NodeTraversal t, Node n, Node parent) {
      Preconditions.checkArgument(NodeUtil.isGetProp(n));
      Node instance = n.getFirstChild();
      JSType instanceType = getType(instance);
      JSType boxedType = instanceType.autoboxesTo();
      instanceType = boxedType != null ? boxedType : instanceType;

      // Retrieves the property.
      ObjectType objType = instanceType.toObjectType();
      Preconditions.checkState(objType != null);

      if (!isExtern) {
        // Don't count reference in extern as a use.
        Reference ref = new Reference(n, parent);

        FunctionType constructor = objType.getConstructor();
        if (constructor != null) {
          String propName = n.getLastChild().getString();
          if (!constructor.getPrototype().hasOwnProperty(propName)) {
            recordSuperClassPrototypePropUse(constructor, propName, ref);
          }

          // TODO(user): TightenType can help a whole lot here.
          recordSubclassPrototypePropUse(constructor, propName, ref);
        } else {
          recordUnknownUse(t, n, parent);
        }
      }
    }

    /**
     * Look for the super class implementation up the tree.
     */
    private void recordSuperClassPrototypePropUse(
        FunctionType classType, String prop, Reference ref) {
      FunctionType superClass = classType.getSuperClassConstructor();
      while (superClass != null) {
        if (superClass.getPrototype().hasOwnProperty(prop)) {
          graph.connect(getNamedContainingFunction(), ref,
              graph.defineNameIfNotExists(
                 superClass.getReferenceName() + ".prototype." + prop, false));
          return;
        } else {
          superClass = superClass.getSuperClassConstructor();
        }
      }
    }

    /**
     * Conservatively assumes that all subclass implementation of this property
     * might be called.
     */
    private void recordSubclassPrototypePropUse(
        FunctionType classType, String prop, Reference ref) {
      if (classType.getPrototype().hasOwnProperty(prop)) {
        graph.connect(getNamedContainingFunction(), ref,
           graph.defineNameIfNotExists(
               classType.getReferenceName() + ".prototype." + prop, false));
      }
      if (classType.getSubTypes() != null) {
        for (FunctionType subclass : classType.getSubTypes()) {
            recordSubclassPrototypePropUse(subclass, prop, ref);
        }
      }
    }

    private void recordUnknownUse(NodeTraversal t, Node n, Node parent) {
      if (isExtern) {
        // Don't count reference in extern as a use.
        return;
      } else {
        Preconditions.checkArgument(NodeUtil.isGetProp(n));
        Reference ref = new Reference(n, parent);
        ref.setUnknown(true);
        unknownNameUse.put(n.getLastChild().getString(),
            new NameUse(getNamedContainingFunction(), ref));
      }
    }

    /**
     * Creates the name in the graph if it does not already exist. Also puts all
     * the properties and prototype properties of this name in the graph.
     */
    private Name recordClassConstructorOrInterface(
        String name, FunctionType type, @Nullable Node n, @Nullable Node parent,
        @Nullable Node gParent, @Nullable Node rhs) {
      Preconditions.checkArgument(type.isConstructor() || type.isInterface());
      Name symbol = graph.defineNameIfNotExists(name, isExtern);
      if (rhs != null) {
        // TODO(user): record the definition.
        symbol.setType(getType(rhs));
        if (NodeUtil.isAssign(n)) {
          symbol.addAssignmentDeclaration(n);
        } else {
          symbol.addFunctionDeclaration(n);
        }
      }
      ObjectType prototype = type.getPrototype();
      for (String prop : prototype.getOwnPropertyNames()) {
        graph.defineNameIfNotExists(
            name + ".prototype." + prop, isExtern);
      }
      return symbol;
    }
  }

  private void connectUnknowns() {
    for (GraphNode<Name, Reference> node : graph.getNodes()) {
      Name name = node.getValue();
      String propName = name.getPropertyName();
      if (propName == null) {
        continue;
      }
      Collection<NameUse> uses = unknownNameUse.get(propName);
      if (uses != null) {
        for (NameUse use : uses) {
          graph.connect(use.name, use.reference, name);
        }
      }
    }
  }

  /**
   * A helper to retrieve the type of a node.
   */
  private JSType getType(Node n) {
    JSType type = n.getJSType();
    if (type == null) {
      if (CONSERVATIVE) {
        throw new RuntimeException("Type system failed us :(");
      } else {
        return compiler.getTypeRegistry().getNativeType(
            JSTypeNative.UNKNOWN_TYPE);
      }
    }
    // Null-ability does not affect the name graph's result.
    return type.restrictByNotNullOrUndefined();
  }

  /**
   * Mark the provided node as the current function that we are analyzing.
   * and add it to the stack of scopes we are inside.
   *
   * @param functionNode node representing current function.
   */
  private void pushContainingFunction(Name functionNode) {
    currentFunctionStack.add(functionNode);
  }

  /**
   * Remove the top item off the containing function stack, and restore the
   * previous containing scope to the be the current containing function.
   */
  private void popContainingFunction() {
    currentFunctionStack.remove(currentFunctionStack.size() - 1);
  }

  /**
   * Find the first containing function that's not an function expression
   * closure.
   */
  private Name getNamedContainingFunction() {
    Name containingFn = null;
    int pos;
    for (pos = currentFunctionStack.size() - 1; pos >= 0; pos = pos - 1) {
      Name cf = currentFunctionStack.get(pos);
      if (cf != graph.UNKNOWN) {
        containingFn = cf;
        break;
      }
    }
    Preconditions.checkNotNull(containingFn);
    return containingFn;
  }

  private static class NameUse {
    private final Name name;
    private final Reference reference;

    private NameUse(Name name, Reference reference) {
      this.name = name;
      this.reference = reference;
    }
  }
}
