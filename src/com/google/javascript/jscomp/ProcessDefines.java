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
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_STRING_BOOLEAN;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Process variables annotated as {@code @define}. A define is
 * a special constant that may be overridden by later files and
 * manipulated by the compiler, much like C preprocessor {@code #define}s.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class ProcessDefines implements CompilerPass {
  private static final Logger logger =
      Logger.getLogger("com.google.javascript.jscomp.ProcessDefines");

  /**
   * Defines in this set will not be flagged with "unknown define" warnings. There are legacy flags
   * that always set these defines, even when they might not be in the binary.
   */
  private static final ImmutableSet<String> KNOWN_DEFINES =
      ImmutableSet.of("COMPILED", "goog.DEBUG");

  private final AbstractCompiler compiler;
  private final Map<String, Node> dominantReplacements;
  private final boolean checksOnly;
  private final Supplier<GlobalNamespace> namespaceSupplier;

  // Warnings
  static final DiagnosticType UNKNOWN_DEFINE_WARNING = DiagnosticType.warning(
      "JSC_UNKNOWN_DEFINE_WARNING",
      "unknown @define variable {0}");

  // Errors
  static final DiagnosticType INVALID_DEFINE_TYPE_ERROR =
    DiagnosticType.error(
        "JSC_INVALID_DEFINE_TYPE_ERROR",
        "@define tag only permits literal types");

  static final DiagnosticType INVALID_DEFINE_INIT_ERROR =
      DiagnosticType.error(
          "JSC_INVALID_DEFINE_INIT_ERROR",
          "illegal initialization of @define variable {0}");

  static final DiagnosticType NON_GLOBAL_DEFINE_INIT_ERROR =
      DiagnosticType.error(
          "JSC_NON_GLOBAL_DEFINE_INIT_ERROR",
          "@define variable {0} assignment must be global");

  static final DiagnosticType DEFINE_NOT_ASSIGNABLE_ERROR =
      DiagnosticType.error(
          "JSC_DEFINE_NOT_ASSIGNABLE_ERROR",
          "@define variable {0} cannot be reassigned due to code at {1}.");

  private static final MessageFormat REASON_DEFINE_NOT_ASSIGNABLE =
      new MessageFormat("line {0} of {1}");

  /** Create a pass that overrides define constants. */
  private ProcessDefines(Builder builder) {
    this.compiler = builder.compiler;
    this.dominantReplacements = ImmutableMap.copyOf(builder.replacements);
    this.checksOnly = builder.checksOnly;
    this.namespaceSupplier = builder.namespaceSupplier;
  }

  /** Builder for ProcessDefines. */
  static class Builder {
    private final AbstractCompiler compiler;
    private final Map<String, Node> replacements = new LinkedHashMap<>();
    private boolean checksOnly;
    private Supplier<GlobalNamespace> namespaceSupplier;

    Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    Builder putReplacements(Map<String, Node> replacements) {
      this.replacements.putAll(replacements);
      return this;
    }

    Builder checksOnly(boolean checksOnly) {
      this.checksOnly = checksOnly;
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
    overrideDefines(collectDefines(externs, root));
  }

  private void overrideDefines(Map<String, DefineInfo> allDefines) {
    if (!checksOnly) {
      for (Map.Entry<String, DefineInfo> def : allDefines.entrySet()) {
        String defineName = def.getKey();
        DefineInfo info = def.getValue();
        Node inputValue = dominantReplacements.get(defineName);
        Node finalValue = inputValue != null ? inputValue : info.getLastValue();
        if (finalValue != info.initialValue) {
          if (logger.isLoggable(Level.FINE)) {
            logger.fine("Overriding @define variable " + defineName);
          }
          boolean changed =
              finalValue.getToken() != info.initialValue.getToken()
              || !finalValue.isEquivalentTo(info.initialValue);
          if (changed) {
            info.initialValueParent.replaceChild(
                info.initialValue, finalValue.cloneTree());
            if (changed) {
              compiler.reportChangeToEnclosingScope(info.initialValueParent);
            }
          }
        }
      }
    }

    Set<String> unusedReplacements = Sets.difference(
        dominantReplacements.keySet(), Sets.union(KNOWN_DEFINES, allDefines.keySet()));

    for (String unknownDefine : unusedReplacements) {
      compiler.report(JSError.make(UNKNOWN_DEFINE_WARNING, unknownDefine));
    }
  }

  private static String format(MessageFormat format, Object... params) {
    return format.format(params);
  }

  /**
   * Only defines of literal number, string, or boolean are supported.
   */
  private boolean isValidDefineType(JSTypeExpression expression) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType type = registry.evaluateTypeExpressionInGlobalScope(expression);
    return !type.isUnknownType()
        && type.isSubtypeOf(registry.getNativeType(NUMBER_STRING_BOOLEAN));
  }

  /**
   * Finds all defines, and creates a {@link DefineInfo} data structure for each one.
   *
   * @return A map of {@link DefineInfo} structures, keyed by name.
   */
  Map<String, DefineInfo> collectDefines(Node externs, Node root) {
    GlobalNamespace namespace = null;
    if (namespaceSupplier != null) {
      namespace = namespaceSupplier.get();
    }
    if (namespace == null) {
      namespace = new GlobalNamespace(compiler, externs, root);
    }

    // namespace =
    //     namespaceSupplier != nul
    //         ? namespaceSupplier.get()
    //         : new GlobalNamespace(compiler, externs, root);

    // Find all the global names with a @define annotation
    List<Name> listOfDefines = new ArrayList<>();
    for (Name name : namespace.getNameIndex().values()) {
      Ref decl = name.getDeclaration();
      if (name.getJSDocInfo() != null && name.getJSDocInfo().isDefine()) {
        // Process defines should not depend on check types being enabled,
        // so we look for the JSDoc instead of the inferred type.
        if (isValidDefineType(name.getJSDocInfo().getType())) {
          listOfDefines.add(name);
        } else {
          JSError error = JSError.make(decl.getNode(), INVALID_DEFINE_TYPE_ERROR);
          compiler.report(error);
        }
      } else {
        for (Ref ref : name.getRefs()) {
          if (ref == decl) {
            // Declarations were handled above.
            continue;
          }

          Node n = ref.getNode();
          Node parent = ref.getNode().getParent();
          JSDocInfo info = n.getJSDocInfo();
          if (info == null && parent.isVar() && parent.hasOneChild()) {
            info = parent.getJSDocInfo();
          }

          if (info != null && info.isDefine()) {
            listOfDefines.add(name);
            break;
          }
        }
      }
    }

    CollectDefines pass = new CollectDefines(namespace, listOfDefines);
    NodeTraversal.traverseRoots(compiler, pass, externs, root);
    return pass.allDefines;
  }

  /** Finds all assignments to @defines, and figures out the last value of the @define. */
  private final class CollectDefines implements Callback {

    private final Map<String, DefineInfo> assignableDefines = new HashMap<>();
    private final Map<String, DefineInfo> allDefines = new HashMap<>();
    private final Map<Node, RefInfo> allRefInfo = new HashMap<>();
    private final Set<Node> validDefineAliases = new HashSet<>();

    // A hack that allows us to remove ASSIGN/VAR statements when
    // we're currently visiting one of the children of the assign.
    private Node lvalueToRemoveLater = null;

    // A stack tied to the node traversal, to keep track of whether
    // we're in a conditional block. If 1 is at the top, assignment to
    // a define is allowed. Otherwise, it's not allowed.
    private final Deque<Integer> assignAllowed = new ArrayDeque<>();

    // listOfDefines is a list of all Names annotated with @define.
    CollectDefines(GlobalNamespace namespace, List<Name> listOfDefines) {
      assignAllowed.push(1);

      // Create a map of references to defines keyed by node for easy lookup
      // This map also includes aliases to defines
      Set<Name> symbols = new HashSet<>();
      Iterables.addAll(symbols, namespace.getAllSymbols());
      for (Name name : listOfDefines) {
        symbols.remove(name);
        Ref decl = name.getDeclaration();
        if (decl != null) {
          allRefInfo.put(decl.getNode(), new RefInfo(decl, name));
        }
        for (Ref ref : name.getRefs()) {
          if (ref == decl) {
            // Declarations were handled above.
            continue;
          }

          // If there's a TWIN def, only put one of the twins in.
          if (ref.getTwin() == null || !ref.getTwin().isSet()) {
            allRefInfo.put(ref.getNode(), new RefInfo(ref, name));
          }
        }
      }

      // Find any valid aliases by looking at all constant definitions and determining whether the
      // RHS is a valid define value.  If any alias is actually added to the list of refs, the
      // loop will repeat, checking only the constant declarations whose values were still
      // indeterminate from the previous iteration.
      Iterables.addAll(validDefineAliases, allRefInfo.keySet());
      boolean repeat = true;
      while (repeat) {
        repeat = false;
        Set<Name> indeterminateNames = new HashSet<>();
        for (Name name : symbols) {
          if (name.getDeclaration() != null) {
            Node declValue = getConstantDeclValue(name.getDeclaration().getNode());
            // Make sure this is a constant.
            TernaryValue validDefine =
                declValue != null ? isValidDefineValue(declValue) : TernaryValue.FALSE;
            if (validDefine.toBoolean(false)) {
              for (Ref ref : name.getRefs()) {
                validDefineAliases.add(ref.getNode());
              }
              repeat = true;
            }
            if (validDefine == TernaryValue.UNKNOWN) {
              indeterminateNames.add(name);
            }
          }
        }
        symbols = indeterminateNames;
      }
    }

    /**
     * Keeps track of whether the traversal is in a conditional branch.
     * We traverse all nodes of the parse tree.
     */
    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n,
        Node parent) {
      updateAssignAllowedStack(n, true);
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      RefInfo refInfo = allRefInfo.get(n);
      if (refInfo != null) {
        Ref ref = refInfo.ref;
        Name name = refInfo.name;
        // If the (qualified) name node had a DEFINE_NAME prop added to it (i.e. by the closure
        // pass) then use that name instead of the name assigned in the AST.  This happens any time
        // the result of goog.define is assigned to something (i.e. all the time, once it stops
        // exporting the global variable).  This allows goog.define to have more flexibility than
        // simple @define.
        String fullName =
            MoreObjects.firstNonNull(ref.getNode().getDefineName(), name.getFullName());
        switch (ref.type) {
          case SET_FROM_GLOBAL:
          case SET_FROM_LOCAL:
            // Note: this may be a NAME, a GETPROP, or even STRING_KEY or GETTER_DEF. We only care
            // about the first two, in which case the parent should be either VAR/CONST or ASSIGN.
            // We could accept STRING_KEY (i.e. `@define` on a property in an object literal), but
            // there's no reason to add another new way to do the same thing.
            Node nameNode = ref.getNode();
            Node nameParent = nameNode.getParent();
            // The assigned value is either the RHS of an assign, or the child of a name node.
            // If the reference is a stub from externs, then the assigned value will be null.
            Node assignedValue = null;
            // The parent of the assigned value, or the NAME node of a stub.
            Node valueParent = null;

            // Set valueParent if this is a valid define initializer.
            if (nameParent.isVar() || nameParent.isConst()) {
              // Simple case of `var` or `const`. There's no reason to support `let` here, and we
              // don't explicitly check that it's not `let` anywhere else.
              checkState(nameNode.isName(), nameNode);
              assignedValue = nameNode.getFirstChild();
              valueParent = nameNode;
            } else if (nameParent.isAssign() && nameNode.isFirstChildOf(nameParent)) {
              // Assignment. Must either assign to a qualified name, or else be a different ref than
              // the declaration to not emit an error (we don't allow assignment before it's
              // declared).
              assignedValue = nameParent.getLastChild();
              if (!name.isSimpleName() || name.getDeclaration() != ref) {
                valueParent = nameParent;
              }
            } else if (nameNode.isFromExterns()) {
              // Stub, only allowed in externs. There is no value in this case.
              valueParent = nameNode;
            } else {
              // Anything else is an error (destructuring assignments, object literals, etc). We
              // don't support @define on any of these non-simple assignments, but can at least make
              // a "best guess" at the assigned value for the node to error on.
              assignedValue = nameParent.getLastChild();
            }

            if (valueParent == null) {
              compiler.report(JSError.make(assignedValue, INVALID_DEFINE_INIT_ERROR, fullName));
            } else if (processDefineAssignment(fullName, assignedValue, valueParent)) {
              // remove the assignment so that the variable is still declared,
              // but no longer assigned to a value, e.g.,
              // DEF_FOO = 5; // becomes "5;"

              // We can't remove the ASSIGN/VAR when we're still visiting its
              // children, so we'll have to come back later to remove it.
              refInfo.name.removeRef(ref);
              lvalueToRemoveLater = valueParent;
            }
            break;
          default:
            if (t.inGlobalHoistScope()) {
              // Treat this as a reference to a define in the global scope.
              // After this point, the define must not be reassigned,
              // or it's an error.
              DefineInfo info = assignableDefines.get(fullName);
              if (info != null) {
                setDefineInfoNotAssignable(info, t);
                assignableDefines.remove(fullName);
              }
            }
            break;
        }
      }

      if (!t.inGlobalScope() && n.getJSDocInfo() != null && n.getJSDocInfo().isDefine()) {
        // warn about @define annotations in local scopes
        compiler.report(JSError.make(n, NON_GLOBAL_DEFINE_INIT_ERROR, ""));
      }

      if (lvalueToRemoveLater == n) {
        lvalueToRemoveLater = null;
        if (n.isAssign()) {
          Node last = n.getLastChild();
          n.removeChild(last);
          parent.replaceChild(n, last);
        } else {
          checkState(n.isName(), n);
          n.removeFirstChild();
        }
        t.reportCodeChange();
      }

      if (n.isCall()) {
        if (t.inGlobalScope()) {
          // If there's a function call in the global scope,
          // we just say it's unsafe and freeze all the defines.
          //
          // NOTE(nicksantos): We could be a lot smarter here. For example,
          // ReplaceOverriddenVars keeps a call graph of all functions and
          // which functions/variables that they reference, and tries
          // to statically determine which functions are "safe" and which
          // are not. But this would be overkill, especially because
          // the intended use of defines is with config_files, where
          // all the defines are at the top of the bundle.
          for (DefineInfo info : assignableDefines.values()) {
            setDefineInfoNotAssignable(info, t);
          }

          assignableDefines.clear();
        }
      }

      updateAssignAllowedStack(n, false);
    }

    /**
     * Determines whether assignment to a define should be allowed
     * in the subtree of the given node, and if not, records that fact.
     *
     * @param n The node whose subtree we're about to enter or exit.
     * @param entering True if we're entering the subtree, false otherwise.
     */
    private void updateAssignAllowedStack(Node n, boolean entering) {
      switch (n.getToken()) {
        case CASE:
        case FOR:
        case FOR_IN:
        case FUNCTION:
        case HOOK:
        case IF:
        case SWITCH:
        case WHILE:
          if (entering) {
            assignAllowed.push(0);
          } else {
            assignAllowed.remove();
          }
          break;
        default:
          break;
      }
    }

    /**
     * Determines whether assignment to a define should be allowed
     * at the current point of the traversal.
     */
    private boolean isAssignAllowed() {
      return assignAllowed.element() == 1;
    }

    /**
     * Tracks the given define.
     *
     * @param name The full name for this define.
     * @param value The value assigned to the define.
     * @param valueParent The parent node of value.
     * @return Whether we should remove this assignment from the parse tree.
     */
    private boolean processDefineAssignment(String name, Node value, Node valueParent) {
      boolean fromExterns = valueParent.isFromExterns();
      if (!fromExterns && (value == null || !isValidDefineValue(value).toBoolean(false))) {
        Node errNode = value == null ? valueParent : value;
        compiler.report(JSError.make(errNode, INVALID_DEFINE_INIT_ERROR, name));
      } else if (!isAssignAllowed()) {
        compiler.report(JSError.make(valueParent, NON_GLOBAL_DEFINE_INIT_ERROR, name));
      } else {
        DefineInfo info = allDefines.get(name);
        if (info == null) {
          // First declaration of this define.
          info = new DefineInfo(value, valueParent);
          allDefines.put(name, info);
          assignableDefines.put(name, info);
        } else if (info.recordAssignment(value)) {
          // The define was already initialized, but this is a safe
          // re-assignment.
          return true;
        } else {
          // The define was already initialized, and this is an unsafe
          // re-assignment.
          compiler.report(
              JSError.make(
                  valueParent,
                  DEFINE_NOT_ASSIGNABLE_ERROR,
                  name,
                  info.getReasonWhyNotAssignable()));
        }
      }

      return false;
    }

    /**
     * Determines whether the given value may be assigned to a define.
     *
     * @param val The value being assigned.
     * @param defines The list of names of existing defines.
     */
    TernaryValue isValidDefineValue(Node val) {
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
          return isValidDefineValue(val.getFirstChild())
              .and(isValidDefineValue(val.getLastChild()));

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
            return validDefineAliases.contains(val) ? TernaryValue.TRUE : TernaryValue.UNKNOWN;
          }
          break;
        default:
          break;
      }
      return TernaryValue.FALSE;
    }
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

  /**
   * Records the fact that because of the current node in the node traversal, the define can't ever
   * be assigned again.
   *
   * @param info Represents the define variable.
   * @param t The current traversal.
   */
  private static void setDefineInfoNotAssignable(DefineInfo info, NodeTraversal t) {
    info.setNotAssignable(
        format(REASON_DEFINE_NOT_ASSIGNABLE, t.getLineNumber(), t.getSourceName()));
  }

  /** A simple data structure for associating a Ref with the name that it references. */
  private static class RefInfo {
    final Ref ref;
    final Name name;

    RefInfo(Ref ref, Name name) {
      this.ref = ref;
      this.name = name;
    }
  }

  /**
   * A simple class for storing information about a define.
   * Gathers the initial value, the last assigned value, and whether
   * the define can be safely assigned a new value.
   */
  private static final class DefineInfo {
    public final Node initialValueParent;
    public final @Nullable Node initialValue;
    private Node lastValue;
    private boolean isAssignable;
    private String reasonNotAssignable;

    /**
     * Initializes a define.
     */
    public DefineInfo(@Nullable Node initialValue, Node initialValueParent) {
      checkState(initialValue != null || initialValueParent.isFromExterns());
      this.initialValueParent = initialValueParent;
      this.initialValue = initialValue;
      lastValue = initialValue;
      isAssignable = true;
    }

    /**
     * Records the fact that this define can't be assigned a value anymore.
     *
     * @param reason A message describing the reason why it can't be assigned.
     */
    public void setNotAssignable(String reason) {
      isAssignable = false;
      reasonNotAssignable = reason;
    }

    /**
     * Gets the reason why a define is not assignable.
     */
    public String getReasonWhyNotAssignable() {
      return reasonNotAssignable;
    }

    /**
     * Records an assigned value.
     *
     * @return False if there was an error.
     */
    public boolean recordAssignment(Node value) {
      lastValue = value;
      return isAssignable;
    }

    /**
     * Gets the last assigned value.
     */
    public Node getLastValue() {
      return lastValue;
    }
  }
}
