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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Converts {@code super()} calls. */
public final class Es6ConvertSuperConstructorCalls implements NodeTraversal.Callback {
  private static final String TMP_ERROR = "$jscomp$tmp$error";
  private static final String SUPER_THIS = "$jscomp$super$this";

  /** Stores superCalls for a constructor. */
  private static final class ConstructorData {
    final Node constructor;
    final List<Node> superCalls;

    ConstructorData(Node constructor) {
      this.constructor = constructor;
      superCalls = new ArrayList<>();
    }
  }

  private final AbstractCompiler compiler;
  private final Deque<ConstructorData> constructorDataStack;
  private final AstFactory astFactory;
  private GlobalNamespace globalNamespace;

  public Es6ConvertSuperConstructorCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.astFactory = compiler.createAstFactory();
    this.constructorDataStack = new ArrayDeque<>();
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      // TODO(bradfordcsmith): Avoid creating data for non-constructor functions.
      constructorDataStack.push(new ConstructorData(n));
    } else if (n.isSuper()) {
      // super(args) or super.prop
      checkState(n.isFirstChildOf(parent), parent);
      if (parent.isGetProp()) {
        // TODO(bradfordcsmith): `super.prop` should have been removed before this code executes,
        //     but instead is being left untranspiled when there's no `extends` clause, so
        //     we have to report that problem here.
        t.report(n, Es6ToEs3Util.CANNOT_CONVERT_YET, "super access with no extends clause");
        return false;
      }
      // must be super(args)
      checkState(parent.isCall(), parent);
      ConstructorData constructorData = checkNotNull(constructorDataStack.peek());
      constructorData.superCalls.add(parent);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    ConstructorData constructorData = constructorDataStack.peek();
    if (constructorData != null && n == constructorData.constructor) {
      constructorDataStack.pop();
      visitSuper(t, constructorData);
    }
  }

  private void visitSuper(NodeTraversal t, ConstructorData constructorData) {
    // NOTE: When this pass runs:
    // -   ES6 classes have already been rewritten as ES5 functions.
    // -   All subclasses have $jscomp.inherits() calls connecting them to their parent class.
    // -   All instances of `super` that are not super constructor calls have been rewritten.
    Node constructor = constructorData.constructor;
    List<Node> superCalls = constructorData.superCalls;
    if (superCalls.isEmpty()) {
      return; // nothing to do
    }
    if (constructor.isFromExterns()) {
      // This class is defined in an externs file, so it's only a stub, not the actual
      // implementation that should be instantiated.
      // A call to super() shouldn't actually exist for a stub and is problematic to transpile,
      // so just drop it.
      for (Node superCall : superCalls) {
        Node enclosingStatement = NodeUtil.getEnclosingStatement(superCall);
        Node enclosingScope = enclosingStatement.getParent();
        enclosingStatement.detach();
        compiler.reportChangeToEnclosingScope(enclosingScope);
      }
    } else {
      // Find the `foo.SuperClass` part of `$jscomp.inherits(foo.SubClass, foo.SuperClass)`
      Node superClassNameNode = getSuperClassQNameNode(constructor);

      String superClassQName = superClassNameNode.getQualifiedName();
      JSType thisType = getTypeOfThisForConstructor(constructorData.constructor);
      if (isNativeObjectClass(t, superClassQName)) {
        // There's no need to call Object as a super constructor, so just replace the call with
        // `this`, which is its correct return value.
        // TODO(bradfordcsmith): Although unlikely, super() could have argument expressions with
        //     side-effects.
        for (Node superCall : superCalls) {
          Node thisNode = astFactory.createThis(thisType).srcref(superCall);
          superCall.replaceWith(thisNode);
          compiler.reportChangeToEnclosingScope(thisNode);
        }
      } else if (isKnownNativeClass(t, superClassQName)) {
        // Although we're transpiling down to ES5, it's quite possible that the code will end up
        // running in an environment where native classes are ES6 classes.
        // To correctly extend them with the ES5 classes we're generating here, we must use
        // `$jscomp.construct`, which is our wrapper around `Reflect.construct`.
        convertSuperCallsToJsCompConstructCalls(
            t, constructor, superCalls, superClassNameNode, thisType);
      } else if (isNativeErrorClass(t, superClassQName)) {
        // TODO(bradfordcsmith): It might be better to use $jscomp.construct() for these instead
        // of our custom-made, Error-specific workaround.
        for (Node superCall : superCalls) {
          Node newSuperCall = createNewSuperCall(superClassNameNode, superCall, thisType);
          replaceNativeErrorSuperCall(superCall, newSuperCall);
        }
      } else if (isKnownToReturnOnlyUndefined(superClassQName)) {
        // super() will not change the value of `this`.
        for (Node superCall : superCalls) {
          Node newSuperCall = createNewSuperCall(superClassNameNode, superCall, thisType);
          Node superCallParent = superCall.getParent();
          if (superCallParent.hasOneChild() && NodeUtil.isStatement(superCallParent)) {
            // super() is a statement unto itself
            superCall.replaceWith(newSuperCall);
          } else {
            // super() is part of an expression, so it must return `this`.
            superCall.replaceWith(
                astFactory
                    .createComma(newSuperCall, astFactory.createThis(thisType))
                    .srcrefTreeIfMissing(superCall));
          }
          compiler.reportChangeToEnclosingScope(superCallParent);
        }
      } else {
        // Either the superclass constructor returns a value, or we cannot find its definition in
        // the sources, so we don't know if it does.
        //
        // 1. We must use the value it returns, if defined, as the 'this' value in the constructor
        //    we're currently transpiling, and we must also return it from this constructor.
        // 2. It may be an ES6 class defined outside of the sources we can see.
        //
        // The code below works as long as the class we're extending is an ES5 class, but will
        // break if we're extending an ES6 class (#2), because it calls the superclass constructor
        // without using `new` or `Reflect.construct()`
        // TODO(b/36789413): We should use $jscomp.construct() here to avoid breakage when extending
        // an ES6 class.
        Node constructorBody = checkNotNull(constructor.getChildAtIndex(2));
        Node firstStatement = constructorBody.getFirstChild();
        Node firstSuperCall = superCalls.get(0);

        if (constructorBody.hasOneChild()
            && firstStatement.isExprResult()
            && firstStatement.hasOneChild()
            && firstStatement.getFirstChild() == firstSuperCall) {
          checkState(superCalls.size() == 1, constructor);
          // Super call is the entire constructor, so just replace it with.
          // `return <newSuperCall> || this;`
          Node newReturn =
              astFactory.createOr(
                  createNewSuperCall(
                      superClassNameNode, superCalls.get(0), superCalls.get(0).getJSType()),
                  astFactory.createThis(thisType));
          firstStatement.replaceWith(IR.returnNode(newReturn).srcrefTreeIfMissing(firstStatement));
        } else {
          final JSType typeOfThis = getTypeOfThisForConstructor(constructor);
          // `this` -> `$jscomp$super$this` throughout the constructor body,
          // except for super() calls.
          updateThisToSuperThis(typeOfThis, constructorBody, superCalls);
          // Start constructor with `var $jscomp$super$this;`
          constructorBody.addChildToFront(
              IR.var(astFactory.createName(SUPER_THIS, typeOfThis)).srcrefTree(constructorBody));
          // End constructor with `return $jscomp$super$this;`
          constructorBody.addChildToBack(
              IR.returnNode(astFactory.createName(SUPER_THIS, typeOfThis))
                  .srcrefTree(constructorBody));
          // Replace each super() call with `($jscomp$super$this = <newSuperCall> || this)`
          for (Node superCall : superCalls) {
            Node newSuperCall = createNewSuperCall(superClassNameNode, superCall, typeOfThis);
            superCall.replaceWith(
                astFactory
                    .createAssign(
                        astFactory.createName(SUPER_THIS, typeOfThis),
                        astFactory.createOr(newSuperCall, astFactory.createThis(typeOfThis)))
                    .srcrefTreeIfMissing(superCall));
          }
        }
        compiler.reportChangeToEnclosingScope(constructorBody);
      }
    }
  }

  /**
   * Change calls to `super` to use `$jscomp.construct` instead.
   *
   * <pre><code>
   *   // note that conversion of the ES6 class to ES5 happens before this pass.
   *   // we're just cleaning up the super() calls now
   *   var Foo = function(arg1, arg2) {
   *     super(arg1);
   *     this.prop = arg2;
   *   }
   *   // becomes
   *   var Foo = function(arg1, arg2) {
   *     // tmp var and return are necessary, because $jscomp.construct() always creates a new
   *     // object to be used as `this`
   *     var $jscomp$super$this;
   *     $jscomp$super$this = $jscomp.construct(SuperClassName, [arg1], this.constructor);
   *     $jscomp$super$this.prop = arg2
   *     return $jscomp$super$this;
   *   }
   * </code></pre>
   */
  private void convertSuperCallsToJsCompConstructCalls(
      NodeTraversal t,
      Node constructor,
      List<Node> superCalls,
      Node superClassNameNode,
      JSType thisType) {
    Node constructorBody = checkNotNull(constructor.getChildAtIndex(2));
    Node firstStatement = constructorBody.getFirstChild();
    // A constructor body with no call to `super()` is a syntax error for a class that has an
    // extends clause. An error should have been reported and we should never reach this point
    // for an empty constructor body.
    checkNotNull(firstStatement, "Empty constructor body");
    Node firstSuperCall = superCalls.get(0);

    if (constructorBody.hasOneChild()
        && firstStatement.isExprResult()
        && firstStatement.hasOneChild()
        && firstStatement.getFirstChild() == firstSuperCall) {
      checkState(superCalls.size() == 1, constructor);
      // Super call is the entire constructor, so just replace it with.
      // `return $jscomp.construct(SuperClassName, [args], this.constructor);`
      firstStatement.replaceWith(
          astFactory.createReturn(
              createJSCompConstructorCall(
                  t.getScope(), superClassNameNode, firstSuperCall, thisType)));
    } else {
      final JSType typeOfThis = getTypeOfThisForConstructor(constructor);
      // `this` -> `$jscomp$super$this` throughout the constructor body,
      // except for super() calls.
      updateThisToSuperThis(typeOfThis, constructorBody, superCalls);
      // Start constructor with `var $jscomp$super$this;`
      constructorBody.addChildToFront(
          astFactory.createSingleVarNameDeclaration(SUPER_THIS).srcrefTree(constructorBody));
      // End constructor with `return $jscomp$super$this;`
      constructorBody.addChildToBack(
          astFactory
              .createReturn(astFactory.createName(SUPER_THIS, typeOfThis))
              .srcrefTree(constructorBody));
      // Replace each super() call with `($jscomp$super$this = $jscomp.construct(...))`
      for (Node superCall : superCalls) {
        superCall.replaceWith(
            astFactory
                .createAssign(
                    astFactory.createName(SUPER_THIS, typeOfThis).srcref(superCall),
                    createJSCompConstructorCall(
                        t.getScope(), superClassNameNode, superCall, thisType))
                .srcref(superCall));
      }
    }
    compiler.reportChangeToEnclosingScope(constructorBody);
  }

  private boolean isKnownToReturnOnlyUndefined(String functionQName) {
    if (globalNamespace == null) {
      return false;
    }
    Name globalName = globalNamespace.getSlot(functionQName);
    if (globalName == null) {
      return false;
    }

    Ref declarationRef = globalName.getDeclaration();
    if (declarationRef == null) {
      for (Ref ref : globalName.getRefs()) {
        if (ref.isSet()) {
          declarationRef = ref;
        }
      }
    }
    if (declarationRef == null) {
      return false;
    }

    Node declaredVarOrProp = declarationRef.getNode();
    if (declaredVarOrProp.isFromExterns()) {
      return false;
    }

    Node declaration = declaredVarOrProp.getParent();
    Node declaredValue = null;
    if (declaration.isFunction()) {
      declaredValue = declaration;
    } else if (NodeUtil.isNameDeclaration(declaration) && declaredVarOrProp.isName()) {
      if (declaredVarOrProp.hasChildren()) {
        declaredValue = checkNotNull(declaredVarOrProp.getFirstChild());
      } else {
        return false; // Declaration without an assigned value.
      }
    } else if (declaration.isAssign() && declaration.getFirstChild() == declaredVarOrProp) {
      declaredValue = checkNotNull(declaration.getSecondChild());
    } else if (declaration.isObjectLit() && declaredVarOrProp.hasOneChild()){
      declaredValue = checkNotNull(declaredVarOrProp.getFirstChild());
    } else {
      throw new IllegalStateException(
          "Unexpected declaration format:\n" + declaration.toStringTree());
    }

    if (declaredValue.isFunction()) {
      Node functionBody = checkNotNull(declaredValue.getChildAtIndex(2));
      return !(new UndefinedReturnValueCheck().mayReturnDefinedValue(functionBody));
    } else if (declaredValue.isQualifiedName()) {
      return isKnownToReturnOnlyUndefined(declaredValue.getQualifiedName());
    } else {
      // TODO(bradfordcsmith): What cases are these? Can we do better?
      return false;
    }
  }

  private class UndefinedReturnValueCheck {
    private boolean foundNonEmptyReturn;

    boolean mayReturnDefinedValue(Node functionBody) {
      foundNonEmptyReturn = false;
      NodeTraversal.Callback checkForDefinedReturnValue =
          new NodeTraversal.AbstractShallowCallback() {

            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (!foundNonEmptyReturn) {
                if (n.isReturn()
                    && n.hasChildren()
                    && !n.getFirstChild().matchesName("undefined")) {
                  foundNonEmptyReturn = true;
                }
              }
            }
          };
      NodeTraversal.traverse(compiler, functionBody, checkForDefinedReturnValue);
      return foundNonEmptyReturn;
    }
  }

  /**
   * Returns a transpiled version of the super constructor call.
   *
   * <p>The children of the passed in `superCall` are all removed from it by this method, but the
   * existing call itself is not replaced in the AST yet. The returned node is not yet attached to
   * the AST.
   */
  private Node createNewSuperCall(Node superClassQNameNode, Node superCall, JSType thisType) {
    checkArgument(superClassQNameNode.isQualifiedName(), superClassQNameNode);
    checkArgument(superCall.isCall(), superCall);

    Node callee = superCall.removeFirstChild();
    checkState(callee.isSuper(), callee);

    List<Node> args = new ArrayList<>();
    boolean hasSpreadArg = false;
    while (superCall.hasChildren()) {
      final Node arg = superCall.removeFirstChild();
      hasSpreadArg = hasSpreadArg || arg.isSpread();
      args.add(arg);
    }

    // Node to which args should be appended
    if (hasSpreadArg) {
      // We want to convert
      //
      // super(x, ...params, y)
      // to
      // Foo.apply(this, [x, ...params, y])
      //
      // because, after transpilation of spread this becomes
      //
      // Foo.apply(this, [x, $jscomp.arrayFromIterable(params), y])
      //
      // If we used `call`, we'd get this nonsense instead
      //
      // Foo.call.apply(Foo, [this, x, $jscomp.arrayFromIterable(params), y])
      Node superClassDotApply =
          astFactory.createGetProp(superClassQNameNode.cloneTree(), "apply").srcrefTree(callee);
      // Create `SuperClass.call(this)`
      Node newSuperCall = astFactory.createCall(superClassDotApply).srcref(superCall);
      newSuperCall.addChildToBack(astFactory.createThis(thisType).srcref(callee));
      newSuperCall.putBooleanProp(Node.FREE_CALL, false); // callee is now a getprop
      // It's very common to just have `super(...arguments)`, because we generate constructors
      // containing that for extending classes that don't have an explicit constructor.
      // For that case it's more efficient to just convert `super(...arguments)` to
      // `SuperClass.apply(this, arguments)` here rather than relying on later optimizations to
      // convert `[...arguments]` to `arguments`.
      if (isSingleSpreadOfArguments(args)) {
        newSuperCall.addChildToBack(Iterables.getOnlyElement(args).getOnlyChild().detach());
      } else {
        newSuperCall.addChildToBack(astFactory.createArraylit(args).srcref(superCall));
      }
      return newSuperCall;
    } else {
      // We want to convert
      //
      // super(arg1, arg2)
      // to
      // Foo.call(this, arg1, arg2)
      //
      // Using `call` is shorter than using `apply`.
      Node superClassDotCall =
          astFactory.createGetProp(superClassQNameNode.cloneTree(), "call").srcrefTree(callee);
      Node newSuperCall = astFactory.createCall(superClassDotCall).srcref(superCall);
      newSuperCall.addChildToBack(astFactory.createThis(thisType).srcref(callee));
      newSuperCall.putBooleanProp(Node.FREE_CALL, false); // callee is now a getprop
      for (Node arg : args) {
        newSuperCall.addChildToBack(arg);
      }
      return newSuperCall;
    }
  }

  /**
   * Returns a transpiled version of the super constructor call using `$jscomp.constructor`.
   *
   * <p>The children of the passed in `superCall` are all removed from it by this method, but the
   * existing call itself is not replaced in the AST yet. The returned node is not yet attached to
   * the AST.
   */
  private Node createJSCompConstructorCall(
      Scope scope, Node superClassQNameNode, Node superCall, JSType thisType) {
    checkArgument(superClassQNameNode.isQualifiedName(), superClassQNameNode);
    checkArgument(superCall.isCall(), superCall);

    final Node callee = checkNotNull(superCall.removeFirstChild(), superCall);
    checkState(callee.isSuper(), callee);

    // `$jscomp.construct`
    final Node jscompDotConstruct =
        astFactory.createQName(scope, "$jscomp", "construct").srcrefTree(callee);

    final Node superClassQName = superClassQNameNode.cloneTree();

    // extract the arguments from the super() call and create the arguments list to pass to
    // $jscomp.construct()
    final List<Node> superCallArgList = new ArrayList<>();
    while (superCall.hasChildren()) {
      superCallArgList.add(superCall.removeFirstChild());
    }
    // It's very common to just have `super(...arguments)`, because we generate constructors
    // containing that for extending classes that don't have an explicit constructor.
    // For that case it's more efficient to just convert `super(...arguments)` to
    // `$jscomp.construct(SuperClass, arguments, this.constructor)` here rather than relying on
    // later optimizations to
    // convert `[...arguments]` to `arguments`.
    final Node superArgs =
        isSingleSpreadOfArguments(superCallArgList)
            // pull out `arguments` from `...arguments`
            ? Iterables.getOnlyElement(superCallArgList).getOnlyChild().detach()
            : astFactory.createArraylit(superCallArgList).srcref(superCall);

    // `this.constructor`
    final Node thisDotConstructor =
        astFactory
            .createGetProp(astFactory.createThis(thisType), "constructor")
            .srcrefTree(superCall);

    // `super(arg1, arg2)`
    // becomes
    // `$jscomp.construct(SuperClassName, [arg1, arg2], this.constructor)`
    return astFactory
        .createCall(jscompDotConstruct, superClassQName, superArgs, thisDotConstructor)
        .srcref(superCall);
  }

  private static boolean isSingleSpreadOfArguments(List<Node> nodeList) {
    return nodeList.size() == 1 && isSpreadOfArguments(Iterables.getOnlyElement(nodeList));
  }

  private static boolean isSpreadOfArguments(Node node) {
    return node.isSpread() && node.getOnlyChild().matchesName("arguments");
  }

  private void replaceNativeErrorSuperCall(Node superCall, Node newSuperCall) {
    // The native error class constructors always return a new object instead of initializing
    // `this`, so a workaround is needed.
    Node superStatement = NodeUtil.getEnclosingStatement(superCall);
    Node body = superStatement.getParent();
    checkState(body.isBlock(), body);

    JSType thisType = newSuperCall.getJSType();

    // var $jscomp$tmp$error;
    Node getError =
        IR.var(astFactory.createName(TMP_ERROR, thisType)).srcrefTreeIfMissing(superCall);
    getError.insertBefore(superStatement);

    // Create an expression to initialize `this` from temporary Error object at the point
    // where super.apply() was called.
    // $jscomp$tmp$error = Error.call(this, ...),
    Node getTmpError =
        astFactory.createAssign(astFactory.createName(TMP_ERROR, thisType), newSuperCall);
    // this.message = $jscomp$tmp$error.message,
    Node copyMessage =
        astFactory.createAssign(
            astFactory.createGetProp(astFactory.createThis(thisType), "message"),
            astFactory.createGetProp(astFactory.createName(TMP_ERROR, thisType), "message"));

    // Old versions of IE Don't set stack until the object is thrown, and won't set it then
    // if it already exists on the object.
    // ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack)
    Node setStack =
        astFactory.createAnd(
            astFactory.createIn(
                astFactory.createString("stack"), astFactory.createName(TMP_ERROR, thisType)),
            astFactory.createAssign(
                astFactory.createGetProp(astFactory.createThis(thisType), "stack"),
                astFactory.createGetProp(astFactory.createName(TMP_ERROR, thisType), "stack")));
    Node superErrorExpr =
        astFactory
            .createCommas(getTmpError, copyMessage, setStack, astFactory.createThis(thisType))
            .srcrefTreeIfMissing(superCall);
    superCall.replaceWith(superErrorExpr);
    compiler.reportChangeToEnclosingScope(superErrorExpr);
  }

  private boolean isNativeObjectClass(NodeTraversal t, String className) {
    return className.equals("Object") && !isDefinedInSources(t, className);
  }

  private boolean isNativeErrorClass(NodeTraversal t, String superClassName) {
    switch (superClassName) {
        // All Error classes listed in the ECMAScript spec as of 2016
      case "Error":
      case "EvalError":
      case "RangeError":
      case "ReferenceError":
      case "SyntaxError":
      case "TypeError":
      case "URIError":
        return !isDefinedInSources(t, superClassName);
      default:
        return false;
    }
  }

  /**
   * Is `className` the name of a known native JS class for which we haven't seen a definition in
   * the source code we're compiling. (Note that our own polyfill definitions don't count as a
   * definition being present in the source code.)
   */
  private boolean isKnownNativeClass(NodeTraversal t, String className) {
    // This list originally taken from the list of built-in objects at
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference
    // as of 2016-10-22.
    // - Intl.* classes were left out, because it doesn't seem worth the extra effort
    //   of handling the qualified name.
    // - Deprecated and experimental classes were left out.
    switch (className) {
      case "Array":
      case "ArrayBuffer":
      case "Boolean":
      case "DataView":
      case "Date":
      case "Float32Array":
      case "Function":
      case "Generator":
      case "GeneratorFunction":
      case "Int16Array":
      case "Int32Array":
      case "Int8Array":
      case "InternalError":
      case "Map":
      case "Number":
      case "Object":
      case "Promise":
      case "Proxy":
      case "RegExp":
      case "Set":
      case "String":
      case "Symbol":
      case "TypedArray":
      case "Uint16Array":
      case "Uint32Array":
      case "Uint8Array":
      case "Uint8ClampedArray":
      case "WeakMap":
      case "WeakSet":
        return !isDefinedInSources(t, className);
      default:
        return false;
    }
  }

  /**
   * Is a variable with the given name defined in the source code being compiled?
   *
   * <p>Please note that the call to {@code t.getScope()} is expensive, so we should avoid
   * calling this method when possible.
   * @param t
   * @param varName
   */
  private boolean isDefinedInSources(NodeTraversal t, String varName) {
    Var objectVar = t.getScope().getVar(varName);
    return objectVar != null && !objectVar.isExtern();
  }

  private void updateThisToSuperThis(
      final JSType typeOfThis, Node constructorBody, final List<Node> superCalls) {
    NodeTraversal.Callback replaceThisWithSuperThis =
        new NodeTraversal.Callback() {
          @Override
          public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
            if (superCalls.contains(n)) {
              return false; // Leave `this` intact on super calls.
            } else if (n.isFunction() && !n.isArrowFunction()) {
              // Don't replace `this` in non-arrow function definitions.
              return false;
            } else {
              return true;
            }
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (n.isThis()) {
              Node superThis = astFactory.createName(SUPER_THIS, n.getJSType()).srcref(n);
              n.replaceWith(superThis);
            } else if (n.isReturn() && !n.hasChildren()) {
              // An empty return needs to be changed to return $jscomp$super$this
              n.addChildToFront(astFactory.createName(SUPER_THIS, typeOfThis).srcref(n));
            }
          }
        };
    NodeTraversal.traverse(compiler, constructorBody, replaceThisWithSuperThis);
  }

  private JSType getTypeOfThisForConstructor(Node constructor) {
    checkArgument(constructor.isFunction(), constructor);
    // If typechecking has run, all function nodes should have a JSType. Nodes that were in a CAST
    // will also have the TYPE_BEFORE_CAST property, which is null for other nodes.
    final JSType constructorTypeBeforeCast = constructor.getJSTypeBeforeCast();
    final JSType constructorType =
        constructorTypeBeforeCast != null ? constructorTypeBeforeCast : constructor.getJSType();
    if (constructorType == null) {
      return null; // Type checking passes must not have run.
    }
    checkState(constructorType.isFunctionType());
    return constructorType.toMaybeFunctionType().getTypeOfThis();
  }

  private Node getSuperClassQNameNode(Node constructor) {
    String className = NodeUtil.getNameNode(constructor).getQualifiedName();
    Node constructorStatement = checkNotNull(NodeUtil.getEnclosingStatement(constructor));

    Node superClassNameNode = null;
    for (Node statement = constructorStatement.getNext();
        statement != null;
        statement = statement.getNext()) {
      superClassNameNode = getSuperClassNameNodeIfIsInheritsStatement(statement, className);
      if (superClassNameNode != null) {
        break;
      }
    }

    return checkNotNull(superClassNameNode, "$jscomp.inherits() call not found.");
  }

  private Node getSuperClassNameNodeIfIsInheritsStatement(Node statement, String className) {
    // $jscomp.inherits(ChildClass, SuperClass);
    if (!statement.isExprResult()) {
      return null;
    }
    Node callNode = statement.getFirstChild();
    if (!callNode.isCall()) {
      return null;
    }
    Node jscompDotInherits = callNode.getFirstChild();
    if (!jscompDotInherits.matchesQualifiedName("$jscomp.inherits")) {
      return null;
    }
    Node classNameNode = checkNotNull(jscompDotInherits.getNext());
    if (classNameNode.matchesQualifiedName(className)) {
      return checkNotNull(classNameNode.getNext());
    } else {
      return null;
    }
  }

  void setGlobalNamespace(GlobalNamespace globalNamespace) {
    this.globalNamespace = globalNamespace;
  }
}
