/*
 * Copyright 2021 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.OptimizeCalls.ReferenceMap;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * Optimize class declarations by removing explicit constructor declarations if the implicit
 * constructor is sufficient.
 *
 * <p>The constructor can be removed, if
 *
 * <p>(0) the class in question defined using ES class syntax
 *
 * <p>(2) the constructor is empty save for the call to super
 *
 * <p>(3) either:
 *
 * <p>- all constructor arguments are forward to the super constructor (via rest or arguments)
 *
 * <p>- super constructor parameters match the number, order, and default values of constructor to
 * be removed and the constructor doesn't look for additional parameters via references to
 * 'arguments'
 *
 * <p>When inspecting super class constructors, we don't need to worry about (1) reassignment of
 * subclasses or (2) escapes of the superclass. We only need to make sure the superclass isn't
 * reassigned. For that we can simply check for direct assignments and whether the name is defined
 * in the externs as references. The two other reassignment cases that are hard/impossible to
 * detect: "globalThis" properties and assignment through eval are only only supported with externs.
 *
 * <p>Note that an alternative exists where the super class could be ignored, if all constructor
 * references to the class were known (including direct calls, super calls, implicit constructor
 * calls, etc) to validate the number of parameters passed. This would require backing off on
 * various escapes and is believed it would be both more complicated and less effective.
 */
class OptimizeConstructors implements CompilerPass, OptimizeCalls.CallGraphCompilerPass {
  private final AbstractCompiler compiler;

  // All constructor definition nodes that are to be removed.
  final ArrayList<Node> removableConstructors = new ArrayList<>();

