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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;

import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Process variables annotated as {@code @define}. A define is
 * a special constant that may be overridden by later files and
 * manipulated by the compiler, much like C preprocessor {@code #define}s.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class ProcessDefines implements CompilerPass {

  /**
   * Defines in this set will not be flagged with "unknown define" warnings.
   * There are legacy flags that always set these defines, even when they
   * might not be in the binary.
   */
  private static final Set<String> KNOWN_DEFINES = ImmutableSet.of("COMPILED");

  private final AbstractCompiler compiler;
  private final Map<String, Node> dominantReplacements;

  private GlobalNamespace namespace = null;

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

  /**
   * Create a pass that overrides define constants.
   *
   * TODO(nicksantos): Write a builder to help JSCompiler induce
   *    {@code replacements} from command-line flags
   *
   * @param replacements A hash table of names of defines to their replacements.
   *   All replacements <b>must</b> be literals.
   */
  ProcessDefines(AbstractCompiler compiler, Map<String, Node> replacements) {
    this.compiler = compiler;
    dominantReplacements = replacements;
  }

  /**
   * Injects a pre-computed global namespace, so that the same namespace
   * can be re-used for multiple check passes. Returns {@code this} for
   * easy chaining.
   */
  ProcessDefines injectNamespace(GlobalNamespace namespace) {
    this.namespace = namespace;
    return this;
  }

  @Override
  public void process(Node externs, Node root) {
    if (namespace == null) {
      namespace = new GlobalNamespace(compiler, root);
    }
    overrideDefines(collectDefines(root, namespace));
  }

  private void overrideDefines(Map<String, DefineInfo> allDefines) {
    boolean changed = false;
    for (Map.Entry<String, DefineInfo> def : allDefines.entrySet()) {
      String defineName = def.getKey();
      DefineInfo info = def.getValue();
      Node inputValue = dominantReplacements.get(defineName);
      Node finalValue = inputValue != null ?
          inputValue : info.getLastValue();
      if (finalValue != info.initialValue) {
        info.initialValueParent.replaceChild(
            info.initialValue, finalValue.cloneTree());
        compiler.addToDebugLog("Overriding @define variable " + defineName);
        changed = changed ||
            finalValue.getType() != info.initialValue.getType() ||
            !finalValue.isEquivalentTo(info.initialValue);
      }
    }

    if (changed) {
      compiler.reportCodeChange();
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
    JSType type = expression.evaluate(null, compiler.getTypeRegistry());
    return !type.isUnknownType() && type.isSubtype(
        compiler.getTypeRegistry().getNativeType(
            JSTypeNative.NUMBER_STRING_BOOLEAN));
  }

  /**
   * Finds all defines, and creates a {@link DefineInfo} data structure for
   * each one.
   * @return A map of {@link DefineInfo} structures, keyed by name.
   */
  private Map<String, DefineInfo> collectDefines(Node root,
      GlobalNamespace namespace) {
    // Find all the global names with a @define annotation
    List<Name> allDefines = Lists.newArrayList();
    for (Name name : namespace.getNameIndex().values()) {
      Ref decl = name.getDeclaration();
      if (name.docInfo != null && name.docInfo.isDefine()) {
        // Process defines should not depend on check types being enabled,
        // so we look for the JSDoc instead of the inferred type.
        if (isValidDefineType(name.docInfo.getType())) {
          allDefines.add(name);
        } else {
          JSError error = JSError.make(
              decl.node, INVALID_DEFINE_TYPE_ERROR);
          compiler.report(error);
        }
      } else {
        for (Ref ref : name.getRefs()) {
          if (ref == decl) {
            // Declarations were handled above.
            continue;
          }

          Node n = ref.node;
          Node parent = ref.node.getParent();
          JSDocInfo info = n.getJSDocInfo();
          if (info == null &&
              parent.isVar() && parent.hasOneChild()) {
            info = parent.getJSDocInfo();
          }

          if (info != null && info.isDefine()) {
            allDefines.add(name);
            break;
          }
        }
      }
    }

    CollectDefines pass = new CollectDefines(compiler, allDefines);
    NodeTraversal.traverse(compiler, root, pass);
    return pass.getAllDefines();
  }

  /**
   * Finds all assignments to @defines, and figures out the last value of
   * the @define.
   */
  private static final class CollectDefines implements Callback {

    private final AbstractCompiler compiler;
    private final Map<String, DefineInfo> assignableDefines;
    private final Map<String, DefineInfo> allDefines;
    private final Map<Node, RefInfo> allRefInfo;

    // A hack that allows us to remove ASSIGN/VAR statements when
    // we're currently visiting one of the children of the assign.
    private Node lvalueToRemoveLater = null;

    // A stack tied to the node traversal, to keep track of whether
    // we're in a conditional block. If 1 is at the top, assignment to
    // a define is allowed. Otherwise, it's not allowed.
    private final Deque<Integer> assignAllowed;

    CollectDefines(AbstractCompiler compiler, List<Name> listOfDefines) {
      this.compiler = compiler;
      this.allDefines = Maps.newHashMap();

      assignableDefines = Maps.newHashMap();
      assignAllowed = new ArrayDeque<>();
      assignAllowed.push(1);

      // Create a map of references to defines keyed by node for easy lookup
      allRefInfo = Maps.newHashMap();
      for (Name name : listOfDefines) {
        Ref decl = name.getDeclaration();
        if (decl != null) {
          allRefInfo.put(decl.node,
                         new RefInfo(decl, name));
        }
        for (Ref ref : name.getRefs()) {
          if (ref == decl) {
            // Declarations were handled above.
            continue;
          }

          // If there's a TWIN def, only put one of the twins in.
          if (ref.getTwin() == null || !ref.getTwin().isSet()) {
            allRefInfo.put(ref.node, new RefInfo(ref, name));
          }
        }
      }
    }

    /**
     * Get a map of {@link DefineInfo} structures, keyed by the name of
     * the define.
     */
    Map<String, DefineInfo> getAllDefines() {
      return allDefines;
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
        String fullName = name.getFullName();
        switch (ref.type) {
          case SET_FROM_GLOBAL:
          case SET_FROM_LOCAL:
            Node valParent = getValueParent(ref);
            Node val = valParent.getLastChild();
            if (valParent.isAssign() && name.isSimpleName() &&
                name.getDeclaration() == ref) {
              // For defines, it's an error if a simple name is assigned
              // before it's declared
              compiler.report(
                  t.makeError(val, INVALID_DEFINE_INIT_ERROR, fullName));
            } else if (processDefineAssignment(t, fullName, val, valParent)) {
              // remove the assignment so that the variable is still declared,
              // but no longer assigned to a value, e.g.,
              // DEF_FOO = 5; // becomes "5;"

              // We can't remove the ASSIGN/VAR when we're still visiting its
              // children, so we'll have to come back later to remove it.
              refInfo.name.removeRef(ref);
              lvalueToRemoveLater = valParent;
            }
            break;
          default:
            if (t.inGlobalScope()) {
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

      if (!t.inGlobalScope() &&
          n.getJSDocInfo() != null && n.getJSDocInfo().isDefine()) {
        // warn about @define annotations in local scopes
        compiler.report(
            t.makeError(n, NON_GLOBAL_DEFINE_INIT_ERROR, ""));
      }

      if (lvalueToRemoveLater == n) {
        lvalueToRemoveLater = null;
        if (n.isAssign()) {
          Node last = n.getLastChild();
          n.removeChild(last);
          parent.replaceChild(n, last);
        } else {
          Preconditions.checkState(n.isName());
          n.removeChild(n.getFirstChild());
        }
        compiler.reportCodeChange();
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
      switch (n.getType()) {
        case Token.CASE:
        case Token.FOR:
        case Token.FUNCTION:
        case Token.HOOK:
        case Token.IF:
        case Token.SWITCH:
        case Token.WHILE:
          if (entering) {
            assignAllowed.push(0);
          } else {
            assignAllowed.remove();
          }
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
     * @param t The current traversal, for context.
     * @param name The full name for this define.
     * @param value The value assigned to the define.
     * @param valueParent The parent node of value.
     * @return Whether we should remove this assignment from the parse tree.
     */
    private boolean processDefineAssignment(NodeTraversal t,
        String name, Node value, Node valueParent) {
      if (value == null || !NodeUtil.isValidDefineValue(value,
                                                        allDefines.keySet())) {
        compiler.report(
            t.makeError(value, INVALID_DEFINE_INIT_ERROR, name));
      } else if (!isAssignAllowed()) {
        compiler.report(
            t.makeError(valueParent, NON_GLOBAL_DEFINE_INIT_ERROR, name));
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
              t.makeError(valueParent, DEFINE_NOT_ASSIGNABLE_ERROR,
                  name, info.getReasonWhyNotAssignable()));
        }
      }

      return false;
    }

    /**
     * Gets the parent node of the value for any assignment to a Name.
     * For example, in the assignment
     * {@code var x = 3;}
     * the parent would be the NAME node.
     */
    private static Node getValueParent(Ref ref) {
      // there are two types of declarations: VARs and ASSIGNs
      return ref.node.getParent() != null &&
          ref.node.getParent().isVar() ?
          ref.node : ref.node.getParent();
    }

    /**
     * Records the fact that because of the current node in the node traversal,
     * the define can't ever be assigned again.
     *
     * @param info Represents the define variable.
     * @param t The current traversal.
     */
    private static void setDefineInfoNotAssignable(DefineInfo info, NodeTraversal t) {
      info.setNotAssignable(format(REASON_DEFINE_NOT_ASSIGNABLE,
                                t.getLineNumber(), t.getSourceName()));
    }

    /**
     * A simple data structure for associating a Ref with the name
     * that it references.
     */
    private static class RefInfo {
      final Ref ref;
      final Name name;

      RefInfo(Ref ref, Name name) {
        this.ref = ref;
        this.name = name;
      }
    }
  }

  /**
   * A simple class for storing information about a define.
   * Gathers the initial value, the last assigned value, and whether
   * the define can be safely assigned a new value.
   */
  private static final class DefineInfo {
    public final Node initialValueParent;
    public final Node initialValue;
    private Node lastValue;
    private boolean isAssignable;
    private String reasonNotAssignable;

    /**
     * Initializes a define.
     */
    public DefineInfo(Node initialValue, Node initialValueParent) {
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
