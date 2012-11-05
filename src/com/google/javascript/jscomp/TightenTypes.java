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

import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ConcreteType.ConcreteFunctionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteInstanceType;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the set of possible concrete types for every variable, property,
 * function argument, and function return value in the program.  Unlike a normal
 * reference type annotation, a concrete type of A indicates that an instance of
 * A -- not a subclass of A -- is a possible value.
 *
 * Also unlike normal type checking, this pass does not assume that all defined
 * functions are actually called.  Instead, it assumes only that the top-level
 * code is executed plus any implicit calls detected, such as calls to functions
 * exported via goog.exportSymbol or Element.addEventListener.  Hence, this pass
 * also performs a very strict form of dead code detection.  Elimination of dead
 * code will occur because the disambiguation pass can rename all uncalled
 * functions to have distinct names, which will then appear to be uncalled to
 * the normal unused property remover.
 *
 * Since concrete types are all reference types, we only care about the limited
 * set of actions that apply to them:  assignments to variables/properties,
 * method calls, and return statements.  To speed up and simplify the
 * implementation, the first time a scope is processed, we make one pass through
 * it {@link CreateScope} to translate it into a list of Actions.  Each Action
 * can translate itself into a list of assignments:  method calls are just
 * assignments to the parameter variables, while return statements are
 * assignments to a special $return slot.  Each time a scope is (re-)processed,
 * we iterate over the assignments produced by the actions and update the types
 * of the target slots.  Once we complete a pass through all scopes with no
 * changes, we are done.
 *
 */
class TightenTypes implements CompilerPass, ConcreteType.Factory {
  public static final String NON_HALTING_ERROR_MSG =
    "TightenTypes pass appears to be stuck in an infinite loop.";

  /** The compiler that invoked this pass. */
  private final AbstractCompiler compiler;

  /**
   * Map of function type information to their concrete wrappers.  These must be
   * reused so that each declaration has only a single concrete type, which will
   * hold all the known types that flow to its arguments and return value.
   */
  private final Map<Node, ConcreteFunctionType> functionFromDeclaration =
      Maps.newHashMap();

  /**
   * Secondary index of concrete functions by JSType.  This is necessary for
   * retrieving the concrete type of a superclass, where the actual declaration
   * is not at hand.  Note that we must use an identity hash map here because
   * functions are compared using the signature only.
   */
  private final Map<FunctionType, ConcreteFunctionType> functionFromJSType =
      Maps.newIdentityHashMap();

  /**
   * Map of instance type information to their concrete wrappers.  These must be
   * reused so that each property has only one variable, which will store all
   * known types that flow to that variable.
   */
  private final Map<ObjectType, ConcreteInstanceType> instanceFromJSType =
      Maps.newHashMap();

  /**
   * Memoized results of "createTypeIntersection" calls.
   */
  private final Map<ConcreteJSTypePair, ConcreteType> typeIntersectionMemos =
      Maps.newHashMap();

  /** Scope storing the top-level variables and functions. */
  private ConcreteScope topScope;