  OptimizeConstructors(AbstractCompiler compiler) {
    this.compiler = checkNotNull(compiler);
  }

  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);

    OptimizeCalls.builder()
        .setCompiler(compiler)
        .setConsiderExterns(false)
        .addPass(this)
        .build()
        .process(externs, root);
  }

  @Override
  public void process(Node externs, Node root, ReferenceMap refMap) {
    for (Map.Entry<String, ArrayList<Node>> entry : refMap.getNameReferences()) {
      addConstructorsToBeRemoved(entry.getKey(), entry.getValue());
    }

    // NOTE: Normally, the ReferenceMap must be kept in a consistent state (removing references
    // as they are removed from the AST) so the next pass can reuse the ReferenceMap. However,
    // currently this pass runs by itself so that work is avoided.
    //

    for (Node ref : removableConstructors) {
      removeConstructorMethod(ref);
    }
  }

  /**
   * Iterate over all the references to a symbol. There are several interesting references:
   *
   * <p>-a class definition
   *
   * <p>-a subclass definition
   *
   * <p>-class redefinition
   *
   * <p>-allowed reference (anything not an assignment)
   */
  private void addConstructorsToBeRemoved(String name, ArrayList<Node> refs) {
    if (!OptimizeCalls.mayBeOptimizableName(compiler, name)) {
      return;
    }

    Node candidateClassDefinition = null;

    // Lazily init as most symbols aren't class, and most classes don't have subclasses
    ArrayList<Node> subclassConstructors = null;

    for (Node n : refs) {
      Node definition = getClassDefinitionOrFunction(n);
      if (definition != null) {
        if (candidateClassDefinition != null) {
          // As a simplification only allow one definition.
          return;
        }

        // Be lazy about constructor analysis as it is expected that most classes
        // don't have subclasses.
        candidateClassDefinition = definition;

        if (candidateClassDefinition.isClass()) {
          // There are two special cases that we can handle just by looking at the extend clause

          // handle no super class
          //    - if no extends clause
          //    - and the constructor body is empty
          //    - and the constructor parameters are side-effect free (no destructuring or
          // side-effect defaults)
          Node extendsExpr = candidateClassDefinition.getSecondChild();
          if (extendsExpr.isEmpty()) {
            Node constructor =
                NodeUtil.getEs6ClassConstructorMemberFunctionDef(candidateClassDefinition);
            if (constructor != null) {
              Node fn = constructor.getLastChild();
              Node body = fn.getLastChild();
              if (!body.hasChildren() && hasRemovableParameterList(constructor)) {
                removableConstructors.add(constructor);
              }
            }
          } else {
            // TODO: handle "Object" super class
            //    - just a call to super and no-side-effects in the formal parameters, or super call
            // arguments
          }
        }
      } else {
        // If this is a reference to the class in an extend clause, then this is a class
        // definition whose constructor should considered for removal.
        if (isClassExtendsExpression(n)) {
          Node subclassLiteral = n.getParent();
          Node subclassConstructor =
              NodeUtil.getEs6ClassConstructorMemberFunctionDef(subclassLiteral);
          if (subclassConstructor != null
              && constructorHasRemovableDefinition(subclassConstructor)) {

            if (subclassConstructors == null) {
              subclassConstructors = new ArrayList<>();
            }
            subclassConstructors.add(subclassConstructor);
          }
        } else {
          // Anything assignment that isn't a class definition covered above, is an
          // invalidating assignment.
          if (isAssigningReference(n)) {
            return;
          }
        }
      }
    }

    // There is no known class definition
    if (candidateClassDefinition == null) {
      return;
    }

    // Nothing that invalidated the superclass definition was found, so now check if the
    // candidate subclasses are equivalent to the superclass.

    if (subclassConstructors != null) {
      ClassConstructorSummary summary = ClassConstructorSummary.build(candidateClassDefinition);
      for (Node n : subclassConstructors) {
        if (summary.isEquivalentConstructorDefinition(n)) {
          removableConstructors.add(n);
        }
      }
    }
  }

  /** Is the node in a position in the AST so that it might be assigned a value? */
  static boolean isAssigningReference(Node n) {
    Node parent = n.getParent();
    Node gparent = parent.getParent();
    switch (parent.getToken()) {
      case LET:
      case CONST:
      case VAR:
        return n.hasChildren(); // value assigned
      case STRING_KEY:
        return gparent.isObjectPattern();
      case COMPUTED_PROP:
        return parent.getLastChild() == n && gparent.isObjectPattern();
      case ARRAY_PATTERN:
      case DEFAULT_VALUE: // object or array or function parameter
      case PARAM_LIST:
      case OBJECT_REST:
      case ITER_REST:
      case INC:
      case DEC:
        return true;
      case FUNCTION:
      case CLASS:
      case CATCH:
        return parent.getFirstChild() == n;
      case ASSIGN:
      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN_EXPONENT:
        return parent.getFirstChild() != n;
      default:
        return false;
    }
  }

  private boolean isClassExtendsExpression(Node n) {
    Node parent = n.getParent();
    return (parent.isClass() && parent.getSecondChild() == n);
  }

  private @Nullable Node getClassDefinitionOrFunction(Node n) {
    Node parent = n.getParent();

    Node expr;
    if (ReferenceMap.isSimpleAssignmentTarget(n)) {
      expr = parent.getLastChild();
    } else if (n.isName() && n.hasChildren()) {
      expr = n.getFirstChild();
    } else if (parent.isFunction() && n.isFirstChildOf(parent)) {
      expr = parent;
    } else if (parent.isClass() && n.isFirstChildOf(parent)) {
      expr = parent;
    } else {
      return null; // Couldn't find a function.
    }

    expr = unwrap(expr);
    if (isDefinitionClassLiteralOrFunction(expr)) {
      return expr;
    } else {
      return null;
    }
  }

  private Node unwrap(Node expr) {
    while (expr.isCast() || expr.isComma()) {
      expr = expr.getLastChild();
    }
    return expr;
  }

  private static boolean isDefinitionClassLiteralOrFunction(Node n) {
    switch (n.getToken()) {
      case FUNCTION:
        // TODO(b/176208718): ideally this is only return true for normal functions, but it is
        // harmless to include other function types and checking for "normal" function is currently
        // non-trivial.
        return true;
      case CLASS:
        // `class NameNode {`
        // find the constructor
        Node constructorMemberFunctionDef = NodeUtil.getEs6ClassConstructorMemberFunctionDef(n);
        return constructorMemberFunctionDef != null;
      default:
        return false;
    }
  }

  /**
   * An abstraction of a super class constructor definition. The goal of this class is to avoid
   * repeated analysis of the super class constructor when checking whether a subclass constructor
   * is removable.
   */
  private static class ClassConstructorSummary {
    // The number of explicit parameters
    final int formalParameterCount;
    // "var args" means any number of parameters are allowed
    final boolean isVarArgs;

    ClassConstructorSummary(boolean isVarArgs, int formalParameterCount) {
      this.isVarArgs = isVarArgs;
      this.formalParameterCount = formalParameterCount;
    }

    static ClassConstructorSummary build(Node classDefinition) {
      checkState(classDefinition.isClass() || classDefinition.isFunction());
      Node fn;
      if (classDefinition.isClass()) {
        // NOTE: it would be possible to handle implicit superclass definitions by caching
        // the super class constructor summaries.
        Node member = NodeUtil.getEs6ClassConstructorMemberFunctionDef(classDefinition);
        fn = member.getFirstChild();
      } else {
        fn = classDefinition;
      }

      boolean argumentsReference = NodeUtil.doesFunctionReferenceOwnArgumentsObject(fn);
      boolean hasVarArgs = argumentsReference || functionHasRest(fn);
      return new ClassConstructorSummary(
          hasVarArgs, NodeUtil.getFunctionParameters(fn).getChildCount());
    }

    public boolean isEquivalentConstructorDefinition(Node constructorMember) {
      // `constructorHasRemovableDefinition` has already checked the subclass definition. We
      // know that it has a trivial constructor and that the parameters are passed to the super call
      // in the same order as they are declared in the parameter list, etc.

      // As a result we can simply check the parameters of the constructor and
      // validate they are sufficient for the constructor of the super class

      Node fn = constructorMember.getFirstChild();
      Node paramList = NodeUtil.getFunctionParameters(fn);

      boolean hasRest = functionHasRest(fn);
      if (hasRest) {
        return true;
      }

      if (this.isVarArgs) {
        // The count of parameters may matter, and the function may not pass them all on.
        return false;
      }

      if (this.formalParameterCount != paramList.getChildCount()) {
        return false;
      }

      // same count
      return true;
    }
  }

  static boolean functionHasRest(Node fn) {
    checkState(fn.isFunction());
    Node params = NodeUtil.getFunctionParameters(fn);
    Node lastParam = params.getLastChild();
    return lastParam != null && lastParam.isRest();
  }

  /**
   * Validate that nothing about the constructor definition itself prevents its removal: - the body
   * is simply a super call - it passes on exactly what it receives. These are necessary but not
   * sufficient for the constructor to be removable.
   */
  private static boolean constructorHasRemovableDefinition(Node member) {
    Node fn = member.getFirstChild();
    Node superCall = getOnlySuperCall(fn);
    if (superCall == null) {
      return false;
    }

    Node paramList = NodeUtil.getFunctionParameters(fn);
    if (paramList.getChildCount() != superCall.getChildCount() - 1) {
      return false;
    }

    // TODO(johnlenz): broaden the recognized patterns: default parameters
    Node param = paramList.getFirstChild();
    Node arg = superCall.getSecondChild();
    while (param != null) {
      if (param.isRest()) {
        if (!arg.isSpread() || !param.getFirstChild().matchesName(arg.getFirstChild())) {
          return false;
        }
      } else if (!param.matchesName(arg)) {
        // not a simple parameter list or matching call args
        return false;
      }
      param = param.getNext();
      arg = arg.getNext();
    }

    return true;
  }

  /** True if the parameter list can't cause side-effects */
  private boolean hasRemovableParameterList(Node member) {
    Node fn = member.getFirstChild();
    Node paramList = NodeUtil.getFunctionParameters(fn);

    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      if (param.isName()) {
        // a simple name
        continue;
      }
      if (param.isRest() && param.getFirstChild().isName()) {
        // a simple rest expression
        continue;
      }

      if (param.isDefaultValue()
          && param.getFirstChild().isName()
          && !new AstAnalyzer(compiler, true).mayHaveSideEffects(param.getLastChild())) {
        // a default parameter whose value is determined to be side-effect free
        continue;
      }

      // not a simple parameter (destructuring can throw, etc)
      return false;
    }

    return true;
  }

  /** If the body contains only a call to super, return it, otherwise null. */
  private static @Nullable Node getOnlySuperCall(Node fn) {
    Node body = fn.getLastChild();
    if (body.isBlock() && body.hasOneChild()) {
      Node stmt = body.getFirstChild();
      if (stmt.isExprResult()) {
        Node call = stmt.getFirstChild();
        if (call.isCall() && call.getFirstChild().isSuper()) {
          return call;
        }
      }
    }
    return null;
  }

  /** Removes any candidate constructor if the callers are consistent with the definition. */
  private void removeConstructorMethod(Node member) {
    checkState(member.isMemberFunctionDef());

    compiler.reportFunctionDeleted(member.getFirstChild());
    compiler.reportChangeToEnclosingScope(member);
    member.detach();

    // NOTE: As this pass is expected to run by itself, so don't spend the time to update the
    // reference map.
  }
}
