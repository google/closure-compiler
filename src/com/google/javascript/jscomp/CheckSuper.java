/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;

/**
 * Check for errors related to the `super` keyword.
 *
 * <p>NOTE: One might be tempted to make one or both of these refactoring improvements to this
 * class.
 *
 * <ol>
 *   <li>Use an inner class as the {@code Callback} instead of making the whole class implement it.
 *   <li>Use a {@code ScopedCallback} since we need to track function scopes here.
 * </ol>
 *
 * <p>Unfortunately, this class is used with {@code CombinedCompilerPass}, which will pass instances
 * of it directly to {@code NodeTraversal.traverse()}.
 */
final class CheckSuper implements HotSwapCompilerPass, NodeTraversal.Callback {
  static final DiagnosticType MISSING_CALL_TO_SUPER =
      DiagnosticType.error("JSC_MISSING_CALL_TO_SUPER", "constructor is missing a call to super()");

  static final DiagnosticType THIS_BEFORE_SUPER =
      DiagnosticType.error("JSC_THIS_BEFORE_SUPER", "cannot access this before calling super()");

  static final DiagnosticType SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR =
      DiagnosticType.error(
          "JSC_SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR",
          "cannot access super properties before calling super()");

  static final DiagnosticType INVALID_SUPER_CALL =
      DiagnosticType.error(
          "JSC_INVALID_SUPER_CALL", "super() not allowed except in the constructor of a subclass");

  // The JS spec allows calls to `super()` in an arrow function within a constructor,
  // as long as `super()` executes exactly once before any references to `this` or `super.prop`.
  // However, doing that makes it very hard to statically determine whether `super()` is being
  // called when it should be.
  // There's really no good reason to call `super()` in an arrow function.
  // It indicates that your code is overly complicated and you should refactor, so we will not
  // allow it.
  static final DiagnosticType SUPER_CALL_IN_ARROW =
      DiagnosticType.error(
          "JSC_SUPER_CALL_IN_ARROW",
          "closure-compiler does not allow calls to `super()` in arrow functions");

  static final DiagnosticType INVALID_SUPER_USAGE =
      DiagnosticType.error(
          "JSC_INVALID_SUPER_USAGE", "''super'' may only be used in a call or property access");

  static final DiagnosticType INVALID_SUPER_ACCESS =
      DiagnosticType.error(
          "JSC_INVALID_SUPER_ACCESS", "''super'' may only be accessed within a method");

  static final DiagnosticType INVALID_SUPER_CALL_WITH_SUGGESTION =
      DiagnosticType.error(
          "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION",
          "super() not allowed here. Did you mean super.{0}?");

  private final AbstractCompiler compiler;