  TightenTypes(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /** Returns the top scope computed during the pass. */
  ConcreteScope getTopScope() { return topScope; }

  /** Convenience method to get the type registry of the compiler. */
  @Override
  public JSTypeRegistry getTypeRegistry() { return compiler.getTypeRegistry(); }

  /** All concrete instance types encountered during flow analysis. */
  private Set<ConcreteType> allInstantiatedTypes = Sets.newHashSet();

  @Override
  public void process(Node externRoot, Node jsRoot) {
    // Create the scope of top-level variables and functions.
    topScope = new ConcreteScope(null);
    topScope.initForExternRoot(externRoot);
    topScope.initForScopeRoot(jsRoot);

    // Process the assignments in each scope in the working set until no more
    // changes are detected.  Each time a new scope is discovered (starting with
    // the top-level scope), it is added to the working set to be processed.
    // Since changes in almost any scope can affect another, we iterate over all
    // discovered scopes until no further changes occur.

    long maxIterations = 1000;
    long iterations = 0;

    Set<ConcreteScope> workSet = Sets.newHashSet(topScope);
    List<ConcreteScope> workList = Lists.newArrayList(topScope);

    boolean changed;
    do {
      changed = false;
      for (int i = 0; i < workList.size(); ++i) {
        ConcreteScope scope = workList.get(i);
        for (Action action : scope.getActions()) {
          for (Assignment assign : action.getAssignments(scope)) {
            if (assign.slot.addConcreteType(assign.type)) {
              changed = true;
              ConcreteScope varScope = assign.slot.getScope();
              if ((varScope != scope) && !workSet.contains(varScope)) {
                workSet.add(varScope);
                workList.add(varScope);
              }
            }
          }
        }
      }
      Preconditions.checkState(++iterations != maxIterations,
          NON_HALTING_ERROR_MSG);
    } while (changed);
  }

  /**
   * Represents a scope in which a set of slots are declared.  The scope also
   * includes code, which is normalized to a set of actions (which may affect
   * slots in other scopes as well).
   */
  class ConcreteScope implements StaticScope<ConcreteType> {
    private final ConcreteScope parent;
    private final Map<String, ConcreteSlot> slots;
    private final List<Action> actions;

    ConcreteScope(ConcreteScope parent) {
      this.parent = parent;
      this.slots = Maps.newHashMap();
      this.actions = Lists.newArrayList();
    }

    @Override
    public Node getRootNode() { return null; }

    @Override
    public StaticScope<ConcreteType> getParentScope() { return parent; }

    @Override
    public StaticSlot<ConcreteType> getOwnSlot(String name) {
      return slots.get(name);
    }

    @Override
    public StaticSlot<ConcreteType> getSlot(String name) {
      StaticSlot<ConcreteType> var = getOwnSlot(name);
      if (var != null) {
        return var;
      } else if (parent != null) {
        return parent.getSlot(name);
      } else {
        return null;
      }
    }

    /** Returns all the slots in this scope. */
    Collection<ConcreteSlot> getSlots() { return slots.values(); }

    @Override
    public ConcreteType getTypeOfThis() {
      // Since the slot doesn't have a reference to its ConcreteType, we can't
      // reference the ConcreteFunctionType directly to get the typeOfThis.
      ConcreteSlot thisVar = slots.get(ConcreteFunctionType.THIS_SLOT_NAME);
      return (thisVar != null) ? thisVar.getType() : ConcreteType.NONE;
    }

    /** Add a declaration for the given variable. */
    void declareSlot(String name, Node declaration) {
      slots.put(name, new ConcreteSlot(this, name));
    }

    /** Add a declaration for the given variable with the given type. */
    void declareSlot(String name, Node declaration, ConcreteType type) {
      ConcreteSlot var = new ConcreteSlot(this, name);
      var.addConcreteType(type);
      slots.put(name, var);
    }

    /** Returns all the actions performed in the code of this scope. */
    List<Action> getActions() { return actions; }

    /** Finds assignments and variables from the function body. */
    void initForScopeRoot(Node decl) {
      Preconditions.checkNotNull(decl);
      if (decl.isFunction()) {
        decl = decl.getLastChild();
      }
      Preconditions.checkArgument(decl.isBlock());

      NodeTraversal.traverse(compiler, decl, new CreateScope(this, false));
    }

    /** Finds assignments and variables from the given externs. */
    void initForExternRoot(Node decl) {
      Preconditions.checkNotNull(decl);
      Preconditions.checkArgument(decl.isBlock());

      NodeTraversal.traverse(compiler, decl, new CreateScope(this, true));
    }

    /** Adds the given action to the list for the code in this scope. */
    void addAction(Action action) { actions.add(action); }

    @Override public String toString() {
      return getTypeOfThis().toString() + " " + getSlots();
    }
  }

  /** Represents a variable or function declared in a scope. */
  static class ConcreteSlot implements StaticSlot<ConcreteType> {
    private final ConcreteScope scope;
    private final String name;
    private ConcreteType type;

    ConcreteSlot(ConcreteScope scope, String name) {
      this.scope = scope;
      this.name = name;
      this.type = ConcreteType.NONE;
    }

    /** Returns the scope in which this slot exists. */
    ConcreteScope getScope() { return scope; }

    /** Returns the name of this slot in its scope. */
    @Override public String getName() { return name; }

    @Override public ConcreteType getType() { return type; }

    /** Whether this type was inferred rather than declared (always true). */
    @Override public boolean isTypeInferred() { return true; }

    @Override public StaticReference<ConcreteType> getDeclaration() {
      return null;
    }

    @Override public JSDocInfo getJSDocInfo() {
      return null;
    }

    /**
     * Adds the given type to the possible concrete types for this slot.
     * Returns whether the added type was not already known.
     */
    boolean addConcreteType(ConcreteType type) {
      ConcreteType origType = this.type;
      this.type = origType.unionWith(type);
      return !this.type.equals(origType);
    }

    @Override public String toString() {
      return getName() + ": " + getType();
    }
  }

  /**
   * Represents a type of action performed in the body of scope that may affect
   * the concrete types of slot.  Example actions are a function call, a
   * variable assignment, and a property assignment.  The function call will
   * create assignments for each of the function parameters, for the "this"
   * slot, and for the "call" slot.  Property and variable assignment actions
   * create assignments for the property or variable they represent.
   */
  private static interface Action {
    /** Returns all assignments that may occur by this action. */
    Collection<Assignment> getAssignments(ConcreteScope scope);
  }

  /** Represents an assignment to a variable of a set of possible types. */
  private static class Assignment {
    private final ConcreteSlot slot;
    private final ConcreteType type;

    Assignment(ConcreteSlot slot, ConcreteType type) {
      this.slot = slot;
      this.type = type;

      Preconditions.checkNotNull(slot);
      Preconditions.checkNotNull(type);
    }
  }

  /** Records an assignment of an expression to a variable. */
  private class VariableAssignAction implements Action {
    private final ConcreteSlot slot;
    private final Node expression;

    VariableAssignAction(ConcreteSlot slot, Node expr) {
      this.slot = slot;
      this.expression = expr;

      Preconditions.checkNotNull(slot);
      Preconditions.checkNotNull(expr);
    }

    @Override
    public Collection<Assignment> getAssignments(ConcreteScope scope) {
      return Lists.newArrayList(
          new Assignment(slot, inferConcreteType(scope, expression)));
    }
  }

  /** Records an assignment of an expression to a property of an object. */
  private class PropertyAssignAction implements Action {
    private final Node receiver;
    private final String propName;
    private final Node expression;

    PropertyAssignAction(Node receiver, Node expr) {
      this.receiver = receiver;
      this.propName = receiver.getNext().getString();
      this.expression = expr;

      Preconditions.checkNotNull(receiver);
      Preconditions.checkNotNull(propName);
      Preconditions.checkNotNull(expr);
    }

    /**
     * Returns all assignments that could occur as a result of this property
     * assign action. Each type in the receiver is checked for a property
     * {@code propName}, and if that property exists, it is assigned the type
     * of {@code expression}.
     */
    @Override
    public Collection<Assignment> getAssignments(ConcreteScope scope) {
      ConcreteType recvType = inferConcreteType(scope, receiver);
      ConcreteType exprType = inferConcreteType(scope, expression);

      List<Assignment> assigns = Lists.newArrayList();
      for (StaticSlot<ConcreteType> prop
           : recvType.getPropertySlots(propName)) {
        assigns.add(new Assignment((ConcreteSlot) prop, exprType));
      }
      return assigns;
    }
  }

  /** Helper class to build a FunctionCall object. */
  private class FunctionCallBuilder {
    private boolean isNewCall = false;
    private boolean isCallFunction = false;
    private final Node receiver;
    private final Node firstArgument;
    private String propName = null;

    FunctionCallBuilder(Node receiver, Node firstArgument) {
      this.receiver = receiver;
      this.firstArgument = firstArgument;
    }

    FunctionCallBuilder setPropName(String propName) {
      this.propName = propName;
      return this;
    }

    /** Should be called iff this is a new call, e.g. new Object(); */
    FunctionCallBuilder setIsNewCall(boolean isNew) {
      Preconditions.checkState(!(isCallFunction && isNew),
          "A function call cannot be of the form: new Object.call()");

      isNewCall = isNew;
      return this;
    }

    /**
     *  Should be called iff this is a {@code call()} function call,
     *  e.g. Array.prototype.slice.call(arguments, 0);
     */
    FunctionCallBuilder setIsCallFunction() {
      Preconditions.checkState(!isNewCall,
          "A function call cannot be of the form: new Object.call()");

      isCallFunction = true;
      return this;
    }

    Action build() {
      if (isCallFunction) {
        return new NativeCallFunctionCall(receiver, propName, firstArgument);
      } else {
        return new FunctionCall(isNewCall, receiver, propName, firstArgument);
      }
    }
  }

  /**
   * Returns a list of assignments that will result from a function call with
   * the given concrete types.
   */
  private List<Assignment> getFunctionCallAssignments(ConcreteType recvType,
      ConcreteType thisType, List<ConcreteType> argTypes) {
    List<Assignment> assigns = Lists.newArrayList();
    for (ConcreteFunctionType fType : recvType.getFunctions()) {
      assigns.add(new Assignment((ConcreteSlot) fType.getCallSlot(), fType));
      assigns.add(new Assignment((ConcreteSlot) fType.getThisSlot(), thisType));
      for (int i = 0; i < argTypes.size(); ++i) {
        ConcreteSlot variable = (ConcreteSlot) fType.getParameterSlot(i);
        // TODO(johnlenz): Support "arguments" references in function bodies.
        // For now, ignore anonymous arguments.
        if (variable != null) {
          assigns.add(new Assignment(variable, argTypes.get(i)));
        }
      }
    }
    return assigns;
  }

  /**
   * Records a call to a function with a given set of concrete types.  This is
   * used for function calls that originate outside the scope of the user code.
   * E.g. callbacks from an extern function.
   */
  private class ExternFunctionCall implements Action {
    private Node receiver;
    private ConcreteType thisType;
    private List<ConcreteType> argTypes;

    ExternFunctionCall(Node receiver, ConcreteType thisType,
                       List<ConcreteType> argTypes) {
      this.receiver = receiver;
      this.thisType = thisType;
      this.argTypes = argTypes;
    }

    @Override
    public Collection<Assignment> getAssignments(ConcreteScope scope) {
      return getFunctionCallAssignments(inferConcreteType(scope, receiver),
                                        thisType, argTypes);
    }
  }

  /** Records a call to a function with a given set of arguments. */
  private class FunctionCall implements Action {
    private final boolean isNewCall;
    private final Node receiver;
    private final String propName;
    private final Node firstArgument;

    /**
     * The function called is {@code receiver} or, if {@code propName} is
     * non-null, the {@propName} field of {@code receiver}.
     */
    FunctionCall(boolean isNewCall, Node receiver, String propName,
                 Node firstArgument) {
      this.isNewCall = isNewCall;
      this.receiver = receiver;
      this.propName = propName;
      this.firstArgument = firstArgument;

      Preconditions.checkNotNull(receiver);
    }

    @Override
    public Collection<Assignment> getAssignments(ConcreteScope scope) {
      ConcreteType thisType = ConcreteType.NONE;
      ConcreteType recvType = inferConcreteType(scope, receiver);

      // If a property name was specified, then the receiver is actually the
      // type of this and the actual receiver is the type of that property.
      if (propName != null) {
        thisType = recvType;
        recvType = thisType.getPropertyType(propName);
      }

      if (recvType.isAll()) {
        // TODO(user): ensure that this will trigger for code like
        // functions[3]();
        throw new AssertionError(
            "Found call on all type, which makes tighten types useless.");
      }

      // If this is a call to new, then a new instance of the receiver is
      // created and passed in as the value of this.
      if (isNewCall) {
        thisType = ConcreteType.NONE;
        for (ConcreteInstanceType instType
             : recvType.getFunctionInstanceTypes()) {
          thisType = thisType.unionWith(instType);
        }
        boolean added = allInstantiatedTypes.add(thisType);
        if (added) {
          // A new type instance invalidates the cached type intersections.
          typeIntersectionMemos.clear();
        }
      }

      List<ConcreteType> argTypes = Lists.newArrayList();
      for (Node arg = firstArgument; arg != null; arg = arg.getNext()) {
        argTypes.add(inferConcreteType(scope, arg));
      }

      return getFunctionCallAssignments(recvType, thisType, argTypes);
    }
  }

  /** Records a call to the native call() function. */
  private class NativeCallFunctionCall implements Action {
    private final Node receiver;
    private final String propName;
    private final Node firstArgument;

    NativeCallFunctionCall(Node receiver, String propName, Node firstArgument) {
      this.receiver = receiver;
      this.propName = propName;
      this.firstArgument = firstArgument;

      Preconditions.checkNotNull(receiver);
    }

    @Override
    public Collection<Assignment> getAssignments(ConcreteScope scope) {
      ConcreteType thisType = (firstArgument != null)
          ? inferConcreteType(scope, firstArgument)
          : getTopScope().getTypeOfThis();
      ConcreteType recvType = inferConcreteType(scope, receiver);

      if (recvType instanceof ConcreteInstanceType &&
          ((ConcreteInstanceType) recvType).isFunctionPrototype()) {
        recvType = thisType.getPropertyType(propName);
      }
      List<ConcreteType> argTypes = Lists.newArrayList();
      // Skip the first argument for call() as it is the 'this' object.
      for (Node arg = firstArgument.getNext();
           arg != null;
           arg = arg.getNext()) {
        argTypes.add(inferConcreteType(scope, arg));
      }
      return getFunctionCallAssignments(recvType, thisType, argTypes);
    }
  }

  /** Adds all the variables and assignments to a given scope from the code. */
  private class CreateScope extends AbstractShallowCallback {
    private final ConcreteScope scope;
    private final boolean inExterns;

    CreateScope(ConcreteScope scope, boolean inExterns) {
      this.scope = scope;
      this.inExterns = inExterns;
    }

    // TODO(user): handle object literals like { a: new Foo };
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.VAR:
          // Variable declaration, e.g. var a = b;
          Node name;
          for (name = n.getFirstChild(); name != null; name = name.getNext()) {
            if (inExterns) {
              // In externs, we have to trust the type information because there
              // are not necessarily assignments to the variables, calls to the
              // functions, etc.
              scope.declareSlot(name.getString(), n, createType(name, scope));
            } else {
              scope.declareSlot(name.getString(), n);
              if (name.getFirstChild() != null) {
                addActions(createAssignmentActions(
                    name, name.getFirstChild(), n));
              }
            }
          }
          break;

        case Token.GETPROP:
          // Property access, e.g. a.b = c;
          if (inExterns) {
            ConcreteType type = inferConcreteType(getTopScope(), n);
            // We only need to set a type if one hasn't been assigned by
            // something else, e.g. an ASSIGN node.
            if (type.isNone()) {
              ConcreteScope scope =
                  (ConcreteScope) inferConcreteType(getTopScope(),
                      n.getFirstChild()).getScope();
              if (scope != null) {
                type = createType(n.getJSType());
                if (type.isNone() || type.isAll()) {
                  break;
                }
                type = createUnionWithSubTypes(type);
                Node nameNode = n.getLastChild();
                scope.declareSlot(nameNode.getString(), n, type);
              }
            }
          }
          break;

        case Token.FUNCTION:
          // Function declaration, e.g. function Foo() {};
          if (NodeUtil.isFunctionDeclaration(n)) {
            if (!n.getJSType().isNoObjectType()) {
              ConcreteFunctionType type = createConcreteFunction(n, scope);
              scope.declareSlot(n.getFirstChild().getString(), n, type);

              if (inExterns && type.getInstanceType() != null) {
                // We must assume all extern types are instantiated since they
                // can be created by the browser itself.
                allInstantiatedTypes.add(type.getInstanceType());
              }
            }
          }
          break;

        case Token.ASSIGN:
          // Variable assignment, e.g. a = b;
          Node lhs = n.getFirstChild();
          if (inExterns) {
            // Again, we have to trust the externs.
            ConcreteScope scope;
            if (lhs.isGetProp()) {
              ConcreteType type = inferConcreteType(getTopScope(),
                  lhs.getFirstChild());
              scope = (ConcreteScope) type.getScope();
            } else {
              scope = getTopScope();
            }

            if (scope == null) break;

            ConcreteType type = inferConcreteType(getTopScope(), n);
            if (type.isNone() || type.isAll()) {
              break;
            }

            if (type.isFunction()) {
              JSType lhsType = lhs.getJSType();
              if (lhsType == null) {
                break;
              }
              FunctionType funType =
                  lhsType.restrictByNotNullOrUndefined().toMaybeFunctionType();
              if (funType == null) {
                break;
              }
              ConcreteType retType = createType(funType.getReturnType());
              retType = createUnionWithSubTypes(retType);
              ConcreteType newret = type.toFunction().getReturnSlot()
                  .getType().unionWith(retType);
              ((ConcreteScope) type.getScope()).declareSlot(
                  ConcreteFunctionType.RETURN_SLOT_NAME, n, newret);
            }
            scope.declareSlot(lhs.getLastChild().getString(), n, type);
          } else {
            addActions(createAssignmentActions(lhs, n.getLastChild(), n));
          }
          break;

        case Token.NEW:
        case Token.CALL:
          Node receiver = n.getFirstChild();
          if (receiver.isGetProp()) {
            Node first = receiver.getFirstChild();
            // Special case the call() function.
            if ("call".equals(first.getNext().getString())) {
              if (first.isGetProp()) {
                // foo.bar.call()
                addAction(new FunctionCallBuilder(first, receiver.getNext())
                    .setPropName(first.getFirstChild().getNext().getString())
                    .setIsCallFunction()
                    .build());
              } else {
                // bar.call()
                addAction(new FunctionCallBuilder(
                    first, receiver.getNext()).setIsCallFunction()
                          .build());
              }
            } else {
              // foo.bar()
              addAction(new FunctionCallBuilder(first, receiver.getNext())
                  .setPropName(first.getNext().getString())
                  .build());
            }
          } else {
            // foo() or new Foo()
            addAction(new FunctionCallBuilder(receiver, receiver.getNext())
                      .setIsNewCall(n.isNew())
                      .build());
          }
          break;

        case Token.NAME:
          if (parent.isCatch() && parent.getFirstChild() == n) {
            // The variable in a catch statement gets defined in the scope of
            // the catch block. We approximate that, as does the normal type
            // system, by declaring a variable for it in the scope in which the
            // catch is declared.
            scope.declareSlot(n.getString(), n,
                createUnionWithSubTypes(
                  createType(getTypeRegistry().getType("Error")).toInstance()));
          }
          break;

        case Token.RETURN:
          if (n.getFirstChild() != null) {
            addAction(new VariableAssignAction(
                (ConcreteSlot) scope.getOwnSlot(
                    ConcreteFunctionType.RETURN_SLOT_NAME), n.getFirstChild()));
          }
          break;
      }

      Collection<Action> actions = getImplicitActions(n);
      if (actions != null) {
        for (Action action : actions) {
          addAction(action);
        }
      }
    }

