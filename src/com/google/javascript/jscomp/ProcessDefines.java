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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING_BOOLEAN;
import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Process variables annotated as {@code @define}. A define is
 * a special constant that may be overridden by later files and
 * manipulated by the compiler, much like C preprocessor {@code #define}s.
 */
class ProcessDefines implements CompilerPass {

  /**
   * Defines in this set will not be flagged with "unknown define" warnings. There are flags that
   * always set these defines, even when they might not be in the binary.
   */
  private static final ImmutableSet<String> KNOWN_DEFINES =
      ImmutableSet.of("COMPILED", "goog.DEBUG", "$jscomp.ISOLATE_POLYFILLS");

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final ImmutableMap<String, Node> replacementValues;
  private final Mode mode;
  private final Supplier<GlobalNamespace> namespaceSupplier;

  private final LinkedHashSet<JSDocInfo> knownDefineJsdocs = new LinkedHashSet<>();
  private final LinkedHashMap<String, Define> defineByDefineName = new LinkedHashMap<>();
  private final LinkedHashSet<Node> validDefineValueExpressions = new LinkedHashSet<>();

  private GlobalNamespace namespace;

  // Warnings
  static final DiagnosticType UNKNOWN_DEFINE_WARNING = DiagnosticType.warning(
      "JSC_UNKNOWN_DEFINE_WARNING",
      "unknown @define variable {0}");

  // Errors
  static final DiagnosticType INVALID_DEFINE_TYPE =
      DiagnosticType.error("JSC_INVALID_DEFINE_TYPE", "@define tag only permits primitive types");

  static final DiagnosticType INVALID_DEFINE_VALUE =
      DiagnosticType.error(
          "JSC_INVALID_DEFINE_VALUE", "invalid initialization value for @define {0}");

  static final DiagnosticType INVALID_DEFINE_LOCATION =
      DiagnosticType.error(
          "JSC_INVALID_DEFINE_LOCATION",
          "@define must be initalized on a static qualified name in global or module scope");

  static final DiagnosticType NON_CONST_DEFINE =
      DiagnosticType.error("JSC_NON_CONST_DEFINE", "@define {0} has already been set at {1}.");

  /** Create a pass that overrides define constants. */
  private ProcessDefines(Builder builder) {
    this.mode = builder.mode;
    this.compiler = builder.compiler;
    this.registry = this.mode.check ? this.compiler.getTypeRegistry() : null;
    this.replacementValues = ImmutableMap.copyOf(builder.replacementValues);
    this.namespaceSupplier = builder.namespaceSupplier;
  }

  enum Mode {
    CHECK(true, false),
    OPTIMIZE(false, true),
    CHECK_AND_OPTIMIZE(true, true);

    private final boolean check;
    private final boolean optimize;

    Mode(boolean check, boolean optimize) {
      this.check = check;
      this.optimize = optimize;
    }
  }

  /** Builder for ProcessDefines. */
  static class Builder {
    private final AbstractCompiler compiler;
    private final Map<String, Node> replacementValues = new LinkedHashMap<>();
    private Mode mode;
    private Supplier<GlobalNamespace> namespaceSupplier;

    Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    Builder putReplacements(Map<String, Node> replacementValues) {
      this.replacementValues.putAll(replacementValues);
      return this;
    }

    Builder setMode(Mode x) {
      this.mode = x;
      return this;
    }

    /**
     * Injects a pre-computed global namespace, so that the same namespace can be re-used for
     * multiple check passes. Accepts a supplier because the namespace may not exist at
     * pass-creation time.
     */
    Builder injectNamespace(Supplier<GlobalNamespace> namespaceSupplier) {
      this.namespaceSupplier = namespaceSupplier;
      return this;
    }

    ProcessDefines build() {
      return new ProcessDefines(this);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    this.initNamespace(externs, root);
    this.collectDefines();
    this.reportDefineUnknownDeclarations(root);
    this.collectValidDefineValueExpressions();
    this.validateDefineDeclarations();
    this.overrideDefines();
  }

  final ImmutableSet<String> collectDefineNames(Node externs, Node root) {
    this.initNamespace(externs, root);
    this.collectDefines();

    return ImmutableSet.copyOf(this.defineByDefineName.keySet());
  }

  private void initNamespace(Node externs, Node root) {
    if (namespaceSupplier != null) {
      this.namespace = namespaceSupplier.get();
    }
    if (this.namespace == null) {
      this.namespace = new GlobalNamespace(compiler, externs, root);
    }
  }

  private void overrideDefines() {
    if (this.mode.optimize) {
      for (Define define : this.defineByDefineName.values()) {
        if (define.valueParent == null) {
          continue;
        }

        String defineName = define.defineName;

        Node inputValue = this.replacementValues.get(defineName);
        if (inputValue == null || inputValue == define.value) {
          continue;
        }

        boolean changed =
            define.value == null
                || inputValue.getToken() != define.value.getToken()
                || !inputValue.isEquivalentTo(define.value);
        if (changed) {
          if (define.value == null) {
            define.valueParent.addChildToBack(inputValue.cloneTree());
          } else {
            define.value.replaceWith(inputValue.cloneTree());
          }

          compiler.reportChangeToEnclosingScope(define.valueParent);
        }
      }
    }

    if (this.mode.check) {
      Set<String> unusedReplacements =
          Sets.difference(
              this.replacementValues.keySet(),
              Sets.union(KNOWN_DEFINES, this.defineByDefineName.keySet()));

      for (String unknownDefine : unusedReplacements) {
        compiler.report(JSError.make(UNKNOWN_DEFINE_WARNING, unknownDefine));
      }
    }
  }

  /**
   * Only defines of literal number, string, or boolean are supported.
   */
  private boolean isValidDefineType(JSTypeExpression expression) {
    JSType type = registry.evaluateTypeExpressionInGlobalScope(expression);
    return !type.isUnknownType()
        && type.isSubtypeOf(registry.getNativeType(NUMBER_STRING_BOOLEAN));
  }

  /** Finds all defines, and creates a {@link Define} data structure for each one. */
  private void collectDefines() {
    for (Name name : this.namespace.getAllSymbols()) {
      Ref declaration = this.selectDefineDeclaration(name);
      if (declaration == null) {
        continue;
      }

      int totalSets = name.getTotalSets();

      String defineName = firstNonNull(declaration.getNode().getDefineName(), name.getFullName());
      Define existingDefine =
          this.defineByDefineName.putIfAbsent(
              defineName, createDefine(defineName, name, declaration));

      if (existingDefine != null) {
        declaration = existingDefine.declaration;
        totalSets += existingDefine.name.getTotalSets();
      }

      /**
       * We have to report this here because otherwise we don't remember which names have the same
       * define name. It's not worth it tracking a set of names, because it makes the rest of the
       * pass more complex.
       */
      if (totalSets > 1) {
        for (Ref ref : name.getRefs()) {
          if (ref.isSet() && !ref.equals(declaration)) {
            this.compiler.report(
                JSError.make(
                    ref.getNode(),
                    NON_CONST_DEFINE,
                    defineName,
                    declaration.getNode().getLocation()));
          }
        }
      }
    }
  }

  @Nullable
  private Ref selectDefineDeclaration(Name name) {
    for (Ref ref : name.getRefs()) {
      // Make sure we don't select a local set as the declaration.
      if (!Ref.Type.SET_FROM_GLOBAL.equals(ref.type)) {
        continue;
      }

      Node refNode = ref.getNode();
      if (!refNode.isQualifiedName()) {
        continue;
      }

      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(refNode);
      if (jsdoc == null || !jsdoc.isDefine()) {
        continue;
      }

      this.knownDefineJsdocs.add(jsdoc);
      return ref;
    }

    return null;
  }

  private static Define createDefine(String defineName, Name name, Ref declaration) {
    checkState(declaration.isSet());

    // Note: this may be a NAME, a GETPROP, or even STRING_KEY or GETTER_DEF. We only care
    // about the first two, in which case the parent should be either VAR/CONST or ASSIGN.
    // We could accept STRING_KEY (i.e. `@define` on a property in an object literal), but
    // there's no reason to add another new way to do the same thing.
    Node declarationNode = declaration.getNode();
    Node declarationParent = declarationNode.getParent();

    Node valueParent = null;
    Node value = null;
    if (declarationParent.isVar() || declarationParent.isConst()) {
      // Simple case of `var` or `const`. There's no reason to support `let` here, and we
      // don't explicitly check that it's not `let` anywhere else.
      checkState(declarationNode.isName(), declarationNode);
      valueParent = declarationNode;
      value = declarationNode.getFirstChild();
    } else if (declarationParent.isAssign() && declarationNode.isFirstChildOf(declarationParent)) {
      // Assignment. Must either assign to a qualified name, or else be a different ref than
      // the declaration to not emit an error (we don't allow assignment before it's
      // declared).
      valueParent = declarationParent;
      value = declarationParent.getLastChild();
    }

    return new Define(defineName, name, declaration, valueParent, value);
  }

  private void collectValidDefineValueExpressions() {

    LinkedHashSet<Name> namesToCheck =
        this.namespace.getAllSymbols().stream()
            .filter(ProcessDefines::isGlobalConst)
            .collect(toCollection(LinkedHashSet::new));

    // All defines are implicitly valid in the values of other defines.
    for (Define define : this.defineByDefineName.values()) {
      namesToCheck.remove(define.name);
      define.name.getRefs().stream()
          .filter((r) -> !r.isSet())
          .map(Ref::getNode)
          .forEachOrdered(this.validDefineValueExpressions::add);
    }

    // Do a breadth-first search of all const names to find those defined in terms of valid values.
    while (true) {
      LinkedHashSet<Name> namesToCheckAgain = new LinkedHashSet<>();

      for (Name name : namesToCheck) {
        Node declValue = getConstantDeclValue(name.getDeclaration().getNode());
        switch (isValidDefineValue(declValue)) {
          case TRUE:
            for (Ref ref : name.getRefs()) {
              this.validDefineValueExpressions.add(ref.getNode());
            }
            break;

          case UNKNOWN:
            namesToCheckAgain.add(name);
            break;

          default:
        }
      }

      if (namesToCheckAgain.size() == namesToCheck.size()) {
        break;
      } else {
        namesToCheck = namesToCheckAgain;
      }
    }
  }

  private final void validateDefineDeclarations() {
    if (!this.mode.check) {
      return;
    }

    for (Define define : this.defineByDefineName.values()) {
      Node declarationNode = define.declaration.getNode();

      if (!this.hasValidValue(define)) {
        compiler.report(
            JSError.make(
                firstNonNull(define.value, firstNonNull(define.valueParent, declarationNode)),
                INVALID_DEFINE_VALUE,
                define.defineName));
      }

      /**
       * Process defines should not depend on check types being enabled, so we look for the JSDoc
       * instead of the inferred type.
       */
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(declarationNode);
      if (jsdoc == null || !isValidDefineType(jsdoc.getType())) {
        compiler.report(JSError.make(declarationNode, INVALID_DEFINE_TYPE));
      }
    }
  }

  private void reportDefineUnknownDeclarations(Node root) {
    if (!this.mode.check) {
      return;
    }

    /**
     * This has to be done using a traversal because the global namespace doesn't record symbols
     * which only appear in local scopes.
     *
     * <p>We don't check the externs because they can't contain local vars.
     */
    NodeTraversal.traversePostOrder(
        this.compiler,
        root,
        (t, n, parent) -> {
          JSDocInfo jsdoc = n.getJSDocInfo();
          if (jsdoc != null && jsdoc.isDefine() && this.knownDefineJsdocs.add(jsdoc)) {
            compiler.report(JSError.make(n, INVALID_DEFINE_LOCATION));
          }
        });
  }

  private boolean hasValidValue(Define define) {
    if (define.valueParent == null) {
      return false;
    } else if (define.valueParent.isFromExterns()) {
      return true;
    } else {
      return this.isValidDefineValue(define.value).toBoolean(false);
    }
  }

  private static boolean isGlobalConst(Name name) {
    return name.getTotalSets() == 1
        && name.getDeclaration() != null
        && name.getDeclaration().type.equals(Ref.Type.SET_FROM_GLOBAL);
  }

  /**
   * Determines whether the given value may be assigned to a define.
   *
   * @param val The value being assigned.
   */
  private TernaryValue isValidDefineValue(@Nullable Node val) {
    if (val == null) {
      return TernaryValue.FALSE;
    }

    switch (val.getToken()) {
      case STRING:
      case NUMBER:
      case TRUE:
      case FALSE:
        return TernaryValue.TRUE;

        // Binary operators are only valid if both children are valid.
      case AND:
      case OR:
      case ADD:
      case BITAND:
      case BITNOT:
      case BITOR:
      case BITXOR:
      case DIV:
      case EQ:
      case EXPONENT:
      case GE:
      case GT:
      case LE:
      case LSH:
      case LT:
      case MOD:
      case MUL:
      case NE:
      case RSH:
      case SHEQ:
      case SHNE:
      case SUB:
      case URSH:
        return isValidDefineValue(val.getFirstChild()).and(isValidDefineValue(val.getLastChild()));

      case HOOK:
        return isValidDefineValue(val.getFirstChild())
            .and(isValidDefineValue(val.getSecondChild()))
            .and(isValidDefineValue(val.getLastChild()));

        // Unary operators are valid if the child is valid.
      case NOT:
      case NEG:
      case POS:
        return isValidDefineValue(val.getFirstChild());

        // Names are valid if and only if they are defines themselves.
      case NAME:
      case GETPROP:
        if (val.isQualifiedName()) {
          return this.validDefineValueExpressions.contains(val)
              ? TernaryValue.TRUE
              : TernaryValue.UNKNOWN;
        }
        break;
      default:
        break;
    }

    return TernaryValue.FALSE;
  }

  /**
   * Checks whether the NAME node is inside either a CONST or a @const VAR. Returns the RHS node if
   * so, otherwise returns null.
   */
  private static Node getConstantDeclValue(Node name) {
    Node parent = name.getParent();
    if (parent == null) {
      return null;
    }
    if (name.isName()) {
      if (parent.isConst()) {
        return name.getFirstChild();
      } else if (!parent.isVar()) {
        return null;
      }
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(name);
      return jsdoc != null && jsdoc.isConstant() ? name.getFirstChild() : null;
    } else if (name.isGetProp() && parent.isAssign()) {
      JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(name);
      return jsdoc != null && jsdoc.isConstant() ? name.getNext() : null;
    }
    return null;
  }

  private static final class Define {
    final String defineName;
    final Name name;

    /**
     * The connonical set ref with an `@define` or `goog.define`.
     *
     * <p>This may not be the same as `name.getDeclaration()`.
     */
    final Ref declaration;

    @Nullable final Node valueParent;
    @Nullable final Node value;

    public Define(
        String defineName,
        Name name,
        Ref declaration,
        @Nullable Node valueParent,
        @Nullable Node value) {
      checkState(valueParent == null || value == null || value.getParent() == valueParent);
      checkState(declaration.isSet());
      checkState(declaration.name.equals(name));

      this.defineName = defineName;
      this.name = name;
      this.declaration = declaration;
      this.valueParent = valueParent;
      this.value = value;
    }
  }
}