  public CheckSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  private final Deque<Context> contextStack = new ArrayDeque<>();

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    if (contextStack.isEmpty()) {
      // should only happen on very first call to shouldTraverse on root node or hotswapped
      // script.
      checkState(n.isRoot() || n.isScript(), n);
      contextStack.push(new SuperNotAllowedContext(n));
    }
    if (n.isFunction()) {
      Context currentContext = contextStack.peek();
      Context newContext = getContextForFunctionNode(currentContext, n);
      if (newContext != currentContext) {
        contextStack.push(newContext);
      }
    }
    return true;
  }

  private Context getContextForFunctionNode(Context currentContext, Node fn) {
    if (NodeUtil.isMethodDeclaration(fn)) {
      if (NodeUtil.isEs6Constructor(fn)) {
        return new ConstructorContext(fn);
      } else {
        return new MethodContext(fn);
      }
    } else {
      // Arrow function context varies depending on the actual context, but
      // super is never allowed in normal functions.
      return fn.isArrowFunction()
          ? currentContext.getContextForArrowFunctionNode(fn)
          : new SuperNotAllowedContext(fn);
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    Context currentContext = checkNotNull(contextStack.peek());
    if (n.isSuper()) {
      if (isSuperConstructorCall(n)) {
        currentContext.visitSuperConstructorCall(t, n);
      } else if (isSuperPropertyAccess(n)) {
        currentContext.visitSuperPropertyAccess(t, n);
      } else {
        // super used some way other than `super()`, `super.prop`, or `super[expr]`.
        t.report(n, INVALID_SUPER_USAGE);
      }
    } else if (n.isThis()) {
      currentContext.visitThis(t, n);
    } else if (n.isReturn()) {
      currentContext.visitReturn(t, n);
    }
    if (n == currentContext.getContextNode()) {
      currentContext.visitContextNode(t);
      contextStack.pop();
    }
  }

  private boolean isSuperConstructorCall(Node superNode) {
    checkState(superNode.isSuper(), superNode);
    Node parent = superNode.getParent();
    return parent.isCall() && superNode.isFirstChildOf(parent);
  }

  private boolean isSuperPropertyAccess(Node superNode) {
    checkState(superNode.isSuper(), superNode);
    Node parent = superNode.getParent();
    return NodeUtil.isNormalGet(parent) && superNode.isFirstChildOf(parent);
  }

  /** Tracks lexical context and determines correct traversal behavior based on it. */
  private abstract static class Context {

    private final Node contextNode;

    Context(Node contextNode) {
      this.contextNode = checkNotNull(contextNode);
    }

    Node getContextNode() {
      return contextNode;
    }

    /** The correct context for an arrow function depends on the enclosing context. */
    abstract Context getContextForArrowFunctionNode(Node arrowFn);

    /** Handle a super constructor call. */
    abstract void visitSuperConstructorCall(NodeTraversal t, Node superNode);

    /** Handle a super property reference. (`super.prop` or `super[expr]`) */
    abstract void visitSuperPropertyAccess(NodeTraversal t, Node superNode);

    void visitThis(NodeTraversal t, Node thisNode) {
      // Ignored except in constructor methods.
    }

    void visitReturn(NodeTraversal t, Node returnNode) {
      // Ignored except in constructor methods.
    }

    void visitContextNode(NodeTraversal t) {
      // Called when visiting the root node of this context after all of its children have been
      // visited.
    }
  }

  /** Lexical context when not within a method, class, or object literal. */
  private static class SuperNotAllowedContext extends Context {
    SuperNotAllowedContext(Node contextNode) {
      super(contextNode);
    }

    @Override
    Context getContextForArrowFunctionNode(Node arrowFn) {
      // Since super isn't allowed in this context, it isn't allowed in any of its arrow functions
      // either.
      return this;
    }

    @Override
    void visitSuperConstructorCall(NodeTraversal t, Node superNode) {
      // We're not within a constructor method.
      t.report(superNode, INVALID_SUPER_CALL);
    }

    @Override
    void visitSuperPropertyAccess(NodeTraversal t, Node superNode) {
      // We're not within a method.
      t.report(superNode, INVALID_SUPER_ACCESS);
    }
  }

  /** Lexicial context within a non-constructor method of either a class or an object literal. */
  private static class MethodContext extends Context {
    private final boolean isClassMethod;

    MethodContext(Node functionNode) {
      super(functionNode);
      checkArgument(
          functionNode.isFunction() && NodeUtil.isMethodDeclaration(functionNode), functionNode);
      Node objLitOrClassMembers = checkNotNull(functionNode.getGrandparent());
      if (objLitOrClassMembers.isObjectLit()) {
        isClassMethod = false;
      } else {
        checkState(objLitOrClassMembers.isClassMembers(), objLitOrClassMembers);
        isClassMethod = true;
      }
    }

    @Override
    Context getContextForArrowFunctionNode(Node arrowFn) {
      // In a method an arrow function keeps the same context as the method, but a non-arrow
      // function creates a new non-method context.
      return this;
    }

    @Override
    void visitSuperPropertyAccess(NodeTraversal t, Node superNode) {
      // super property access in a method is perfectly valid, so do not report an error.
    }

    @Override
    void visitSuperConstructorCall(NodeTraversal t, Node superNode) {
      if (isClassMethod) {
        // super() - not allowed in non-constructor methods.
        String propName = getPropertyName();
        if (propName != null) {
          // Maybe the user was confused by other languages where super() invokes the
          // same method in the parent class?
          t.report(superNode, INVALID_SUPER_CALL_WITH_SUGGESTION, propName);
        } else {
          t.report(superNode, INVALID_SUPER_CALL);
        }
      } else {
        // object literal methods cannot contain super() calls
        t.report(superNode, INVALID_SUPER_CALL);
      }
    }

    /** Return the property name associated with the method, if any, otherwise NULL. */
    @Nullable
    String getPropertyName() {
      Node parent = checkNotNull(getContextNode().getParent());
      // ```
      // class X {
      //   propertyName() {}
      //   get propertyName() {}
      //   set propertyName(value) {}
      //   [expression]() {} // no name for this one
      // }
      // ```
      if (parent.isMemberFunctionDef() || parent.isGetterDef() || parent.isSetterDef()) {
        return parent.getString();
      } else {
        return null;
      }
    }
  }

  /** Lexical context within a class constructor method. */
  private static class ConstructorContext extends Context {
    final boolean hasParentClass;

    // Will be set to the first `super()` call that appears lexically, if any.
    Node firstSuperCall = null;
    // Call to super() isn't required if the constructor returns a value.
    boolean returnsAValue = false;
    Node thisAccessedBeforeSuper = null;
    Node superPropertyAccessedBeforeSuperCall = null;

    ConstructorContext(Node contextNode) {
      super(contextNode);
      checkArgument(NodeUtil.isEs6Constructor(contextNode), contextNode);
      Node classNode = NodeUtil.getEnclosingClass(contextNode);
      hasParentClass = !classNode.getSecondChild().isEmpty();
    }

    @Override
    Context getContextForArrowFunctionNode(Node arrowFn) {
      // Unlike normal methods, we need to create a separate context for arrow functions in
      // constructors. We need to distinguish between return statements and `super()` calls
      // that appear directly in the constructor and those that appear in arrow functions.
      return new ConstructorArrowContext(this, arrowFn);
    }

    @Override
    void visitSuperConstructorCall(NodeTraversal t, Node superNode) {
      if (firstSuperCall == null) {
        firstSuperCall = superNode;
      }
    }

    @Override
    void visitSuperPropertyAccess(NodeTraversal t, Node superNode) {
      if (firstSuperCall == null) {
        superPropertyAccessedBeforeSuperCall = superNode;
      }
    }

    @Override
    void visitThis(NodeTraversal t, Node thisNode) {
      if (firstSuperCall == null) {
        thisAccessedBeforeSuper = thisNode;
      }
    }

    @Override
    void visitReturn(NodeTraversal t, Node returnNode) {
      if (returnNode.hasChildren()) {
        returnsAValue = true;
      }
    }

    @Override
    void visitContextNode(NodeTraversal t) {
      // We've now visited the entire constructor, so we can decide whether to report errors.
      if (!hasParentClass) {
        if (firstSuperCall != null) {
          // calling `super()` only makes sense when there is a super class.
          t.report(firstSuperCall, INVALID_SUPER_CALL);
        }
      } else {
        // There is a parent class, so a call to `super()` is required unless the constructor
        // returns a value.
        if (firstSuperCall == null && !returnsAValue) {
          // The context node is the function itself , but ErrorToFixMapper expects the error to
          // be reported on the MEMBER_FUNCTION_DEF that is its parent.
          t.report(getContextNode().getParent(), MISSING_CALL_TO_SUPER);
        }
        if (thisAccessedBeforeSuper != null) {
          t.report(thisAccessedBeforeSuper, THIS_BEFORE_SUPER);
        }
        if (superPropertyAccessedBeforeSuperCall != null) {
          t.report(superPropertyAccessedBeforeSuperCall, SUPER_ACCESS_BEFORE_SUPER_CONSTRUCTOR);
        }
      }
    }
  }

  /** Lexical context within an arrow function enclosed by a class constructor method. */
  private static class ConstructorArrowContext extends Context {
    private final ConstructorContext constructorContext;

    ConstructorArrowContext(ConstructorContext constructorContext, Node arrowFn) {
      super(arrowFn);
      this.constructorContext = checkNotNull(constructorContext);
    }

    @Override
    Context getContextForArrowFunctionNode(Node arrowFn) {
      // Arrow functions within arrow functions share the same context behavior.
      return this;
    }

    @Override
    void visitSuperConstructorCall(NodeTraversal t, Node superNode) {
      t.report(superNode, SUPER_CALL_IN_ARROW);
      // Tell the constructor context about this `super()` call in order to avoid confusing and
      // likely redundant error messages such as "missing super call" or "`this` accessed before
      // super()".
      constructorContext.visitSuperConstructorCall(t, superNode);
    }

    @Override
    void visitSuperPropertyAccess(NodeTraversal t, Node superNode) {
      // We will pretend the arrow function is immediately called, so it's as if the super
      // property reference appeared directly in the constructor.
      // The result is that you get an error for code like the following, even though it isn't
      // technically a JS error.
      // ```
      // class Sub extends Base {
      //   constructor() {
      //     let arrow = () => super.prop; // ERROR: super.prop comes before super()
      //     super();
      //     arrow(); // not really executed until here, though
      //   }
      // }
      // ```
      // This behavior is consistent with TypeScript.
      // In general there's no good reason to declare such arrow functions before calling `super()`.
      constructorContext.visitSuperPropertyAccess(t, superNode);
    }
  }
}