    /** Adds the given action to the scope (in non-externs only). */
    private void addAction(Action action) {
      Preconditions.checkState(!inExterns, "Unexpected action in externs.");
      scope.addAction(action);
    }

    /** Adds the given action to the scope (in non-externs only). */
    private void addActions(List<Action> actions) {
      Preconditions.checkState(!inExterns, "Unexpected action in externs.");
      for (Action action : actions) {
        scope.addAction(action);
      }
    }

    /**
     * Returns an action for assigning the right-hand-side to the left or null
     * if this assignment should be ignored.
     */
    private List<Action> createAssignmentActions(
        Node lhs, Node rhs, Node parent) {
      switch (lhs.getType()) {
        case Token.NAME:
          ConcreteSlot var = (ConcreteSlot) scope.getSlot(lhs.getString());
          Preconditions.checkState(var != null,
              "Type tightener could not find variable with name %s",
              lhs.getString());
          return Lists.<Action>newArrayList(
              new VariableAssignAction(var, rhs));

        case Token.GETPROP:
          Node receiver = lhs.getFirstChild();
          return Lists.<Action>newArrayList(
              new PropertyAssignAction(receiver, rhs));

        case Token.GETELEM:
          return Lists.newArrayList();

        default:
          throw new AssertionError(
              "Bad LHS for assignment: " + parent.toStringTree());
      }
    }

