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
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT;

import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Converts {@code super()} calls. This has to run after typechecking. */
public final class Es6ConvertSuperConstructorCalls
implements NodeTraversal.Callback, HotSwapCompilerPass {
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
  private static final FeatureSet transpiledFeatures = FeatureSet.BARE_MINIMUM.with(Feature.SUPER);

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
      // NOTE(sdh): Es6RewriteRestAndSpread rewrites super(...args) to super.apply(this, args),
      // so we need to go up a level if that happened.  This extra getParent() could be removed
      // if we could flip the order of these transpilation passes.
      Node superCall = parent.isCall() ? parent : parent.getParent();
      if (!superCall.isCall() && parent.isGetProp()) {
        // This is currently broken because whatever earlier pass is responsible for transpiling
        // away super inside GETPROP is not handling it. Unfortunately there's not really a good
        // way to handle it without additional runtime support, and it will be a lot easier to deal
        // with after classes are typechecked natively. So for now, we just don't support it. This
        // is not a problem, since any such usages were already broken, just with a different error.
        t.report(n, Es6ToEs3Util.CANNOT_CONVERT_YET, "super access with no extends clause");
        return false;
      }
      checkState(superCall.isCall(), superCall);
      ConstructorData constructorData = checkNotNull(constructorDataStack.peek());
      constructorData.superCalls.add(superCall);
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
    // -   All instances of super() that are not super constructor calls have been rewritten.
    // -   However, if the original call used spread (e.g. super(...list)), then spread
    //     transpilation will have turned that into something like
    //     super.apply(null, $jscomp$expanded$args).
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
          Node thisNode = astFactory.createThis(thisType).useSourceInfoFrom(superCall);
          superCall.replaceWith(thisNode);
          compiler.reportChangeToEnclosingScope(thisNode);
        }
      } else if (isUnextendableNativeClass(t, superClassQName)) {
        compiler.report(
            JSError.make(
                constructor, CANNOT_CONVERT, "extending native class: " + superClassQName));
      } else if (isNativeErrorClass(t, superClassQName)) {
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
            superCallParent.replaceChild(superCall, newSuperCall);
          } else {
            // super() is part of an expression, so it must return `this`.
            superCallParent.replaceChild(
                superCall,
                astFactory
                    .createComma(newSuperCall, astFactory.createThis(thisType))
                    .useSourceInfoIfMissingFromForTree(superCall));
          }
          compiler.reportChangeToEnclosingScope(superCallParent);
        }
      } else {
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
          constructorBody.replaceChild(
              firstStatement,
              IR.returnNode(newReturn).useSourceInfoIfMissingFromForTree(firstStatement));
        } else {
          final JSType typeOfThis = getTypeOfThisForConstructor(constructor);
          // `this` -> `$jscomp$super$this` throughout the constructor body,
          // except for super() calls.
          updateThisToSuperThis(typeOfThis, constructorBody, superCalls);
          // Start constructor with `var $jscomp$super$this;`
          constructorBody.addChildToFront(
              IR.var(astFactory.createName(SUPER_THIS, typeOfThis))
                  .useSourceInfoFromForTree(constructorBody));
          // End constructor with `return $jscomp$super$this;`
          constructorBody.addChildToBack(
              IR.returnNode(astFactory.createName(SUPER_THIS, typeOfThis))
                  .useSourceInfoFromForTree(constructorBody));
          // Replace each super() call with `($jscomp$super$this = <newSuperCall> || this)`
          for (Node superCall : superCalls) {
            Node newSuperCall = createNewSuperCall(superClassNameNode, superCall, typeOfThis);
            superCall.replaceWith(
                astFactory
                    .createAssign(
                        astFactory.createName(SUPER_THIS, typeOfThis),
                        astFactory.createOr(newSuperCall, astFactory.createThis(typeOfThis)))
                    .useSourceInfoIfMissingFromForTree(superCall));
          }
        }
        compiler.reportChangeToEnclosingScope(constructorBody);
      }
    }
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
                    && !n.getFirstChild().matchesQualifiedName("undefined")) {
                  foundNonEmptyReturn = true;
                }
              }
            }
          };
      NodeTraversal.traverse(compiler, functionBody, checkForDefinedReturnValue);
      return foundNonEmptyReturn;
    }
  }

  private Node createNewSuperCall(Node superClassQNameNode, Node superCall, JSType thisType) {
    checkArgument(superClassQNameNode.isQualifiedName(), superClassQNameNode);
    checkArgument(superCall.isCall(), superCall);
    Node callee = superCall.getFirstChild();

    if (callee.isSuper()) {
      return createNewSuperCallWithDotCall(superClassQNameNode, superCall, thisType);
    } else {
      return createNewSuperCallWithDotApply(superClassQNameNode, superCall, thisType);
    }
  }

  private Node createNewSuperCallWithDotCall(
      Node superClassQNameNode, Node superCall, JSType thisType) {
    // super(...) -> SuperClass.call(this, ...)
    checkArgument(superClassQNameNode.isQualifiedName(), superClassQNameNode);
    checkArgument(superCall.isCall(), superCall);
    Node callee = superCall.removeFirstChild();

    // Create `SuperClass.call`
    Node superClassDotCall =
        astFactory
            .createGetProp(superClassQNameNode.cloneTree(), "call")
            .useSourceInfoFromForTree(callee);
    // Create `SuperClass.call(this)`
    Node newSuperCall = astFactory.createCall(superClassDotCall).useSourceInfoFrom(superCall);
    newSuperCall.addChildToBack(astFactory.createThis(thisType).useSourceInfoFrom(callee));
    newSuperCall.putBooleanProp(Node.FREE_CALL, false); // callee is now a getprop

    // add any additional arguments to the new super call
    while (superCall.hasChildren()) {
      newSuperCall.addChildToBack(superCall.removeFirstChild());
    }
    return newSuperCall;
  }

  private Node createNewSuperCallWithDotApply(
      Node superClassQNameNode, Node superCall, JSType thisType) {
    // spread transpilation does
    // super(...arguments) -> super.apply(null, arguments)
    // Now we must do
    // super.apply(null, arguments) -> SuperClass.apply(this, arguments)
    checkArgument(superClassQNameNode.isQualifiedName(), superClassQNameNode);
    checkArgument(superCall.isCall(), superCall);
    Node callee = superCall.removeFirstChild();
    checkState(callee.isGetProp(), callee);

    Node applyNode = checkNotNull(callee.getSecondChild());
    checkState(applyNode.getString().equals("apply"), applyNode);
    Node superNode = callee.getFirstChild();

    // Replace `super.apply` with `SuperClass.apply`
    callee.replaceChild(
        superNode, superClassQNameNode.cloneTree().useSourceInfoFromForTree(superNode));
    // Get `null` from `super.apply(null, arguments)`
    Node nullNode = superCall.getFirstChild();
    checkState(nullNode.isNull(), nullNode);
    superCall.removeChild(nullNode);

    // Create `SuperClass.apply(null)`
    Node newSuperCall = astFactory.createCall(callee).useSourceInfoFrom(superCall);
    newSuperCall.addChildToBack(astFactory.createThis(thisType).useSourceInfoFrom(nullNode));

    // add any additional arguments to the new super call
    while (superCall.hasChildren()) {
      newSuperCall.addChildToBack(superCall.removeFirstChild());
    }
    return newSuperCall;
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
        IR.var(astFactory.createName(TMP_ERROR, thisType))
            .useSourceInfoIfMissingFromForTree(superCall);
    body.addChildBefore(getError, superStatement);

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
            .useSourceInfoIfMissingFromForTree(superCall);
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
   * Is the given class a native class for which we cannot properly transpile extension?
   * @param t
   * @param className
   */
  private boolean isUnextendableNativeClass(NodeTraversal t, String className) {
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
              Node superThis =
                  astFactory.createName(SUPER_THIS, n.getJSType()).useSourceInfoFrom(n);
              parent.replaceChild(n, superThis);
            } else if (n.isReturn() && !n.hasChildren()) {
              // An empty return needs to be changed to return $jscomp$super$this
              n.addChildToFront(astFactory.createName(SUPER_THIS, typeOfThis).useSourceInfoFrom(n));
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

  @Override
  public void process(Node externs, Node root) {
    globalNamespace = new GlobalNamespace(compiler, externs, root);
    // Might need to synthesize constructors for ambient classes in .d.ts externs
    TranspilationPasses.processTranspile(compiler, externs, transpiledFeatures, this);
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
    TranspilationPasses.maybeMarkFeaturesAsTranspiledAway(compiler, transpiledFeatures);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, transpiledFeatures, this);
  }
}
