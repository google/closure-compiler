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
import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/** Converts {@code super()} calls. This has to run after typechecking. */
public final class Es6ConvertSuperConstructorCalls
implements NodeTraversal.Callback, HotSwapCompilerPass {
  private static final String TMP_ERROR = "$jscomp$tmp$error";

  private final AbstractCompiler compiler;

  public Es6ConvertSuperConstructorCalls(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isSuper()) {
      visitSuper(t, n, parent);
    }
  }

  private void visitSuper(NodeTraversal t, Node node, Node parent) {
    // NOTE: When this pass runs:
    // -   ES6 classes have already been rewritten as ES5 functions.
    // -   All instances of super() that are not super constructor calls have been rewritten.
    // -   However, if the original call used spread (e.g. super(...list)), then spread
    //     transpilation will have turned that into something like
    //     super.apply(null, $jscomp$expanded$args).
    if (node.isFromExterns()) {
      // This class is defined in an externs file, so it's only a stub, not the actual
      // implementation that should be instantiated.
      // A call to super() shouldn't actually exist for a stub and is problematic to transpile,
      // so just drop it.
      NodeUtil.getEnclosingStatement(node).detach();
      compiler.reportCodeChange();
    } else {
      // super() or super.apply()
      Node superCall = parent.isCall() ? parent : parent.getParent();
      String superClassQName = getSuperClassQName(superCall);
      if (isNativeObjectClass(t, superClassQName)) {
        // There's no need to call Object as a super constructor, so just replace the call with
        // `this`, which is its correct return value.
        // TODO(bradfordcsmith): Although unlikely, super() could have argument expressions with
        //     side-effects.
        superCall.getParent().replaceChild(superCall, IR.thisNode().useSourceInfoFrom(superCall));
        compiler.reportCodeChange();
      } else if (isUnextendableNativeClass(t, superClassQName)) {
        compiler.report(
            JSError.make(
                superCall,
                CANNOT_CONVERT_YET,
                "extending native class: " + superClassQName));
      } else {
        Node newSuperCall = createNewSuperCall(superClassQName, superCall);
        if (isNativeErrorClass(t, superClassQName)) {
          replaceNativeErrorSuperCall(superCall, newSuperCall);
        } else {
          superCall.getParent().replaceChild(superCall, newSuperCall);
        }
        compiler.reportCodeChange();
      }
    }
  }

  private Node createNewSuperCall(String superClassQName, Node superCall) {
    checkArgument(superCall.isCall(), superCall);
    Node newSuperCall = superCall.cloneTree();
    Node callee = newSuperCall.getFirstChild();

    if (callee.isSuper()) {
      // super(...) -> super.call(this, ...)
      Node superClassDotCall =
          IR.getprop(NodeUtil.newQName(compiler, superClassQName), IR.string("call"))
              .useSourceInfoFromForTree(callee);
      newSuperCall.replaceChild(callee, superClassDotCall);
      newSuperCall.putBooleanProp(Node.FREE_CALL, false); // callee is now a getprop
      newSuperCall.addChildAfter(IR.thisNode().useSourceInfoFrom(callee), superClassDotCall);
    } else {
      // super.apply(null|this, ...) -> SuperClass.apply(this, ...)
      checkState(callee.isGetProp(), callee);
      Node applyNode = checkNotNull(callee.getSecondChild());
      checkState(applyNode.getString().equals("apply"), applyNode);

      Node superDotApply = newSuperCall.getFirstChild();
      Node superNode = superDotApply.getFirstChild();
      superDotApply.replaceChild(
          superNode,
          NodeUtil.newQName(compiler, superClassQName).useSourceInfoFromForTree(superNode));
      // super.apply(null, ...) is generated by spread transpilation
      // super.apply(this, arguments) is used by Es6ConvertSuper in automatically-generated
      //   constructors.
      Node nullOrThisNode = newSuperCall.getSecondChild();
      if (!nullOrThisNode.isThis()) {
        checkState(nullOrThisNode.isNull(), nullOrThisNode);
        newSuperCall.replaceChild(nullOrThisNode, IR.thisNode().useSourceInfoFrom(nullOrThisNode));
      }
    }
    return newSuperCall;
  }

  private void replaceNativeErrorSuperCall(Node superCall, Node newSuperCall) {
    // The native error class constructors always return a new object instead of initializing
    // `this`, so a workaround is needed.
    Node superStatement = NodeUtil.getEnclosingStatement(superCall);
    Node body = superStatement.getParent();
    checkState(body.isBlock(), body);

    // var $jscomp$tmp$error;
    Node getError = IR.var(IR.name(TMP_ERROR)).useSourceInfoIfMissingFromForTree(superCall);
    body.addChildBefore(getError, superStatement);

    // Create an expression to initialize `this` from temporary Error object at the point
    // where super.apply() was called.
    // $jscomp$tmp$error = Error.call(this, ...),
    Node getTmpError = IR.assign(IR.name(TMP_ERROR), newSuperCall);
    // this.message = $jscomp$tmp$error.message,
    Node copyMessage =
        IR.assign(
            IR.getprop(IR.thisNode(), IR.string("message")),
            IR.getprop(IR.name(TMP_ERROR), IR.string("message")));

    // Old versions of IE Don't set stack until the object is thrown, and won't set it then
    // if it already exists on the object.
    // ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack)
    Node setStack =
        IR.and(
            IR.in(IR.string("stack"), IR.name(TMP_ERROR)),
            IR.assign(
                IR.getprop(IR.thisNode(), IR.string("stack")),
                IR.getprop(IR.name(TMP_ERROR), IR.string("stack"))));
    // TODO(bradfordcsmith): The spec says super() should return `this`, but Angular2 errors.ts
    //     currently depends on it returning the newly created Error object.
    Node superErrorExpr =
        IR.comma(IR.comma(IR.comma(getTmpError, copyMessage), setStack), IR.name(TMP_ERROR))
            .useSourceInfoIfMissingFromForTree(superCall);
    superCall.getParent().replaceChild(superCall, superErrorExpr);
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
        // TODO(bradfordcsmith): Disallow Map again when client code is fixed.
        // case "Map":
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

  private String getSuperClassQName(Node superCall) {
    // Find the $jscomp.inherits() call and take the super class name from there.
    Node enclosingConstructor = checkNotNull(NodeUtil.getEnclosingFunction(superCall));
    String className = NodeUtil.getNameNode(enclosingConstructor).getQualifiedName();
    Node constructorStatement = checkNotNull(NodeUtil.getEnclosingStatement(enclosingConstructor));

    for (Node statement = constructorStatement.getNext();
        statement != null;
        statement = statement.getNext()) {
      String superClassName = getSuperClassNameIfIsInheritsStatement(statement, className);
      if (superClassName != null) {
        return superClassName;
      }
    }
    throw new IllegalStateException("$jscomp.inherits() call not found.");
  }

  private String getSuperClassNameIfIsInheritsStatement(Node statement, String className) {
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
      Node superClass = checkNotNull(classNameNode.getNext());
      return superClass.getQualifiedName();
    } else {
      return null;
    }
  }

  @Override
  public void process(Node externs, Node root) {
    // Might need to synthesize constructors for ambient classes in .d.ts externs
    TranspilationPasses.processTranspile(compiler, externs, this);
    TranspilationPasses.processTranspile(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, this);
  }
}