    private ExternFunctionCall createExternFunctionCall(
        Node receiver, JSType jsThisType, FunctionType fun) {
      List<ConcreteType> argTypes = Lists.newArrayList();
      ConcreteType thisType;
      if (fun != null) {
        thisType = createType(jsThisType);
        for (Node arg : fun.getParameters()) {
          argTypes.add(createType(arg, scope));
        }
      } else {
        thisType = ConcreteType.NONE;
      }
      return new ExternFunctionCall(receiver, thisType, argTypes);
    }

    private JSType getJSType(Node n) {
      if (n.getJSType() != null) {
        return n.getJSType();
      } else {
        return getTypeRegistry().getNativeType(UNKNOWN_TYPE);
      }
    }

    /**
     * Returns any actions that are implicit in the given code.  This can return
     * null instead of an empty collection if none are found.
     */
    private Collection<Action> getImplicitActions(Node n) {
      switch (n.getType()) {
        case Token.CALL:
          // Functions passed to externs functions are considered called.
          // E.g. window.setTimeout(callback, 100);
          // TODO(user): support global extern function calls if necessary
          // TODO(user): handle addEventListener for the case of an object
          //     implementing the EventListener interface.
          Node receiver = n.getFirstChild();
          if (!inExterns && receiver.isGetProp()) {
            return getImplicitActionsFromCall(n, receiver.getJSType());
          }
          break;

        case Token.ASSIGN:
          Node lhs = n.getFirstChild();
          // Functions assigned to externs properties are considered called.
          // E.g. element.onclick = function handle(evt) {};
          if (!inExterns && lhs.isGetProp()) {
            return getImplicitActionsFromProp(lhs.getFirstChild().getJSType(),
                lhs.getLastChild().getString(), n.getLastChild());
          }
          break;
      }
      return null;
    }

    private Collection<Action> getImplicitActionsFromCall(
        Node n, JSType recvType) {
      Node receiver = n.getFirstChild();
      if (recvType.isUnionType()) {
        List<Action> actions = Lists.newArrayList();
        for (JSType alt : recvType.toMaybeUnionType().getAlternates()) {
          actions.addAll(getImplicitActionsFromCall(n, alt));
        }
        return actions;
      } else if (!(recvType.isFunctionType())) {
        return Lists.<Action>newArrayList();
      }

      ObjectType objType = ObjectType.cast(
          getJSType(receiver.getFirstChild())
          .restrictByNotNullOrUndefined());
      String prop = receiver.getLastChild().getString();
      if (objType != null &&
          (objType.isPropertyInExterns(prop)) &&
          (recvType.toMaybeFunctionType()).getParameters() != null) {
        List<Action> actions = Lists.newArrayList();

        // Look for a function type in the argument list.
        Iterator<Node> paramIter =
            (recvType.toMaybeFunctionType()).getParameters().iterator();
        Iterator<Node> argumentIter = n.children().iterator();
        argumentIter.next(); // Skip the function name.
        while (paramIter.hasNext() && argumentIter.hasNext()) {
          Node arg = argumentIter.next();
          Node param = paramIter.next();
          if (arg.getJSType() != null && arg.getJSType().isFunctionType()) {
            actions.addAll(getImplicitActionsFromArgument(
                arg,
                arg.getJSType().toMaybeFunctionType().getTypeOfThis()
                    .toObjectType(),
                param.getJSType()));
          }
        }
        return actions;
      }
      return Lists.<Action>newArrayList();
    }

    private Collection<Action> getImplicitActionsFromArgument(
        Node arg, ObjectType thisType, JSType paramType) {
      if (paramType.isUnionType()) {
        List<Action> actions = Lists.newArrayList();
        for (JSType paramAlt : paramType.toMaybeUnionType().getAlternates()) {
          actions.addAll(
              getImplicitActionsFromArgument(arg, thisType, paramAlt));
        }
        return actions;
      } else if (paramType.isFunctionType()) {
        return Lists.<Action>newArrayList(createExternFunctionCall(
            arg, thisType, paramType.toMaybeFunctionType()));
      } else {
        return Lists.<Action>newArrayList(createExternFunctionCall(
            arg, thisType, null));
      }
    }

    private Collection<Action> getImplicitActionsFromProp(
        JSType jsType, String prop, Node fnNode) {
      List<Action> actions = Lists.newArrayList();
      if (jsType.isUnionType()) {
        boolean found = false;
        for (JSType alt : jsType.toMaybeUnionType().getAlternates()) {
          ObjectType altObj = ObjectType.cast(alt);
          if (altObj != null) {
            actions.addAll(getImplicitActionsFromPropNonUnion(
                  altObj, prop, fnNode));
            if (altObj.hasProperty(prop)) {
              found = true;
            }
          }
        }
        if (found) {
          return actions;
        }
      } else {
        ObjectType objType = ObjectType.cast(jsType);
        if (objType != null &&
            !objType.isUnknownType() && objType.hasProperty(prop)) {
          return getImplicitActionsFromPropNonUnion(objType, prop, fnNode);
        }
      }

      // If we didn't find a type that has the property, then check if there
      // exists a property with this name anywhere in the externs.
      for (ObjectType type :
               getTypeRegistry().getEachReferenceTypeWithProperty(prop)) {
        actions.addAll(
            getImplicitActionsFromPropNonUnion(
                  type, prop, fnNode));
      }
      return actions;
    }

    private Collection<Action> getImplicitActionsFromPropNonUnion(
        ObjectType jsType, String prop, Node fnNode) {
      JSType propType = jsType.getPropertyType(prop)
          .restrictByNotNullOrUndefined();
      if (jsType.isPropertyInExterns(prop) && propType.isFunctionType()) {
        ObjectType thisType = jsType;
        if (jsType.isFunctionPrototypeType()) {
          thisType = thisType.getOwnerFunction().getInstanceType();
        }
        FunctionType callType = propType.toMaybeFunctionType();
        Action action = createExternFunctionCall(
            fnNode, thisType, callType);
        return Lists.<Action>newArrayList(action);
      }
      return Lists.<Action>newArrayList();
    }
  }

  /** Returns a concrete type from the JSType of the given variable. */
  private ConcreteType createType(Node name, ConcreteScope scope) {
    Preconditions.checkNotNull(name);
    Preconditions.checkArgument(name.isName());

    if (name.getJSType() == null) {
      return ConcreteType.ALL;
    }

    if ((name.getFirstChild() != null)
        && (name.getFirstChild().isFunction())) {
      return createConcreteFunction(name.getFirstChild(), scope);
    }

    return createType(name.getJSType());
  }

  /** Returns a concrete type from the given JSType. */
  private ConcreteType createType(JSType jsType) {
    if (jsType.isUnknownType() || jsType.isEmptyType()) {
      return ConcreteType.ALL;
    }

    if (jsType.isUnionType()) {
      ConcreteType type = ConcreteType.NONE;
      for (JSType alt : jsType.toMaybeUnionType().getAlternates()) {
        type = type.unionWith(createType(alt));
      }
      return type;
    }

    if (jsType.isFunctionType()) {
      if (getConcreteFunction(jsType.toMaybeFunctionType()) != null) {
        return getConcreteFunction(jsType.toMaybeFunctionType());
      }
      // Since we don't have a declaration, it's not concrete.
      return ConcreteType.ALL;
    }

    if (jsType.isObject()) {
      return createConcreteInstance(jsType.toObjectType());
    }

    return ConcreteType.NONE;  // Not a reference type.
  }

  /**
   * Returns a concrete type from the given JSType that includes the concrete
   * types for subtypes and implementing types for any interfaces.
   */
  private ConcreteType createTypeWithSubTypes(JSType jsType) {
    ConcreteType ret = ConcreteType.NONE;
    if (jsType.isUnionType()) {
      for (JSType alt : jsType.toMaybeUnionType().getAlternates()) {
        ret = ret.unionWith(createTypeWithSubTypes(alt));
      }
    } else {
      ObjectType instType = ObjectType.cast(jsType);
      if (instType != null &&
          instType.getConstructor() != null &&
          instType.getConstructor().isInterface()) {
        Collection<FunctionType> implementors =
            getTypeRegistry().getDirectImplementors(instType);

        for (FunctionType implementor : implementors) {
          ret = ret.unionWith(createTypeWithSubTypes(
              implementor.getInstanceType()));
        }
      } else {
        ret = ret.unionWith(createUnionWithSubTypes(createType(jsType)));
      }
    }
    return ret;
  }

  /** Computes the concrete types that can result from the given expression. */
  ConcreteType inferConcreteType(ConcreteScope scope, Node expr) {
    Preconditions.checkNotNull(scope);
    Preconditions.checkNotNull(expr);
    ConcreteType ret;
    switch (expr.getType()) {
      case Token.NAME:
        StaticSlot<ConcreteType> slot = scope.getSlot(expr.getString());

        if (slot != null) {
          ret = slot.getType();
        } else {
          // This should occur only for extern variables, which we are assuming
          // do not ever get assigned instances of user types.
          ret = ConcreteType.ALL;
        }
        break;

      case Token.THIS:
        ret = scope.getTypeOfThis();
        break;

      case Token.ASSIGN:
        // Using the right-hand side is more specific since the left hand side
        // is a variable of some sort that can be assigned elsewhere.
        ret = inferConcreteType(scope, expr.getLastChild());
        break;

      case Token.COMMA:
        ret = inferConcreteType(scope, expr.getLastChild());
        break;

      case Token.AND:
        // Since a reference type is always true, only the right hand side could
        // actually be returned.
        ret = inferConcreteType(scope, expr.getLastChild());
        break;

      case Token.OR:
        ret = inferConcreteType(scope, expr.getFirstChild()).unionWith(
                   inferConcreteType(scope, expr.getLastChild()));
        break;

      case Token.HOOK:
        ret = inferConcreteType(scope,
                   expr.getFirstChild().getNext()).unionWith(
                       inferConcreteType(scope, expr.getLastChild()));
        break;

      case Token.GETPROP:
        ConcreteType recvType = inferConcreteType(scope, expr.getFirstChild());
        if (recvType.isAll()) {
          ret = recvType;
          break;
        }
        Node prop = expr.getLastChild();
        String propName = prop.getString();
        ConcreteType type = recvType.getPropertyType(propName);
        if ("prototype".equals(propName)) {
          for (ConcreteFunctionType funType : recvType.getFunctions()) {
            type = type.unionWith(funType.getPrototypeType());
          }
        } else if (compiler.getCodingConvention()
                   .isSuperClassReference(propName)) {
          for (ConcreteFunctionType superType : recvType.getSuperclassTypes()) {
            type = type.unionWith(superType.getPrototypeType());
          }
        } else if ("call".equals(propName)) {
          type = recvType;
        }
        ret = type;
        break;

      case Token.GETELEM:
        ret = ConcreteType.ALL;
        break;

      case Token.CALL:
        // TODO(user): Support apply on functions.
        // TODO(user): Create goog.bind that curries some arguments.
        ConcreteType targetType =
            inferConcreteType(scope, expr.getFirstChild());
        if (targetType.isAll()) {
          ret = targetType;
          break;
        }
        ret = ConcreteType.NONE;
        for (ConcreteFunctionType funType : targetType.getFunctions()) {
          ret = ret.unionWith(funType.getReturnSlot().getType());
        }
        break;

      case Token.NEW:
        ConcreteType constructorType =
            inferConcreteType(scope, expr.getFirstChild());
        if (constructorType.isAll()) {
          throw new AssertionError("Attempted new call on all type!");
        }
        ret = ConcreteType.NONE;
        for (ConcreteInstanceType instType
             : constructorType.getFunctionInstanceTypes()) {
          ret = ret.unionWith(instType);
        }
        allInstantiatedTypes.add(ret);
        break;

      case Token.FUNCTION:
        ret = createConcreteFunction(expr, scope);
        break;

      case Token.OBJECTLIT:
        if ((expr.getJSType() != null) && !expr.getJSType().isUnknownType()) {
          JSType exprType = expr.getJSType().restrictByNotNullOrUndefined();
          ConcreteType inst = createConcreteInstance(exprType.toObjectType());
          allInstantiatedTypes.add(inst);
          ret = inst;
        } else {
          ret = ConcreteType.ALL;
        }
        break;

      case Token.ARRAYLIT:
        ObjectType arrayType = (ObjectType) getTypeRegistry()
            .getNativeType(JSTypeNative.ARRAY_TYPE);
        ConcreteInstanceType inst = createConcreteInstance(arrayType);
        allInstantiatedTypes.add(inst);
        ret = inst;
        break;

      default:
        ret = ConcreteType.NONE;
    }
    return createTypeIntersection(ret, expr.getJSType());
  }

  private ConcreteType createTypeIntersection(
      ConcreteType concreteType, JSType jsType) {
    // TODO(johnlenz): Even with memoizing all the time of this pass is still
    // spent in this function (due to invalidation caused by changes to
    // allInstantiatedTypes), specifically calls to ConcreteUnionType.unionWith
    ConcreteJSTypePair key = new ConcreteJSTypePair(concreteType, jsType);
    ConcreteType ret = typeIntersectionMemos.get(key);
    if (ret != null) {
      return ret;
    }

    if (jsType == null || jsType.isUnknownType() || concreteType.isNone()) {
      ret = concreteType;
    } else if (concreteType.isUnion() || concreteType.isSingleton()) {
      ret = concreteType.intersectWith(createTypeWithSubTypes(jsType));
    } else {
      Preconditions.checkState(concreteType.isAll());
      ret = createTypeWithSubTypes(jsType);
    }
    ret = ret.intersectWith(ConcreteType.createForTypes(allInstantiatedTypes));

    // Keep all function types, as restricting to instantiated types will only
    // keep instance types.
    // TODO(user): only keep functions that match the JS type.
    for (ConcreteFunctionType functionType : concreteType.getFunctions()) {
      ret = ret.unionWith(functionType);
    }

    // The prototype type is special as it should only appear from a direct
    // reference to Foo.prototype, and not via a type cast, thus, do not filter
    // them out.  We do not include them in the list of instantiated types.
    for (ConcreteInstanceType prototype : concreteType.getPrototypeTypes()) {
      ret = ret.unionWith(prototype);
    }

    // Anonymous object types and enums will get removed in the createForTypes
    // call, so add them back in as well.
    for (ConcreteInstanceType instance : concreteType.getInstances()) {
      if (!instance.instanceType.isInstanceType()
          && !instance.isFunctionPrototype()) {
        ret = ret.unionWith(instance);
      }
    }

    typeIntersectionMemos.put(key, ret);
    return ret;
  }

  @Override
  public ConcreteFunctionType createConcreteFunction(
      Node decl, StaticScope<ConcreteType> parent) {
    ConcreteFunctionType funType = functionFromDeclaration.get(decl);
    if (funType == null) {
      functionFromDeclaration.put(decl,
          funType = new ConcreteFunctionType(this, decl, parent));
      if (decl.getJSType() != null) {
        functionFromJSType.put(decl.getJSType().toMaybeFunctionType(), funType);
      }
    }
    return funType;
  }

  @Override
  public ConcreteInstanceType createConcreteInstance(ObjectType instanceType) {
    // This should be an instance or function prototype object, not a function.
    Preconditions.checkArgument(
        !instanceType.isFunctionType() ||
        instanceType == getTypeRegistry().getNativeType(U2U_CONSTRUCTOR_TYPE));
    ConcreteInstanceType instType = instanceFromJSType.get(instanceType);
    if (instType == null) {
      instanceFromJSType.put(instanceType,
          instType = new ConcreteInstanceType(this, instanceType));
    }
    return instType;
  }

  /** Returns the (already created) function with the given declaration. */
  ConcreteFunctionType getConcreteFunction(Node decl) {
    return functionFromDeclaration.get(decl);
  }

  /** Returns the function (if any) for the given node. */
  @Override
  public ConcreteFunctionType getConcreteFunction(FunctionType functionType) {
    return functionFromJSType.get(functionType);
  }

  /** Returns the function (if any) for the given node. */
  @Override
  public ConcreteInstanceType getConcreteInstance(ObjectType instanceType) {
    return instanceFromJSType.get(instanceType);
  }

  @Override
  public StaticScope<ConcreteType> createFunctionScope(
      Node decl, StaticScope<ConcreteType> parent) {
    ConcreteScope scope = new ConcreteScope((ConcreteScope) parent);
    scope.declareSlot(ConcreteFunctionType.CALL_SLOT_NAME, decl);
    scope.declareSlot(ConcreteFunctionType.THIS_SLOT_NAME, decl);
    scope.declareSlot(ConcreteFunctionType.RETURN_SLOT_NAME, decl);
    for (Node n = decl.getFirstChild().getNext().getFirstChild();
         n != null;
         n = n.getNext()) {
      scope.declareSlot(n.getString(), n);
    }
    // TODO(user): Create an 'arguments' variable that returns the union
    //     of the concrete types of all parameters.
    scope.initForScopeRoot(decl.getLastChild());
    return scope;
  }

  @Override
  public StaticScope<ConcreteType> createInstanceScope(
      ObjectType instanceType) {
    ConcreteScope parentScope = null;
    ObjectType implicitProto = instanceType.getImplicitPrototype();
    if (implicitProto != null && !implicitProto.isUnknownType()) {
      ConcreteInstanceType prototype = createConcreteInstance(implicitProto);
      parentScope = (ConcreteScope) prototype.getScope();
    }
    ConcreteScope scope = new ConcreteScope(parentScope);
    for (String propName : instanceType.getOwnPropertyNames()) {
      scope.declareSlot(propName, null);
    }
    return scope;
  }

  /**
   * Returns a ConcreteType that is the union of the given type and all of its
   * subtypes.  This assumes that the passed in type is an instance type,
   * otherwise an empty set is returned. The returned set will be instance
   * types.
   */
  ConcreteType createUnionWithSubTypes(ConcreteType type) {
    Set<ConcreteType> set = null;
    if (type.isInstance()) {
      set = getSubTypes(type.toInstance());
    }

    return ConcreteType.createForTypes(set).unionWith(type);
  }

  /** Returns the set of subtypes of the given type. */
  private Set<ConcreteType> getSubTypes(ConcreteInstanceType type) {
    if (type.getConstructorType() == null) {
      return null;
    }

    Set<ConcreteType> set = Sets.newHashSet();
    getSubTypes(type.getConstructorType().getJSType(), set);
    return set;
  }

  /**
   * Adds all subtypes of the given type to the provided set.
   * @return false if the all type was encountered, else true.
   */
  private boolean getSubTypes(FunctionType type, Set<ConcreteType> set) {
    if (type.getSubTypes() != null) {
      for (FunctionType sub : type.getSubTypes()) {
        ConcreteType concrete = createType(sub);
        if (concrete.isFunction()
            && concrete.toFunction().getInstanceType() != null) {
          concrete = concrete.toFunction().getInstanceType();
          if (!set.contains(concrete)) {
            set.add(concrete);
            if (!getSubTypes(sub, set)) {
              return false;
            }
          }
        } else {
          // The only time we should find a subtype that doesn't have an
          // instance type is for the odd case of ActiveXObject, which is
          // of the NoObject type and will be returned as a subtype of Object.
          set.clear();
          set.add(ConcreteType.ALL);
          return false;
        }
      }
    }
    return true;
  }

  /**
   * A simple class used to pair a concrete type and a JS type.  Used to
   * memoize the results of a "createTypeIntersection" call.
   */
  static class ConcreteJSTypePair {
    final ConcreteType concrete;
    final JSType jstype;
    final int hashcode;

    ConcreteJSTypePair(ConcreteType concrete, JSType jstype) {
      this.concrete = concrete;
      this.jstype = jstype;
      this.hashcode = concrete.hashCode() + getJSTypeHashCode();
    }

    private int getJSTypeHashCode() {
      return jstype != null ? jstype.hashCode() : 0;
    }

    private boolean equalsJSType(JSType jsType) {
      if (jsType == null || jstype == null) {
        return jstype == jsType;
      } else {
        return jsType.equals(this.jstype);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof ConcreteJSTypePair) {
        ConcreteJSTypePair pair = (ConcreteJSTypePair) o;
        if ((pair.concrete.equals(this.concrete)
            && equalsJSType(pair.jstype))) {
          return true;
        }
      }
      return false;
    }

    @Override
    public int hashCode() {
      return hashcode;
    }
  }
}
