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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.PolyfillFindingCallback.PolyfillUsage;
import com.google.javascript.jscomp.PolyfillFindingCallback.Polyfills;
import com.google.javascript.jscomp.diagnostic.LogFile;
import com.google.javascript.jscomp.resources.ResourceLoader;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Garbage collection for variable and function definitions. Basically performs
 * a mark-and-sweep type algorithm over the JavaScript parse tree.
 *
 * For each scope:
 * (1) Scan the variable/function declarations at that scope.
 * (2) Traverse the scope for references, marking all referenced variables.
 *     Unlike other compiler passes, this is a pre-order traversal, not a
 *     post-order traversal.
 * (3) If the traversal encounters an assign without other side-effects,
 *     create a continuation. Continue the continuation iff the assigned
 *     variable is referenced.
 * (4) When the traversal completes, remove all unreferenced variables.
 *
 * If it makes it easier, you can think of the continuations of the traversal
 * as a reference graph. Each continuation represents a set of edges, where the
 * source node is a known variable, and the destination nodes are lazily
 * evaluated when the continuation is executed.
 *
 * This algorithm is similar to the algorithm used by {@code SmartNameRemoval}.
 * {@code SmartNameRemoval} maintains an explicit graph of dependencies
 * between global symbols. However, {@code SmartNameRemoval} cannot handle
 * non-trivial edges in the reference graph ("A is referenced iff both B and C
 * are referenced"), or local variables. {@code SmartNameRemoval} is also
 * substantially more complicated because it tries to handle namespaces
 * (which is largely unnecessary in the presence of {@code CollapseProperties}.
 *
 * This pass also uses a more complex analysis of assignments, where
 * an assignment to a variable or a property of that variable does not
 * necessarily count as a reference to that variable, unless we can prove
 * that it modifies external state. This is similar to
 * {@code FlowSensitiveInlineVariables}, except that it works for variables
 * used across scopes.
 *
 * Multiple datastructures are used to accumulate nodes, some of which are
 * later removed. Since some nodes encompass a subtree of nodes, the removal
 * can sometimes pre-remove other nodes which are also referenced in these
 * datastructures for later removal. Attempting double-removal violates scope
 * change notification constraints so there is a desire to excise
 * already-removed subtree nodes from these datastructures. But not all of the
 * datastructures are conducive to flexible removal and the ones that are
 * conducive don't necessarily track all flavors of nodes. So instead of
 * updating datastructures on the fly a pre-check is performed to skip
 * already-removed nodes right before the moment an attempt to remove them
 * would otherwise be made.
 */
class RemoveUnusedCode implements CompilerPass {

  // Properties that are implicitly used as part of the JS language.
  private static final ImmutableSet<String> IMPLICITLY_USED_PROPERTIES =
      ImmutableSet.of("length", "toString", "valueOf", "constructor");

  private final AbstractCompiler compiler;
  private final AstAnalyzer astAnalyzer;

  private final CodingConvention codingConvention;

  private final boolean removeLocalVars;
  private final boolean removeGlobals;

  private final boolean preserveFunctionExpressionNames;

  /**
   * Used to hold continuations that need to be invoked.
   *
   * When we find a subtree of the AST that may not need to be traversed, we create a Continuation
   * for it. If we later discover that we do need to traverse it, we add it to this worklist
   * rather than traversing it immediately. If we invoked the traversal immediately, we could
   * end up modifying a data structure in the traversal as we're iterating over it.
   */
  private final Deque<Continuation> worklist = new ArrayDeque<>();

  private final Map<Var, VarInfo> varInfoMap = new IdentityHashMap<>();

  private final Set<String> pinnedPropertyNames = new HashSet<>(IMPLICITLY_USED_PROPERTIES);

  /** Stores Removable objects for each property name that is currently considered removable. */
  private final Multimap<String, Removable> removablesForPropertyNames = HashMultimap.create();

  /** Single value to use for all vars for which we cannot remove anything at all. */
  private final VarInfo canonicalUnremovableVarInfo;

  /**
   * Keep track of scopes that we've traversed.
   */
  private final List<Scope> allFunctionParamScopes = new ArrayList<>();

  /**
   * Stores the names of all "leaf" properties that are polyfilled, to avoid unnecessary qualified
   * name matching and searches for all the other properties. This includes global names such as
   * "Promise" and "Map", static methods on global names such as "Array.from" and "Math.fround", and
   * instance properties such as "String.prototype.repeat" and "Promise.prototype.finally".
   */
  private final Multimap<String, PolyfillInfo> polyfills = HashMultimap.create();

  private final Set<Node> guardedUsages = new HashSet<>();

  private final Polyfills polyfillsFromTable;

  private final SyntacticScopeCreator scopeCreator;

  private final boolean removeUnusedPrototypeProperties;
  private final boolean allowRemovalOfExternProperties;
  private final boolean removeUnusedThisProperties;
  private final boolean removeUnusedObjectDefinePropertiesDefinitions;
  private final boolean removeUnusedPolyfills;
  private final boolean assumeGettersArePure;

  // Allocated & cleaned up by process()
  private LogFile removalLog;

  RemoveUnusedCode(Builder builder) {
    this.compiler = builder.compiler;
    this.astAnalyzer = compiler.getAstAnalyzer();
    this.codingConvention = builder.compiler.getCodingConvention();
    this.scopeCreator = new SyntacticScopeCreator(builder.compiler);

    this.removeLocalVars = builder.removeLocalVars;
    this.removeGlobals = builder.removeGlobals;
    this.preserveFunctionExpressionNames = builder.preserveFunctionExpressionNames;
    this.removeUnusedPrototypeProperties = builder.removeUnusedPrototypeProperties;
    this.allowRemovalOfExternProperties = builder.allowRemovalOfExternProperties;
    this.removeUnusedThisProperties = builder.removeUnusedThisProperties;
    this.removeUnusedObjectDefinePropertiesDefinitions =
        builder.removeUnusedObjectDefinePropertiesDefinitions;
    this.removeUnusedPolyfills = builder.removeUnusedPolyfills;
    this.polyfillsFromTable =
        Polyfills.fromTable(
            ResourceLoader.loadTextResource(RemoveUnusedCode.class, "js/polyfills.txt"));
    this.assumeGettersArePure = builder.assumeGettersArePure;

    // All Vars that are completely unremovable will share this VarInfo instance.
    canonicalUnremovableVarInfo = new VarInfo();
    canonicalUnremovableVarInfo.setIsExplicitlyNotRemovable();
  }

  public static class Builder {
    private final AbstractCompiler compiler;

    private boolean removeLocalVars = false;
    private boolean removeGlobals = false;
    private boolean preserveFunctionExpressionNames = false;
    private boolean removeUnusedPrototypeProperties = false;
    private boolean allowRemovalOfExternProperties = false;
    private boolean removeUnusedThisProperties = false;
    private boolean removeUnusedObjectDefinePropertiesDefinitions = false;
    private boolean removeUnusedPolyfills = false;
    private boolean assumeGettersArePure = false;

    Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    Builder removeLocalVars(boolean value) {
      this.removeLocalVars = value;
      return this;
    }

    Builder removeGlobals(boolean value) {
      this.removeGlobals = value;
      return this;
    }

    Builder preserveFunctionExpressionNames(boolean value) {
      this.preserveFunctionExpressionNames = value;
      return this;
    }

    Builder removeUnusedPrototypeProperties(boolean value) {
      this.removeUnusedPrototypeProperties = value;
      return this;
    }

    Builder allowRemovalOfExternProperties(boolean value) {
      this.allowRemovalOfExternProperties = value;
      return this;
    }

    Builder removeUnusedThisProperties(boolean value) {
      this.removeUnusedThisProperties = value;
      return this;
    }

    Builder removeUnusedObjectDefinePropertiesDefinitions(boolean value) {
      this.removeUnusedObjectDefinePropertiesDefinitions = value;
      return this;
    }

    Builder removeUnusedPolyfills(boolean value) {
      this.removeUnusedPolyfills = value;
      return this;
    }

    Builder assumeGettersArePure(boolean value) {
      this.assumeGettersArePure = value;
      return this;
    }

    RemoveUnusedCode build() {
      return new RemoveUnusedCode(this);
    }
  }

  /** Supplies the string needed for an entry in the removal log. */
  private static class RemovalLogRecord implements Supplier<String> {
    private final String kind;
    private final Supplier<String> nameSupplier;
    private final Supplier<String> functionNameSupplier;

    /**
     * Returns a log entry string.
     *
     * <p>Each entry is one tab-separated line of the form:
     *
     * <pre>
     *   KIND NAME [FUNCTION_NAME]
     * </pre>
     *
     * <p>See specific methods below for details.
     */
    @Override
    public String get() {
      return String.join("\t", kind, nameSupplier.get(), functionNameSupplier.get());
    }

    RemovalLogRecord(
        String kind, Supplier<String> nameSupplier, Supplier<String> functionNameSupplier) {
      this.kind = checkNotNull(kind);
      this.nameSupplier = checkNotNull(nameSupplier);
      this.functionNameSupplier = checkNotNull(functionNameSupplier);
    }

    RemovalLogRecord(String kind, Supplier<String> nameSupplier) {
      // No function name
      this(kind, nameSupplier, () -> "");
    }

    static RemovalLogRecord forProperty(String propName) {
      return new RemovalLogRecord("prop", () -> propName);
    }

    static RemovalLogRecord forVar(Var var) {
      return new RemovalLogRecord("var", var::getName);
    }

    static RemovalLogRecord forPolyfill(PolyfillInfo polyfillInfo) {
      return new RemovalLogRecord("poly", polyfillInfo::getName);
    }

    /**
     * Records removal of a named function parameter.
     *
     * @param nameNode The parameter's NAME node
     * @param argList The function's PARAM_LIST node
     */
    static RemovalLogRecord forNamedArg(Node nameNode, Node argList) {
      return new RemovalLogRecord(
          "arg", nameNode::getString, getLoggableFunctionNameSupplier(argList));
    }

    /**
     * Records removal of a destructuring function parameter.
     *
     * @param argList The function's PARAM_LIST node
     */
    static RemovalLogRecord forDestructuringArg(Node argList) {
      return new RemovalLogRecord(
          "arg", () -> "<pattern>", getLoggableFunctionNameSupplier(argList));
    }

    /**
     * Records that a named parameter is marked as unused for possible removal by {@see
     * OptimizeParameters}.
     *
     * @param nameNode The parameter's NAME node
     * @param argList The function's PARAM_LIST node
     */
    static RemovalLogRecord forMarkingNamedArg(Node nameNode, Node argList) {
      return new RemovalLogRecord(
          "argmark", nameNode::getString, getLoggableFunctionNameSupplier(argList));
    }

    /**
     * Returns a supplier for the FUNCTION_NAME field of an argument removal log entry.
     *
     * <p>If no good name can be found, then {@code "<anonymous>"} will be supplied.
     *
     * @param argList The function's PARAM_LIST node
     */
    private static Supplier<String> getLoggableFunctionNameSupplier(Node argList) {
      return () -> {
        String functionName = NodeUtil.getNearestFunctionName(checkNotNull(argList).getParent());
        if (functionName == null) {
          functionName = "<anonymous>";
        }
        return functionName;
      };
    }
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    if (!allowRemovalOfExternProperties) {
      pinnedPropertyNames.addAll(compiler.getExternProperties());
    }

    try (LogFile logFile = compiler.createOrReopenIndexedLog(this.getClass(), "removals.log")) {
      removalLog = logFile; // avoid passing the log file through a bunch of methods
      traverseAndRemoveUnusedReferences(root);
    } finally {
      removalLog = null;
    }
  }

  /** Traverses a node recursively. Call this once per pass. */
  private void traverseAndRemoveUnusedReferences(Node root) {
    // Create scope from parent of root node, which also has externs as a child, so we'll
    // have extern definitions in scope.
    Scope scope = scopeCreator.createScope(root.getParent(), null);
    if (!scope.hasSlot(NodeUtil.JSC_PROPERTY_NAME_FN)) {
      // TODO(b/70730762): Passes that add references to this should ensure it is declared.
      // NOTE: null input makes this an extern var.
      scope.declare(
          NodeUtil.JSC_PROPERTY_NAME_FN, /* no declaration node */ null, /* no input */ null);
    }

    // Accumulate guarded usages of polyfills before removal starts.
    new PolyfillFindingCallback(compiler, polyfillsFromTable)
        .traverseOnlyGuarded(root, this::storePolyfill);

    worklist.add(new Continuation(root, scope));
    while (!worklist.isEmpty()) {
      Continuation continuation = worklist.remove();
      continuation.apply();
    }

    removeUnreferencedVarsAndPolyfills();
    removeIndependentlyRemovableProperties();
    for (Scope fparamScope : allFunctionParamScopes) {
      removeUnreferencedFunctionArgs(fparamScope);
    }
  }

  private void storePolyfill(PolyfillUsage polyfillUsage) {
    this.guardedUsages.add(polyfillUsage.node());
  }

  private void removeIndependentlyRemovableProperties() {
    for (String propName : removablesForPropertyNames.keys()) {
      removalLog.log(RemovalLogRecord.forProperty(propName));
      for (Removable removable : removablesForPropertyNames.get(propName)) {
        removable.remove(compiler);
      }
    }
  }

  /**
   * Traverses everything in the current scope and marks variables that
   * are referenced.
   *
   * During traversal, we identify subtrees that will only be
   * referenced if their enclosing variables are referenced. Instead of
   * traversing those subtrees, we create a continuation for them,
   * and traverse them lazily.
   */
  private void traverseNode(Node n, Scope scope) {
    Node parent = n.getParent();
    Token type = n.getToken();
    switch (type) {
      case CATCH:
        traverseCatch(n, scope);
        break;

      case FUNCTION:
        {
          VarInfo varInfo = null;
          // If this function is a removable var, then create a continuation
          // for it instead of traversing immediately.
          if (NodeUtil.isFunctionDeclaration(n)) {
            varInfo = traverseNameNode(n.getFirstChild(), scope);
            FunctionDeclaration functionDeclaration =
                new RemovableBuilder()
                    .addContinuation(new Continuation(n, scope))
                    .buildFunctionDeclaration(n);
            varInfo.addRemovable(functionDeclaration);
            if (parent.isExport()) {
              varInfo.markAsReferenced();
            }
          } else {
            traverseFunction(n, scope);
          }
        }
        break;

      case ASSIGN:
        traverseAssign(n, scope);
        break;

      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
        traverseCompoundAssign(n, scope);
        break;

      case INC:
      case DEC:
        traverseIncrementOrDecrementOp(n, scope);
        break;

      case CALL:
        traverseCall(n, scope);
        break;

      case SWITCH:
      case BLOCK:
        // This case if for if there are let and const variables in block scopes.
        // Otherwise other variables will be hoisted up into the global scope and already be
        // handled.
        traverseChildren(
            n, NodeUtil.createsBlockScope(n) ? scopeCreator.createScope(n, scope) : scope);
        break;

      case MODULE_BODY:
        traverseChildren(n, scopeCreator.createScope(n, scope));
        break;

      case CLASS:
        traverseClass(n, scope);
        break;

      case CLASS_MEMBERS:
        traverseClassMembers(n, scope);
        break;

      case ARRAY_PATTERN:
      case PARAM_LIST:
        traverseIndirectAssignmentList(n, scope);
        break;

      case OBJECT_PATTERN:
        traverseObjectPattern(n, scope);
        break;

      case OBJECTLIT:
        traverseObjectLiteral(n, scope);
        break;

      case FOR:
        traverseVanillaFor(n, scope);
        break;

      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
        traverseEnhancedFor(n, scope);
        break;

      case LET:
      case CONST:
      case VAR:
        // for-loop cases are handled by custom traversal methods.
        checkState(NodeUtil.isStatement(n));
        traverseDeclarationStatement(n, scope);
        break;

      case INSTANCEOF:
        traverseInstanceof(n, scope);
        break;

      case NAME:
        // The only cases that should reach this point are parameter declarations and references
        // to names. The name node does not have children in these cases.
        checkState(!n.hasChildren());
        // the parameter declaration is not a read of the name
        if (!parent.isParamList()) {
          // var|let|const name;
          // are handled at a higher level.
          checkState(!NodeUtil.isNameDeclaration(parent));
          // function name() {}
          // class name() {}
          // handled at a higher level
          checkState(!((parent.isFunction() || parent.isClass()) && parent.getFirstChild() == n));
          traverseNameNode(n, scope).markAsReferenced();
        }
        break;

      case GETPROP:
        traverseGetProp(n, scope);
        break;

      default:
        traverseChildren(n, scope);
        break;
    }
  }

  private void traverseInstanceof(Node instanceofNode, Scope scope) {
    checkArgument(instanceofNode.isInstanceOf(), instanceofNode);
    Node lhs = instanceofNode.getFirstChild();
    Node rhs = lhs.getNext();
    traverseNode(lhs, scope);
    if (rhs.isName()) {
      VarInfo varInfo = traverseNameNode(rhs, scope);
      RemovableBuilder builder = new RemovableBuilder();
      varInfo.addRemovable(builder.buildInstanceofName(instanceofNode));
    } else {
      traverseNode(rhs, scope);
    }
  }

  private void traverseGetProp(Node getProp, Scope scope) {
    Node objectNode = getProp.getFirstChild();
    Node propertyNameNode = objectNode.getNext();
    String propertyName = propertyNameNode.getString();

    if (polyfills.containsKey(propertyName)) {
      for (PolyfillInfo info : polyfills.get(propertyName)) {
        info.considerPossibleReference(getProp);
      }
    }

    if (NodeUtil.isExpressionResultUsed(getProp)
        || considerForAccessorSideEffects(getProp, PropertyAccessKind.GETTER_ONLY)) {
      // must record as reference to the property and continue traversal.
      markPropertyNameAsPinned(propertyName);
      traverseNode(objectNode, scope);
    } else if (objectNode.isThis()) {
      // this.propName;
      RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
      considerForIndependentRemoval(builder.buildUnusedReadReference(getProp, propertyNameNode));
    } else if (isDotPrototype(objectNode)) {
      // (objExpression).prototype.propName;
      RemovableBuilder builder = new RemovableBuilder().setIsPrototypeDotPropertyReference(true);
      Node objExpression = objectNode.getFirstChild();
      if (objExpression.isName()) {
        // name.prototype.propName;
        VarInfo varInfo = traverseNameNode(objExpression, scope);
        varInfo.addRemovable(builder.buildUnusedReadReference(getProp, propertyNameNode));
      } else {
        // (objExpression).prototype.propName;
        if (astAnalyzer.mayHaveSideEffects(objExpression)) {
          traverseNode(objExpression, scope);
        } else {
          builder.addContinuation(new Continuation(objExpression, scope));
        }
        considerForIndependentRemoval(builder.buildUnusedReadReference(getProp, propertyNameNode));
      }
    } else {
      // TODO(bradfordcsmith): add removal of `varName.propName;`
      markPropertyNameAsPinned(propertyName);
      traverseNode(objectNode, scope);
    }
  }

  // TODO(b/137380742): Combine with `traverseCompoundAssign`.
  private void traverseIncrementOrDecrementOp(Node incOrDecOp, Scope scope) {
    checkArgument(incOrDecOp.isInc() || incOrDecOp.isDec(), incOrDecOp);
    Node arg = incOrDecOp.getOnlyChild();
    if (NodeUtil.isExpressionResultUsed(incOrDecOp)) {
      // If expression result is used, then this expression is definitely not removable.
      traverseNode(arg, scope);
    } else if (arg.isGetProp()) {
      Node getPropObj = arg.getFirstChild();
      Node propertyNameNode = arg.getLastChild();

      if (considerForAccessorSideEffects(arg, PropertyAccessKind.GETTER_AND_SETTER)) {
        traverseNode(getPropObj, scope); // Don't re-traverse the GETPROP as a read.
      } else if (getPropObj.isThis()) {
        // this.propName++
        RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
        considerForIndependentRemoval(builder.buildIncOrDepOp(incOrDecOp, propertyNameNode, null));
      } else if (isDotPrototype(getPropObj)) {
        // someExpression.prototype.propName++
        Node exprObj = getPropObj.getFirstChild();
        RemovableBuilder builder = new RemovableBuilder().setIsPrototypeDotPropertyReference(true);
        if (exprObj.isName()) {
          // varName.prototype.propName++
          VarInfo varInfo = traverseNameNode(exprObj, scope);
          varInfo.addRemovable(builder.buildIncOrDepOp(incOrDecOp, propertyNameNode, null));
        } else {
          // (someExpression).prototype.propName++
          Node toPreserve = null;
          if (astAnalyzer.mayHaveSideEffects(exprObj)) {
            toPreserve = exprObj;
            traverseNode(exprObj, scope);
          } else {
            builder.addContinuation(new Continuation(exprObj, scope));
          }
          considerForIndependentRemoval(
              builder.buildIncOrDepOp(incOrDecOp, propertyNameNode, toPreserve));
        }
      } else {
        // someExpression.propName++ is not removable except in the cases covered above
        traverseNode(arg, scope);
      }
    } else {
      // TODO(bradfordcsmith): varName++ should be removable if varName is otherwise unused
      traverseNode(arg, scope);
    }
  }

  // TODO(b/137380742): Combine with `traverseIncrementOrDecrement`.
  private void traverseCompoundAssign(Node compoundAssignNode, Scope scope) {
    // We'll allow removal of compound assignment to a `this` property as long as the result of the
    // assignment is unused.
    // e.g. `this.x += 3;`
    // TODO(nickreid): Why do we treat `this` properties specially? It it because `this` is const?
    Node targetNode = compoundAssignNode.getFirstChild();
    Node valueNode = compoundAssignNode.getLastChild();
    if (targetNode.isGetProp()) {
      if (considerForAccessorSideEffects(targetNode, PropertyAccessKind.GETTER_AND_SETTER)) {
        traverseNode(targetNode.getFirstChild(), scope); // Don't re-traverse the GETPROP as a read.
        traverseNode(valueNode, scope);
      } else if (targetNode.getFirstChild().isThis()
          && !NodeUtil.isExpressionResultUsed(compoundAssignNode)) {
      RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
      traverseRemovableAssignValue(valueNode, builder, scope);
      considerForIndependentRemoval(
          builder.buildNamedPropertyAssign(compoundAssignNode, targetNode.getLastChild()));
      } else {
        traverseNode(targetNode, scope);
        traverseNode(valueNode, scope);
      }
    } else {
      traverseNode(targetNode, scope);
      traverseNode(valueNode, scope);
    }
  }

  private VarInfo traverseNameNode(Node n, Scope scope) {
    if (polyfills.containsKey(n.getString())) {
      for (PolyfillInfo info : polyfills.get(n.getString())) {
        info.considerPossibleReference(n);
      }
    }

    return traverseVar(getVarForNameNode(n, scope));
  }

  private void traverseCall(Node callNode, Scope scope) {
    Node callee = callNode.getFirstChild();

    if (callee.isQualifiedName()
        && codingConvention.isPropertyRenameFunction(callee.getOriginalQualifiedName())) {
      Node propertyNameNode = callee.getNext();
      if (propertyNameNode != null && propertyNameNode.isString()) {
        markPropertyNameAsPinned(propertyNameNode.getString());
      }
      traverseChildren(callNode, scope);
    } else if (NodeUtil.isObjectDefinePropertiesDefinition(callNode)) {
      // TODO(bradfordcsmith): Should also handle Object.create() and Object.defineProperty().
      traverseObjectDefinePropertiesCall(callNode, scope);
    } else if (removeUnusedPolyfills && isJscompPolyfill(callee)) {
      Node firstArg = callee.getNext();
      String polyfillName = firstArg.getString();
      PolyfillInfo info = createPolyfillInfo(callNode, scope, polyfillName);
      polyfills.put(info.key, info);
      // Only traverse the callee (to mark it as used).  The arguments may be traversed later.
      traverseNode(callNode.getFirstChild(), scope);
    } else {
      Node parent = callNode.getParent();
      String classVarName = null;

      // A call that is a statement unto itself or the left side of a comma expression might be
      // a call to a known method for doing class setup
      // e.g. $jscomp.inherits(Class, BaseClass) or goog.addSingletonGetter(Class)
      // Such methods never have meaningful return values, so we won't look for them in other
      // contexts
      if (parent.isExprResult() || (parent.isComma() && parent.getFirstChild() == callNode)) {
        SubclassRelationship subclassRelationship =
            codingConvention.getClassesDefinedByCall(callNode);
        if (subclassRelationship != null) {
          // e.g. goog.inherits(DerivedClass, BaseClass);
          // NOTE: DerivedClass and BaseClass must be QNames. Otherwise getClassesDefinedByCall()
          // will return null.
          classVarName = subclassRelationship.subclassName;
        } else {
          // Look for calls to addSingletonGetter calls.
          classVarName = codingConvention.getSingletonGetterClassName(callNode);
        }
      }

      Var classVar = null;
      if (classVarName != null && NodeUtil.isValidSimpleName(classVarName)) {
        classVar = checkNotNull(scope.getVar(classVarName), classVarName);
      }

      if (classVar == null || !classVar.isGlobal()) {
        // The call we are traversing does not modify a class definition,
        // or the class is not specified with a simple variable name,
        // or the variable name is not global.
        // TODO(bradfordcsmith): It would be more correct to check whether the class name
        // references a known constructor and expand to allow QNames.
        traverseChildren(callNode, scope);
      } else {
        RemovableBuilder builder = new RemovableBuilder();
        for (Node child = callNode.getFirstChild(); child != null; child = child.getNext()) {
          builder.addContinuation(new Continuation(child, scope));
        }
        traverseVar(classVar).addRemovable(builder.buildClassSetupCall(callNode));
      }
    }
  }

  /** Checks whether this is a recognizable call to $jscomp.polyfill. */
  private static boolean isJscompPolyfill(Node n) {
    switch (n.getToken()) {
      case NAME:
        // Need to work correctly after CollapseProperties.
        return n.getString().equals("$jscomp$polyfill") && n.getNext().isString();
      case GETPROP:
        // Need to work correctly without CollapseProperties.
        return n.getLastChild().getString().equals("polyfill")
            && n.getFirstChild().isName()
            && n.getFirstChild().getString().equals("$jscomp")
            && n.getNext().isString();
      default:
        return false;
    }
  }

  /** Traverse `Object.defineProperties(someObject, propertyDefinitions);`. */
  private void traverseObjectDefinePropertiesCall(Node callNode, Scope scope) {
    // First child is Object.defineProperties or some equivalent of it.
    Node callee = callNode.getFirstChild();
    Node targetObject = callNode.getSecondChild();
    Node propertyDefinitions = targetObject.getNext();

    if ((targetObject.isName() || isNameDotPrototype(targetObject))
        && !NodeUtil.isExpressionResultUsed(callNode)) {
      // NOTE: Object.defineProperties() returns its first argument, so if its return value is used
      // that counts as a use of the targetObject.
      Node nameNode = targetObject.isName() ? targetObject : targetObject.getFirstChild();
      VarInfo varInfo = traverseNameNode(nameNode, scope);
      RemovableBuilder builder = new RemovableBuilder();
      // TODO(bradfordcsmith): Is it really necessary to traverse the callee
      // (aka. Object.defineProperties)?
      builder.addContinuation(new Continuation(callee, scope));
      if (astAnalyzer.mayHaveSideEffects(propertyDefinitions)) {
        traverseNode(propertyDefinitions, scope);
      } else {
        builder.addContinuation(new Continuation(propertyDefinitions, scope));
      }
      varInfo.addRemovable(builder.buildClassSetupCall(callNode));
    } else {
      // TODO(bradfordcsmith): Is it really necessary to traverse the callee
      // (aka. Object.defineProperties)?
      traverseNode(callee, scope);
      traverseNode(targetObject, scope);
      traverseNode(propertyDefinitions, scope);
    }
  }

  /** Traverse the object literal passed as the second argument to `Object.defineProperties()`. */
  private void traverseObjectDefinePropertiesLiteral(Node propertyDefinitions, Scope scope) {
    for (Node property = propertyDefinitions.getFirstChild();
        property != null;
        property = property.getNext()) {
      if (property.isQuotedString()) {
        // Quoted property name counts as a reference to the property and protects it from removal.
        markPropertyNameAsPinned(property.getString());
        traverseNode(property.getOnlyChild(), scope);
      } else if (property.isStringKey()) {
        Node definition = property.getOnlyChild();
        if (astAnalyzer.mayHaveSideEffects(definition)) {
          traverseNode(definition, scope);
        } else {
          considerForIndependentRemoval(
              new RemovableBuilder()
                  .addContinuation(new Continuation(definition, scope))
                  .buildObjectDefinePropertiesDefinition(property));
        }
      } else {
        // TODO(bradfordcsmith): Maybe report error for anything other than a computed property,
        // since getters, setters, and methods don't make much sense in this context.
        traverseNode(property, scope);
      }
    }
  }

  private Var getVarForNameNode(Node nameNode, Scope scope) {
    return checkNotNull(scope.getVar(nameNode.getString()), nameNode);
  }

  private void traverseObjectLiteral(Node objectLiteral, Scope scope) {
    checkArgument(objectLiteral.isObjectLit(), objectLiteral);
    // Is this an object literal that is assigned directly to a 'prototype' property?
    if (isAssignmentToPrototype(objectLiteral.getParent())) {
      traversePrototypeLiteral(objectLiteral, scope);
    } else if (isObjectDefinePropertiesSecondArgument(objectLiteral)) {
      // TODO(bradfordcsmith): Consider restricting special handling of the properties literal to
      // cases where the target object is a known class, prototype, or this.
      traverseObjectDefinePropertiesLiteral(objectLiteral, scope);
    } else {
      traverseNonPrototypeObjectLiteral(objectLiteral, scope);
    }
  }

  private boolean isObjectDefinePropertiesSecondArgument(Node n) {
    Node parent = n.getParent();
    return NodeUtil.isObjectDefinePropertiesDefinition(parent) && parent.getLastChild() == n;
  }

  private void traverseNonPrototypeObjectLiteral(Node objectLiteral, Scope scope) {
    for (Node propertyNode = objectLiteral.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      if (propertyNode.isStringKey()) {
        // A property name in an object literal counts as a reference,
        // because of some reflection patterns.
        // Note that we are intentionally treating both quoted and unquoted keys as
        // references.
        markPropertyNameAsPinned(propertyNode.getString());
        traverseNode(propertyNode.getFirstChild(), scope);
      } else {
        traverseNode(propertyNode, scope);
      }
    }
  }

  private void traversePrototypeLiteral(Node objectLiteral, Scope scope) {
    for (Node propertyNode = objectLiteral.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      if (propertyNode.isComputedProp() || propertyNode.isQuotedString()) {
        traverseChildren(propertyNode, scope);
      } else {
        Node valueNode = propertyNode.getOnlyChild();
        if (astAnalyzer.mayHaveSideEffects(valueNode)) {
          // TODO(bradfordcsmith): Ideally we should preserve the side-effect without keeping the
          // property itself alive.
          traverseNode(valueNode, scope);
        } else {
          // If we've come this far, we already know we're keeping the prototype literal itself,
          // but we may be able to remove unreferenced properties in it.
          considerForIndependentRemoval(
              new RemovableBuilder()
                  .addContinuation(new Continuation(valueNode, scope))
                  .buildClassOrPrototypeNamedProperty(propertyNode));
        }
      }
    }
  }

  private boolean isAssignmentToPrototype(Node n) {
    return n.isAssign() && isDotPrototype(n.getFirstChild());
  }

  /** True for `someExpression.prototype`. */
  private static boolean isDotPrototype(Node n) {
    return n.isGetProp() && n.getLastChild().getString().equals("prototype");
  }

  private void traverseCatch(Node catchNode, Scope scope) {
    Node exceptionNameNode = catchNode.getFirstChild();
    Node block = exceptionNameNode.getNext();
    if (exceptionNameNode.isName()) {
      // exceptionNameNode can be an empty node if not using a binding in 2019.
      VarInfo exceptionVarInfo = traverseNameNode(exceptionNameNode, scope);
      exceptionVarInfo.setIsExplicitlyNotRemovable();
    }
    traverseNode(block, scope);
  }

  private void traverseEnhancedFor(Node enhancedFor, Scope scope) {
    Scope forScope = scopeCreator.createScope(enhancedFor, scope);
    // for (iterationTarget in|of collection) body;
    Node iterationTarget = enhancedFor.getFirstChild();
    Node collection = iterationTarget.getNext();
    Node body = collection.getNext();
    if (iterationTarget.isName()) {
      // using previously-declared loop variable. e.g.
      // `for (varName of collection) {}`
      VarInfo varInfo = traverseNameNode(iterationTarget, forScope);
      varInfo.setIsExplicitlyNotRemovable();
    } else if (NodeUtil.isNameDeclaration(iterationTarget)) {
      // loop has const/var/let declaration
      Node declNode = iterationTarget.getOnlyChild();
      if (declNode.isDestructuringLhs()) {
        // e.g.
        // `for (const [a, b] of pairList) {}`
        // destructuring is handled at a lower level
        // Note that destructuring assignments are always considered to set an unknown value
        // equivalent to what we set for the var name case above and below.
        // It isn't necessary to set the variable names as not removable, though, because the
        // thing that isn't removable is the destructuring pattern itself, which we never remove.
        // TODO(bradfordcsmith): The need to explain all the above shows this should be reworked.
        traverseNode(declNode, forScope);
      } else {
        // e.g.
        // `for (const varName of collection) {}`
        checkState(declNode.isName());
        checkState(!declNode.hasChildren());
        // We can never remove the loop variable of a for-in or for-of loop, because it's
        // essential to loop syntax.
        VarInfo varInfo = traverseNameNode(declNode, forScope);
        varInfo.setIsExplicitlyNotRemovable();
      }
    } else {
      // using some general LHS value e.g.
      // `for ([a, b] of collection) {}` destructuring with existing vars
      // `for (a.x of collection) {}` using a property as the loop var
      // TODO(bradfordcsmith): This should be considered a write if it's a property reference.
      traverseNode(iterationTarget, forScope);
    }
    traverseNode(collection, forScope);
    traverseNode(body, forScope);
  }

  private void traverseVanillaFor(Node forNode, Scope scope) {
    Scope forScope = scopeCreator.createScope(forNode, scope);
    Node initialization = forNode.getFirstChild();
    Node condition = initialization.getNext();
    Node update = condition.getNext();
    Node block = update.getNext();
    if (NodeUtil.isNameDeclaration(initialization)) {
      traverseVanillaForNameDeclarations(initialization, forScope);
    } else {
      traverseNode(initialization, forScope);
    }
    traverseNode(condition, forScope);
    traverseNode(update, forScope);
    traverseNode(block, forScope);
  }

  private void traverseVanillaForNameDeclarations(Node nameDeclaration, Scope scope) {
    for (Node child = nameDeclaration.getFirstChild(); child != null; child = child.getNext()) {
      if (!child.isName()) {
        // TODO(bradfordcsmith): Customize handling of destructuring
        traverseNode(child, scope);
      } else {
        Node nameNode = child;
        @Nullable Node valueNode = child.getFirstChild();
        VarInfo varInfo = traverseNameNode(nameNode, scope);
        if (valueNode == null) {
          varInfo.addRemovable(new RemovableBuilder().buildVanillaForNameDeclaration(nameNode));
        } else if (astAnalyzer.mayHaveSideEffects(valueNode)) {
          // TODO(bradfordcsmith): Actually allow for removing the variable while keeping the
          // valueNode for its side-effects.
          varInfo.setIsExplicitlyNotRemovable();
          traverseNode(valueNode, scope);
        } else {
          VanillaForNameDeclaration vanillaForNameDeclaration =
              new RemovableBuilder()
                  .addContinuation(new Continuation(valueNode, scope))
                  .buildVanillaForNameDeclaration(nameNode);
          varInfo.addRemovable(vanillaForNameDeclaration);
        }
      }
    }
  }

  private void traverseDeclarationStatement(Node declarationStatement, Scope scope) {
    // Normalization should ensure that declaration statements always have just one child.
    Node nameNode = declarationStatement.getOnlyChild();
    if (!nameNode.isName()) {
      // Destructuring declarations are handled elsewhere.
      traverseNode(nameNode, scope);
    } else {
      Node valueNode = nameNode.getFirstChild();
      VarInfo varInfo = traverseNameNode(nameNode, scope);
      RemovableBuilder builder = new RemovableBuilder();
      if (valueNode == null) {
        varInfo.addRemovable(builder.buildNameDeclarationStatement(declarationStatement));
      } else {
        if (astAnalyzer.mayHaveSideEffects(valueNode)) {
          traverseNode(valueNode, scope);
        } else {
          builder.addContinuation(new Continuation(valueNode, scope));
        }
        NameDeclarationStatement removable =
            builder.buildNameDeclarationStatement(declarationStatement);
        varInfo.addRemovable(removable);
      }
    }
  }

  private void traverseAssign(Node assignNode, Scope scope) {
    checkState(NodeUtil.isAssignmentOp(assignNode));

    Node lhs = assignNode.getFirstChild();
    Node valueNode = assignNode.getLastChild();
    if (lhs.isName()) {
      // varName = something
      VarInfo varInfo = traverseNameNode(lhs, scope);
      RemovableBuilder builder = new RemovableBuilder();
      traverseRemovableAssignValue(valueNode, builder, scope);
      varInfo.addRemovable(builder.buildVariableAssign(assignNode));
    } else if (lhs.isGetElem()) {
      Node getElemObj = lhs.getFirstChild();
      Node getElemKey = lhs.getLastChild();
      Node varNameNode =
          getElemObj.isName()
              ? getElemObj
              : isNameDotPrototype(getElemObj) ? getElemObj.getFirstChild() : null;

      if (varNameNode != null) {
        // varName[someExpression] = someValue
        // OR
        // varName.prototype[someExpression] = someValue
        VarInfo varInfo = traverseNameNode(varNameNode, scope);
        RemovableBuilder builder = new RemovableBuilder();
        if (astAnalyzer.mayHaveSideEffects(getElemKey)) {
          traverseNode(getElemKey, scope);
        } else {
          builder.addContinuation(new Continuation(getElemKey, scope));
        }
        traverseRemovableAssignValue(valueNode, builder, scope);
        varInfo.addRemovable(builder.buildComputedPropertyAssign(assignNode, getElemKey));
      } else {
        traverseNode(getElemObj, scope);
        traverseNode(getElemKey, scope);
        traverseNode(valueNode, scope);
      }
    } else if (lhs.isGetProp()) {
      Node getPropLhs = lhs.getFirstChild();
      Node propNameNode = lhs.getLastChild();

      if (considerForAccessorSideEffects(lhs, PropertyAccessKind.SETTER_ONLY)) {
        // And the possible side-effects mean we can't do any removal. We don't use the
        // `AstAnalyzer` because we only want to consider side-effect from the assignment, not the
        // entire l-value subtree.
        traverseNode(getPropLhs, scope); // Don't re-traverse the GETPROP as a read.
        traverseNode(valueNode, scope);
      } else if (getPropLhs.isName()) {
        // varName.propertyName = someValue
        VarInfo varInfo = traverseNameNode(getPropLhs, scope);
        RemovableBuilder builder = new RemovableBuilder();
        traverseRemovableAssignValue(valueNode, builder, scope);
        varInfo.addRemovable(builder.buildNamedPropertyAssign(assignNode, propNameNode));
      } else if (isDotPrototype(getPropLhs)) {
        // objExpression.prototype.propertyName = someValue
        Node objExpression = getPropLhs.getFirstChild();
        RemovableBuilder builder = new RemovableBuilder().setIsPrototypeDotPropertyReference(true);
        traverseRemovableAssignValue(valueNode, builder, scope);
        if (objExpression.isName()) {
          // varName.prototype.propertyName = someValue
          VarInfo varInfo = traverseNameNode(getPropLhs.getFirstChild(), scope);
          varInfo.addRemovable(builder.buildNamedPropertyAssign(assignNode, propNameNode));
        } else {
          // (someExpression).prototype.propertyName = someValue
          if (astAnalyzer.mayHaveSideEffects(objExpression)) {
            traverseNode(objExpression, scope);
          } else {
            builder.addContinuation(new Continuation(objExpression, scope));
          }
          considerForIndependentRemoval(
              builder.buildAnonymousPrototypeNamedPropertyAssign(
                  assignNode, propNameNode.getString()));
        }
      } else if (getPropLhs.isThis()) {
        // this.propertyName = someValue
        RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
        traverseRemovableAssignValue(valueNode, builder, scope);
        considerForIndependentRemoval(builder.buildNamedPropertyAssign(assignNode, propNameNode));
      } else {
        traverseNode(lhs, scope);
        traverseNode(valueNode, scope);
      }
    } else {
      // no other cases are removable
      traverseNode(lhs, scope);
      traverseNode(valueNode, scope);
    }
  }

  private void traverseRemovableAssignValue(Node valueNode, RemovableBuilder builder, Scope scope) {
    if (astAnalyzer.mayHaveSideEffects(valueNode)
        || NodeUtil.isExpressionResultUsed(valueNode.getParent())) {
      traverseNode(valueNode, scope);
    } else {
      builder.addContinuation(new Continuation(valueNode, scope));
    }
  }

  private boolean isNameDotPrototype(Node n) {
    return n.isGetProp()
        && n.getFirstChild().isName()
        && n.getLastChild().getString().equals("prototype");
  }

  private void traverseObjectPattern(Node pattern, Scope scope) {
    checkState(pattern.isObjectPattern(), pattern);

    for (Node elem = pattern.getFirstChild(); elem != null; elem = elem.getNext()) {
      switch (elem.getToken()) {
        case COMPUTED_PROP:
          traverseIndirectAssignment(elem, elem.getSecondChild(), scope);
          break;

        case STRING_KEY:
          if (!elem.isQuotedString()) {
            markPropertyNameAsPinned(elem.getString());
          }
          traverseIndirectAssignment(elem, elem.getOnlyChild(), scope);
          break;

        case ITER_REST:
        case OBJECT_REST:
          // Recall that the rest target can be any l-value expression
          traverseIndirectAssignment(elem, elem.getOnlyChild(), scope);
          break;

        default:
          throw new IllegalStateException(
              "Unexpected child of " + pattern.getToken() + ": " + elem.toStringTree());
      }
    }
  }

  private void traverseIndirectAssignmentList(Node list, Scope scope) {
    checkState(list.isArrayPattern() || list.isParamList(), list);

    for (Node elem = list.getFirstChild(); elem != null; elem = elem.getNext()) {
      switch (elem.getToken()) {
        case EMPTY:
          break;

        case ARRAY_PATTERN:
        case DEFAULT_VALUE:
        case GETELEM:
        case GETPROP:
        case NAME:
        case OBJECT_PATTERN:
          traverseIndirectAssignment(elem, elem, scope);
          break;

        case ITER_REST:
        case OBJECT_REST:
          traverseIndirectAssignment(elem, elem.getOnlyChild(), scope);
          break;

        default:
          throw new IllegalStateException(
              "Unexpected child of " + list.getToken() + ": " + elem.toStringTree());
      }
    }
  }

  /**
   * Traverse an AST structure representing an assignment operation for which the target and value
   * are far apart.
   *
   * <p>Examples include destructurings and function parameters.
   *
   * @param root The root of the assignment subtree.
   * @param target The l-value expression being assigned to.
   */
  private void traverseIndirectAssignment(Node root, Node target, Scope scope) {
    Node rootParent = root.getParent();
    checkArgument(rootParent.isDestructuringPattern() || rootParent.isParamList(), rootParent);

    // Flatten out the case where the target is a default value. We always have to consider it.
    if (target.isDefaultValue()) {
      target = target.getFirstChild();
    }

    if (target.isGetProp()) {
      considerForAccessorSideEffects(target, PropertyAccessKind.SETTER_ONLY);
    }

    RemovableBuilder builder =
        new RemovableBuilder().addContinuation(new Continuation(root, scope));

    if (astAnalyzer.mayHaveSideEffects(root)) {
      // If anywhere in the assignment subtree has side-effects, it means that even if the target is
      // removable the subtree is not.
      traverseNode(root, scope);
      // TODO(bradfordcsmith): Preserve side effects without preventing removal of variables and
      // properties. We could probably do this by subbing in an empty object pattern.
    } else if (target.isName()) {
      VarInfo varInfo = traverseNameNode(target, scope);
      varInfo.addRemovable(builder.buildIndirectAssign(root, target));
    } else if (isNameDotPrototype(target) || isThisDotProperty(target)) {
      considerForIndependentRemoval(builder.buildIndirectAssign(root, target));
    } else {
      // TODO(bradfordcsmith): Handle property assignments also
      // e.g. `({a: foo.bar, b: foo.baz}) = {a: 1, b: 2}`
      traverseNode(root, scope);
    }
  }

  private void traverseChildren(Node n, Scope scope) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, scope);
    }
  }

  /**
   * Handle a class that is not the RHS child of an assignment or a variable declaration
   * initializer.
   *
   * <p>For
   * @param classNode
   * @param scope
   */
  private void traverseClass(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    if (NodeUtil.isClassDeclaration(classNode)) {
      traverseClassDeclaration(classNode, scope);
    } else {
      traverseClassExpression(classNode, scope);
    }
  }

  private void traverseClassDeclaration(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    Node classNameNode = classNode.getFirstChild();
    Node baseClassExpression = classNameNode.getNext();
    Node classBodyNode = baseClassExpression.getNext();
    Scope classScope = scopeCreator.createScope(classNode, scope);

    VarInfo varInfo = traverseNameNode(classNameNode, scope);
    if (classNode.getParent().isExport()) {
      // Cannot remove an exported class.
      varInfo.setIsExplicitlyNotRemovable();
      traverseNode(baseClassExpression, scope);
      // Use traverseChildren() here, because we should not consider any properties on the exported
      // class to be removable.
      traverseChildren(classBodyNode, classScope);
    } else if (astAnalyzer.mayHaveSideEffects(baseClassExpression)) {
      // TODO(bradfordcsmith): implement removal without losing side-effects for this case
      varInfo.setIsExplicitlyNotRemovable();
      traverseNode(baseClassExpression, scope);
      traverseClassMembers(classBodyNode, classScope);
    } else {
      RemovableBuilder builder =
          new RemovableBuilder()
              .addContinuation(new Continuation(baseClassExpression, classScope))
              .addContinuation(new Continuation(classBodyNode, classScope));
      varInfo.addRemovable(builder.buildClassDeclaration(classNode));
    }
  }

  private void traverseClassExpression(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    Node classNameNode = classNode.getFirstChild();
    Node baseClassExpression = classNameNode.getNext();
    Node classBodyNode = baseClassExpression.getNext();
    Scope classScope = scopeCreator.createScope(classNode, scope);

    if (classNameNode.isName()) {
      // We may be able to remove the name node if nothing ends up referring to it.
      VarInfo varInfo = traverseNameNode(classNameNode, classScope);
      // The class is non-local, because it is accessible by unknown code outside
      // of the scope where InnerName is defined.
      // e.g. `use(class InnerName {})`
      varInfo.hasNonLocalOrNonLiteralValue = true;
      varInfo.addRemovable(new RemovableBuilder().buildNamedClassExpression(classNode));
    }
    // If we're traversing the class expression, we've already decided we cannot remove it.
    traverseNode(baseClassExpression, scope);
    traverseClassMembers(classBodyNode, classScope);
  }

  private void traverseClassMembers(Node node, Scope scope) {
    checkArgument(node.isClassMembers(), node);
    if (!removeUnusedPrototypeProperties) {
      traverseChildren(node, scope);
      return;
    }

    for (Node member = node.getFirstChild(); member != null; member = member.getNext()) {
      switch (member.getToken()) {
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
          // If we get as far as traversing the members of a class, we've already decided that
          // we cannot remove the class itself, so just consider individual members for removal.
          considerForIndependentRemoval(
              new RemovableBuilder()
                  .addContinuation(new Continuation(member, scope))
                  .buildClassOrPrototypeNamedProperty(member));
          break;

        case COMPUTED_PROP:
          traverseChildren(member, scope);
          break;

        default:
          throw new IllegalStateException(
              "Unexpected child of CLASS_MEMBERS: " + member.toStringTree());
      }
    }
  }

  /**
   * Traverses a function
   *
   * ES6 scopes of a function include the parameter scope and the body scope
   * of the function.
   *
   * Note that CATCH blocks also create a new scope, but only for the
   * catch variable. Declarations within the block actually belong to the
   * enclosing scope. Because we don't remove catch variables, there's
   * no need to treat CATCH blocks differently like we do functions.
   */
  private void traverseFunction(Node function, Scope parentScope) {
    checkState(function.hasXChildren(3), function);
    checkState(function.isFunction(), function);

    final Node paramlist = NodeUtil.getFunctionParameters(function);
    final Node body = function.getLastChild();
    checkState(body.getNext() == null && body.isBlock(), body);

    // Checking the parameters
    Scope fparamScope = scopeCreator.createScope(function, parentScope);

    // Checking the function body
    Scope fbodyScope = scopeCreator.createScope(body, fparamScope);

    Node nameNode = function.getFirstChild();
    if (!nameNode.getString().isEmpty()) {
      // var x = function funcName() {};
      // make sure funcName gets into the varInfoMap so it will be considered for removal.
      VarInfo varInfo = traverseNameNode(nameNode, fparamScope);
      if (NodeUtil.isExpressionResultUsed(function)) {
        // var f = function g() {};
        // The f is an alias for g, so g escapes from the scope where it is defined.
        varInfo.hasNonLocalOrNonLiteralValue = true;
      }
    }

    traverseNode(paramlist, fparamScope);
    traverseChildren(body, fbodyScope);

    allFunctionParamScopes.add(fparamScope);
  }

  private boolean canRemoveParameters(Node parameterList) {
    checkState(parameterList.isParamList());
    Node function = parameterList.getParent();
    return removeGlobals && !NodeUtil.isGetOrSetKey(function.getParent());
  }

  /**
   * Removes unreferenced arguments from a function declaration and when possible the function's
   * callSites.
   *
   * @param fparamScope The function parameter
   */
  private void removeUnreferencedFunctionArgs(Scope fparamScope) {
    // Notice that removing unreferenced function args breaks
    // Function.prototype.length. In advanced mode, we don't really care
    // about this: we consider "length" the equivalent of reflecting on
    // the function's lexical source.
    //
    // Rather than create a new option for this, we assume that if the user
    // is removing globals, then it's OK to remove unused function args.
    //
    // See http://blickly.github.io/closure-compiler-issues/#253
    if (!removeGlobals) {
      return;
    }

    Node function = fparamScope.getRootNode();
    checkState(function.isFunction());
    if (NodeUtil.isGetOrSetKey(function.getParent())) {
      // The parameters object literal setters can not be removed.
      return;
    }

    Node argList = NodeUtil.getFunctionParameters(function);
    // Strip as many unreferenced args off the end of the function declaration as possible.
    maybeRemoveUnusedTrailingParameters(argList, fparamScope);

    // Mark any remaining unused parameters are unused to OptimizeParameters can try to remove
    // them.
    markUnusedParameters(argList, fparamScope);
  }

  private void markPropertyNameAsPinned(String propertyName) {
    if (pinnedPropertyNames.add(propertyName)) {
      // Continue traversal of all of the property name's values and no longer consider them for
      // removal.
      for (Removable removable : removablesForPropertyNames.removeAll(propertyName)) {
        removable.applyContinuations();
      }
    }
  }

  private void considerForIndependentRemoval(Removable removable) {
    if (removable.isNamedProperty()) {
      String propertyName = removable.getPropertyName();

      if (pinnedPropertyNames.contains(propertyName) || codingConvention.isExported(propertyName)) {
        // Referenced or exported, so not removable.
        removable.applyContinuations();
      } else if (isIndependentlyRemovable(removable)) {
        // Store for possible removal later.
        removablesForPropertyNames.put(propertyName, removable);
      } else {
        removable.applyContinuations();
        // This assignment counts as a reference, since we won't be removing it.
        // This is necessary in order to preserve getters and setters for the property.
        markPropertyNameAsPinned(propertyName);
      }
    } else {
      removable.applyContinuations();
    }
  }

  /** @return Whether or not accessor side-effect are a possibility. */
  private boolean considerForAccessorSideEffects(Node getprop, PropertyAccessKind usage) {
    checkState(getprop.isGetProp(), getprop); // Other node types may make sense in the future.

    String propName = getprop.getSecondChild().getString();
    PropertyAccessKind recorded = compiler.getAccessorSummary().getKind(propName);
    if ((recorded.hasGetter() && usage.hasGetter() && !assumeGettersArePure)
        || (recorded.hasSetter() && usage.hasSetter())) {
      markPropertyNameAsPinned(propName);
      return true;
    }

    return false;
  }

  private boolean isIndependentlyRemovable(Removable removable) {
    return (removeUnusedPrototypeProperties && removable.isPrototypeProperty())
        // TODO(b/139319709): Combine these with "removeUnusedPrototypeProperties". The fact that
        // only these two property type are conflated is arbitrary.
        || (removeUnusedThisProperties
            && (removable.isThisDotPropertyReference() || removable.isStaticProperty()))
        || (removeUnusedObjectDefinePropertiesDefinitions
            && removable.isObjectDefinePropertiesDefinition());
  }

  /**
   * Mark any remaining unused parameters as being unused so it can be used elsewhere.
   *
   * @param paramList list of function's parameters
   * @param fparamScope
   */
  private void markUnusedParameters(Node paramList, Scope fparamScope) {
    checkArgument(paramList.isParamList(), paramList);

    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      if (param.isUnusedParameter()) {
        // already marked
        continue;
      }

      Node paramNameNode = nameOfParam(param);
      if (paramNameNode == null) {
        // destructuring pattern parameters don't have a name that applies to the whole parameter
        // TODO(bradfordcsmith): We could mark this if we determined that all vars created by
        // the pattern are unused.
        continue;
      }

      VarInfo varInfo = traverseNameNode(paramNameNode, fparamScope);
      if (varInfo.isRemovable()) {
        param.setUnusedParameter(true);
        compiler.reportChangeToEnclosingScope(paramList);
        removalLog.log(RemovalLogRecord.forMarkingNamedArg(paramNameNode, paramList));
      }
    }
  }

  /**
   * Strip as many unreferenced args off the end of the function declaration as possible. We start
   * from the end of the function declaration because removing parameters from the middle of the
   * param list could mess up the interpretation of parameters being sent over by any function
   * calls.
   *
   * @param argList list of function's arguments
   * @param fparamScope
   */
  private void maybeRemoveUnusedTrailingParameters(Node argList, Scope fparamScope) {
    checkArgument(argList.isParamList(), argList);
    Node lastArg;
    while ((lastArg = argList.getLastChild()) != null) {
      Node argNode = lastArg;
      if (lastArg.isDefaultValue()) {
        argNode = lastArg.getFirstChild();
        if (astAnalyzer.mayHaveSideEffects(lastArg.getLastChild())) {
          break;
        }
      }

      if (argNode.isRest()) {
        argNode = argNode.getFirstChild();
      }

      if (argNode.isDestructuringPattern()) {
        if (argNode.hasChildren()) {
          // TODO(johnlenz): handle the case where there are no assignments.
          break;
        } else {
          // Remove empty destructuring patterns and their associated object literal assignment
          // if it exists and if the right hand side does not have side effects. Note, a
          // destructuring pattern with a "leftover" property key as in {a:{}} is not considered
          // empty in this case!
          NodeUtil.deleteNode(lastArg, compiler);
          removalLog.log(RemovalLogRecord.forDestructuringArg(argList));
          continue;
        }
      }

      VarInfo varInfo = getVarInfo(getVarForNameNode(argNode, fparamScope));
      if (varInfo.isRemovable()) {
        NodeUtil.deleteNode(lastArg, compiler);
        removalLog.log(RemovalLogRecord.forNamedArg(argNode, argList));
      } else {
        break;
      }
    }
  }

  /**
   * Handles a variable reference seen during traversal and returns a {@link VarInfo} object
   * appropriate for the given {@link Var}.
   *
   * <p>This is a wrapper for {@link #getVarInfo} that handles additional logic needed when we're
   * getting the {@link VarInfo} during traversal.
   */
  private VarInfo traverseVar(Var var) {
    checkNotNull(var);
    if (removeLocalVars && var.isArguments()) {
      // If we are considering removing local variables, that includes parameters.
      // If `arguments` is used in a function we must consider all parameters to be referenced.
      Scope functionScope = var.getScope().getClosestHoistScope();
      Node paramList = NodeUtil.getFunctionParameters(functionScope.getRootNode());
      for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
        Node lValue = nameOfParam(param);
        if (lValue == null) {
          continue;
        }

        getVarInfo(getVarForNameNode(lValue, functionScope)).markAsReferenced();
      }
      // `arguments` is never removable.
      return canonicalUnremovableVarInfo;
    } else {
      return getVarInfo(var);
    }
  }

  /**
   * Return the NAME node associated with a function parameter (the child of a PARAM_LIST), or null
   * if there is no single name.
   */
  @Nullable
  private static Node nameOfParam(Node param) {
    switch (param.getToken()) {
      case NAME:
        return param;
      case DEFAULT_VALUE:
        return nameOfParam(param.getFirstChild());
      case ITER_REST:
        return nameOfParam(param.getOnlyChild());
      case ARRAY_PATTERN:
      case OBJECT_PATTERN:
        return null;
      default:
        throw new IllegalStateException("Unexpected child of PARAM_LIST: " + param.toStringTree());
    }
  }

  /**
   * Get the right {@link VarInfo} object to use for the given {@link Var}.
   *
   * <p>This method is responsible for managing the entries in {@link #varInfoMap}.
   * <p>Note: Several {@link Var}s may share the same {@link VarInfo} when they should be treated
   * the same way.
   */
  private VarInfo getVarInfo(Var var) {
    checkNotNull(var);
    boolean isGlobal = var.isGlobal();
    if (var.isExtern()) {
      return canonicalUnremovableVarInfo;
    } else if (isGlobal && !removeGlobals) {
      return canonicalUnremovableVarInfo;
    } else if (!isGlobal && !removeLocalVars) {
      return canonicalUnremovableVarInfo;
    } else if (codingConvention.isExported(var.getName(), !isGlobal)) {
      return canonicalUnremovableVarInfo;
    } else if (var.isArguments()) {
      return canonicalUnremovableVarInfo;
    } else {
      VarInfo varInfo = varInfoMap.get(var);
      if (varInfo == null) {
        varInfo = new VarInfo();
        if (var.getParentNode().isParamList()) {
          varInfo.hasNonLocalOrNonLiteralValue = true;
        }
        varInfoMap.put(var, varInfo);
      }
      return varInfo;
    }
  }

  /**
   * Removes any vars in the scope that were not referenced. Removes any assignments to those
   * variables as well.
   */
  private void removeUnreferencedVarsAndPolyfills() {
    for (Entry<Var, VarInfo> entry : varInfoMap.entrySet()) {
      Var var = entry.getKey();
      VarInfo varInfo = entry.getValue();

      if (!varInfo.isRemovable()) {
        continue;
      }

      removalLog.log(RemovalLogRecord.forVar(var));
      // Regardless of what happens to the original declaration,
      // we need to remove all assigns, because they may contain references
      // to other unreferenced variables.
      varInfo.removeAllRemovables();

      Node nameNode = var.getNameNode();
      Node toRemove = nameNode.getParent();
      if (toRemove == null || alreadyRemoved(toRemove)) {
        // assignedVarInfo.removeAllRemovables () already removed it
      } else if (NodeUtil.isFunctionExpression(toRemove)) {
        // TODO(bradfordcsmith): Add a Removable for this case.
        if (!preserveFunctionExpressionNames) {
          Node fnNameNode = toRemove.getFirstChild();
          compiler.reportChangeToEnclosingScope(fnNameNode);
          fnNameNode.setString("");
        }
      } else {
        // Removables are not created for theses cases.
        // function foo(unused1 = someSideEffectingValue, ...unused2) {}
        // removeUnreferencedFunctionArgs() is responsible for removing these.
        // TODO(bradfordcsmith): handle parameter declarations with removables
        checkState(
            toRemove.isParamList()
                || (toRemove.getParent().isParamList()
                    && (toRemove.isDefaultValue() || toRemove.isRest())),
            "unremoved code: %s",
            toRemove);
      }
    }

    Iterator<PolyfillInfo> iter = polyfills.values().iterator();
    while (iter.hasNext()) {
      PolyfillInfo polyfill = iter.next();
      if (polyfill.isRemovable) {
        removalLog.log(RemovalLogRecord.forPolyfill(polyfill));
        polyfill.removable.remove(compiler);
        iter.remove();
      }
    }
  }

  /**
   * Our progress in a traversal can be expressed completely as the
   * current node and scope. The continuation lets us save that
   * information so that we can continue the traversal later.
   */
  private class Continuation {
    private final Node node;
    private final Scope scope;

    Continuation(Node node, Scope scope) {
      this.node = node;
      this.scope = scope;
    }

    void apply() {
      if (node.isFunction()) {
        // Calling traverseNode here would create infinite recursion for a function declaration
        traverseFunction(node, scope);
      } else {
        traverseNode(node, scope);
      }
    }
  }

  /** Represents a portion of the AST that can be removed. */
  private abstract class Removable {

    private final List<Continuation> continuations;
    @Nullable private final String propertyName;
    private final boolean isPrototypeDotPropertyReference;
    private final boolean isThisDotPropertyReference;

    private boolean continuationsAreApplied = false;
    private boolean isRemoved = false;

    Removable(RemovableBuilder builder) {
      continuations = builder.continuations;
      propertyName = builder.propertyName;
      isPrototypeDotPropertyReference = builder.isPrototypeDotPropertyReference;
      isThisDotPropertyReference = builder.isThisDotPropertyReference;
    }

    String getPropertyName() {
      return checkNotNull(propertyName);
    }

    /** Remove the associated nodes from the AST. */
    abstract void removeInternal(AbstractCompiler compiler);

    /** Remove the associated nodes from the AST, unless they've already been removed. */
    void remove(AbstractCompiler compiler) {
      if (!isRemoved) {
        isRemoved = true;
        removeInternal(compiler);
      }
    }

    public void applyContinuations() {
      if (!continuationsAreApplied) {
        continuationsAreApplied = true;
        for (Continuation c : continuations) {
          // Enqueue the continuation for processing.
          // Don't invoke the continuation immediately, because that can lead to concurrent
          // modification of data structures.
          worklist.add(c);
        }
        continuations.clear();
      }
    }

    /** True if this object represents assignment to a variable. */
    boolean isVariableAssignment() {
      return false;
    }

    /** True if this object represents a named property, either assignment or declaration. */
    boolean isNamedProperty() {
      return propertyName != null;
    }

    /**
     * True if this object represents assignment to a named property.
     *
     * <p>This does not include class or object literal member declarations.
     */
    boolean isNamedPropertyAssignment() {
      return false;
    }

    boolean isAssignedValueLocal() {
      return false; // assume non-local by default
    }

    /** Is this a direct assignment to `varName.prototype`? */
    boolean isPrototypeAssignment() {
      return isNamedPropertyAssignment() && propertyName.equals("prototype");
    }

    /** Is this an assignment to a property on a prototype object? */
    boolean isPrototypeDotPropertyReference() {
      return isPrototypeDotPropertyReference;
    }

    boolean isClassOrPrototypeNamedProperty() {
      return false;
    }

    boolean isPrototypeProperty() {
      return isPrototypeDotPropertyReference() || isClassOrPrototypeNamedProperty();
    }

    boolean isThisDotPropertyReference() {
      return isThisDotPropertyReference;
    }

    public boolean isObjectDefinePropertiesDefinition() {
      return false;
    }

    // TODO(b/134610338): Combine this method with `isPrototypeProperty`.
    public boolean isStaticProperty() {
      return false;
    }

    /**
     * Would a nonlocal or nonliteral value prevent removal of a variable associated with this
     * {@link Removable}?
     *
     * <p>True if the nature of this removable is such that a variable associated with it must not
     * be removed if its value or its prototype is not a local, literal value.
     *
     * <p>e.g. When X or X.prototype is nonlocal and / or nonliteral we don't know whether it is
     * safe to remove code like this.
     *
     * <pre><code>
     *   X.propName = something; // Don't know the effect of setting X.propName
     *   use(something instanceof X); // can't be certain there are no instances of X
     * </code></pre>
     */
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      return false;
    }
  }

  private class RemovableBuilder {
    final List<Continuation> continuations = new ArrayList<>();

    @Nullable String propertyName = null;
    boolean isPrototypeDotPropertyReference = false;
    boolean isThisDotPropertyReference = false;

    RemovableBuilder addContinuation(Continuation continuation) {
      continuations.add(continuation);
      return this;
    }

    RemovableBuilder setIsPrototypeDotPropertyReference(boolean value) {
      this.isPrototypeDotPropertyReference = value;
      return this;
    }

    RemovableBuilder setIsThisDotPropertyReference(boolean value) {
      this.isThisDotPropertyReference = value;
      return this;
    }

    IndirectAssign buildIndirectAssign(Node root, Node targetNode) {
      return new IndirectAssign(this, root, targetNode);
    }

    Polyfill buildPolyfill(Node polyfillNode) {
      return new Polyfill(this, polyfillNode);
    }

    ClassDeclaration buildClassDeclaration(Node classNode) {
      return new ClassDeclaration(this, classNode);
    }

    NamedClassExpression buildNamedClassExpression(Node classNode) {
      return new NamedClassExpression(this, classNode);
    }

    ClassOrPrototypeNamedProperty buildClassOrPrototypeNamedProperty(Node propertyNode) {
      checkArgument(
          propertyNode.isMemberFunctionDef()
              || NodeUtil.isGetOrSetKey(propertyNode)
              || (propertyNode.isStringKey() && !propertyNode.isQuotedString()),
          propertyNode);
      this.propertyName = propertyNode.getString();
      return new ClassOrPrototypeNamedProperty(this, propertyNode);
    }

    ObjectDefinePropertiesDefinition buildObjectDefinePropertiesDefinition(Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new ObjectDefinePropertiesDefinition(this, propertyNode);
    }

    FunctionDeclaration buildFunctionDeclaration(Node functionNode) {
      return new FunctionDeclaration(this, functionNode);
    }

    NameDeclarationStatement buildNameDeclarationStatement(Node declarationStatement) {
      return new NameDeclarationStatement(this, declarationStatement);
    }

    Assign buildNamedPropertyAssign(Node assignNode, Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new Assign(this, assignNode, Kind.NAMED_PROPERTY, propertyNode);
    }

    Assign buildComputedPropertyAssign(Node assignNode, Node propertyNode) {
      return new Assign(this, assignNode, Kind.COMPUTED_PROPERTY, propertyNode);
    }

    Assign buildVariableAssign(Node assignNode) {
      return new Assign(this, assignNode, Kind.VARIABLE, /* propertyNode */ null);
    }

    ClassSetupCall buildClassSetupCall(Node callNode) {
      return new ClassSetupCall(this, callNode);
    }

    VanillaForNameDeclaration buildVanillaForNameDeclaration(Node nameNode) {
      return new VanillaForNameDeclaration(this, nameNode);
    }

    AnonymousPrototypeNamedPropertyAssign buildAnonymousPrototypeNamedPropertyAssign(
        Node assignNode, String propertyName) {
      this.propertyName = propertyName;
      return new AnonymousPrototypeNamedPropertyAssign(this, assignNode);
    }

    IncOrDecOp buildIncOrDepOp(Node incOrDecOp, Node propertyNode, @Nullable Node toPreseve) {
      this.propertyName = propertyNode.getString();
      return new IncOrDecOp(this, incOrDecOp, toPreseve);
    }

    UnusedReadReference buildUnusedReadReference(Node referenceNode, Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new UnusedReadReference(this, referenceNode);
    }

    public Removable buildInstanceofName(Node instanceofNode) {
      return new InstanceofName(this, instanceofNode);
    }
  }

  /** Represents a read reference whose value is not used. */
  private class UnusedReadReference extends Removable {
    final Node referenceNode;

    UnusedReadReference(RemovableBuilder builder, Node referenceNode) {
      super(builder);
      // TODO(bradfordcsmith): handle `name;` and `name.property;` references
      checkState(
          isThisDotProperty(referenceNode) || isDotPrototypeDotProperty(referenceNode),
          referenceNode);
      this.referenceNode = referenceNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(referenceNode)) {
        if (isThisDotProperty(referenceNode)) {
          removeExpressionCompletely(referenceNode);
        } else {
          checkState(isDotPrototypeDotProperty(referenceNode), referenceNode);
          // objExpression.prototype.propertyName
          Node objExpression = referenceNode.getFirstFirstChild();
          if (astAnalyzer.mayHaveSideEffects(objExpression)) {
            replaceNodeWith(referenceNode, objExpression.detach());
          } else {
            removeExpressionCompletely(referenceNode);
          }
        }
      }
    }

    @Override
    public String toString() {
      return "UnusedReadReference:" + referenceNode;
    }
  }

  /**
   * Represents `something instanceof varName`.
   *
   * <p>If `varName` is removed, this expression can be replaced with `false` or
   * `(something, false)` to preserve side effects.
   */
  private class InstanceofName extends Removable {
    final Node instanceofNode;

    InstanceofName(RemovableBuilder builder, Node instanceofNode) {
      super(builder);
      checkArgument(instanceofNode.isInstanceOf(), instanceofNode);
      this.instanceofNode = instanceofNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(instanceofNode)) {
        Node lhs = instanceofNode.getFirstChild();
        Node falseNode = IR.falseNode().srcref(instanceofNode);
        if (astAnalyzer.mayHaveSideEffects(lhs)) {
          replaceNodeWith(instanceofNode, IR.comma(lhs.detach(), falseNode).srcref(instanceofNode));
        } else {
          replaceNodeWith(instanceofNode, falseNode);
        }
      }
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      // If we aren't sure where X comes from and what aliases it might have, we cannot be sure
      // there are no instances of it.
      return true;
    }

    @Override
    public String toString() {
      return "InstanceofName:" + instanceofNode;
    }
  }

  /** Represents an increment or decrement operation that could be removed. */
  private class IncOrDecOp extends Removable {
    final Node incOrDecNode;
    @Nullable final Node toPreserve;

    IncOrDecOp(RemovableBuilder builder, Node incOrDecNode, @Nullable Node toPreserve) {
      super(builder);
      checkArgument(incOrDecNode.isInc() || incOrDecNode.isDec(), incOrDecNode);

      Node arg = incOrDecNode.getOnlyChild();
      // TODO(bradfordcsmith): handle `name;` and `name.property;` references
      checkState(isThisDotProperty(arg) || isDotPrototypeDotProperty(arg), arg);

      this.incOrDecNode = incOrDecNode;
      this.toPreserve = toPreserve;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (alreadyRemoved(incOrDecNode)) {
        return;
      }

      Node arg = incOrDecNode.getOnlyChild();
      checkState(arg.isGetProp(), arg);

      if (this.toPreserve == null) {
        removeExpressionCompletely(incOrDecNode);
      } else {
        replaceNodeWith(incOrDecNode, toPreserve.detach());
      }
    }

    @Override
    public String toString() {
      return "IncOrDecOp:" + incOrDecNode;
    }
  }

  /** True for `this.propertyName` */
  private static boolean isThisDotProperty(Node n) {
    return n.isGetProp() && n.getFirstChild().isThis();
  }

  /** True for `(something).prototype.propertyName` */
  private static boolean isDotPrototypeDotProperty(Node n) {
    return n.isGetProp() && isDotPrototype(n.getFirstChild());
  }

  private class IndirectAssign extends Removable {
    /** The subtree which can be removed if the assignment is removable. */
    final Node root;
    /** The l-value expression below root. */
    final Node targetNode;

    IndirectAssign(RemovableBuilder builder, Node root, Node targetNode) {
      super(builder);

      Node rootParent = root.getParent();
      checkState(rootParent.isDestructuringPattern() || rootParent.isParamList(), rootParent);
      checkState(targetNode.isName() || targetNode.isGetProp(), targetNode);

      this.root = root;
      this.targetNode = targetNode;
    }

    @Override
    boolean isVariableAssignment() {
      return targetNode.isName();
    }

    @Override
    boolean isThisDotPropertyReference() {
      return isThisDotProperty(targetNode);
    }

    @Override
    boolean isNamedProperty() {
      return targetNode.isGetProp();
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      if (targetNode.isGetProp()) {
        Node getPropLhs = targetNode.getFirstChild();
        // assignment to varName.property or varName.prototype.property
        // cannot be removed unless varName and varName.prototype have literal, local values.
        return getPropLhs.isName() || isNameDotPrototype(getPropLhs);
      } else {
        return false;
      }
    }

    @Override
    boolean isNamedPropertyAssignment() {
      return targetNode.isGetProp();
    }

    @Override
    String getPropertyName() {
      checkState(targetNode.isGetProp(), targetNode);
      return targetNode.getLastChild().getString();
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(targetNode)) {
        removeRoot();
      }
    }

    private void removeRoot() {
      Node rootParent = root.getParent();

      switch (rootParent.getToken()) {
        case ARRAY_PATTERN:
          // [a, root, b] = something;
          // [a, root] = something;
          // Replace root with an empty node to avoid messing up the order of patterns,
          // then clean up trailing empties.
          replaceNodeWith(root, IR.empty().srcref(root));
          // We prefer `[a, b]` to `[a, b, , , , ]`
          // So remove any trailing empty nodes.
          for (Node maybeEmpty = rootParent.getLastChild();
              maybeEmpty != null && maybeEmpty.isEmpty();
              maybeEmpty = rootParent.getLastChild()) {
            maybeEmpty.detach();
          }
          compiler.reportChangeToEnclosingScope(rootParent);
          // TODO(bradfordcsmith): If the array pattern is now empty, try to remove it entirely.
          break;

        case PARAM_LIST:
          if (!root.isDefaultValue()) {
            // removeUnreferencedFunctionArgs() is responsible for removal of function parameter
            // positions, so all we can do here is remove the default value.
            // NOTE: traverseRest() avoids creating a removable for a rest parameter.
            // TODO(bradfordcsmith): Handle parameter removal consistently with other removals.
            return;
          }

          // function(removableName = removableValue)
          compiler.reportChangeToEnclosingScope(rootParent);
          // preserve the slot in the parameter list
          Node name = root.getFirstChild();
          checkState(name.isName());
          if (root == rootParent.getLastChild()
              && removeGlobals
              && canRemoveParameters(rootParent)) {
            // function(p1, removableName = removableDefault)
            // and we're allowed to remove the parameter entirely
            root.detach();
          } else {
            // function(removableName = removableDefault, otherParam)
            // or removableName is at the end, but cannot be completely removed.
            root.replaceWith(name.detach());
          }
          NodeUtil.markFunctionsDeleted(root, compiler);
          break;

        case OBJECT_PATTERN:
          // ({ [propExpression]: root } = something)
          // becomes
          // ({} = something)
          NodeUtil.deleteNode(root, compiler);
          break;

        default:
          throw new IllegalStateException(
              "Unexpected parent of indirect assignment: " + rootParent.toStringTree());
      }
    }
  }

  /** A call to $jscomp.polyfill that can be removed if it is no longer referenced. */
  private class Polyfill extends Removable {
    final Node polyfillNode;

    Polyfill(RemovableBuilder builder, Node polyfillNode) {
      super(builder);
      this.polyfillNode = polyfillNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(polyfillNode, compiler);
    }

    @Override
    public String toString() {
      return "Polyfill:" + polyfillNode;
    }
  }

  private class ClassDeclaration extends Removable {
    final Node classDeclarationNode;

    ClassDeclaration(RemovableBuilder builder, Node classDeclarationNode) {
      super(builder);
      this.classDeclarationNode = classDeclarationNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(classDeclarationNode, compiler);
    }

    @Override
    public String toString() {
      return "ClassDeclaration:" + classDeclarationNode;
    }
  }

  private class NamedClassExpression extends Removable {
    final Node classNode;

    NamedClassExpression(RemovableBuilder builder, Node classNode) {
      super(builder);
      this.classNode = classNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(classNode)) {
        Node nameNode = classNode.getFirstChild();
        if (!nameNode.isEmpty()) {
          // Just empty the class's name. If the expression is assigned to an unused variable,
          // then the whole class might still be removed as part of that assignment.
          classNode.replaceChild(nameNode, IR.empty().useSourceInfoFrom(nameNode));
          compiler.reportChangeToEnclosingScope(classNode);
        }
      }
    }

    @Override
    public String toString() {
      return "NamedClassExpression:" + classNode;
    }
  }

  private class ClassOrPrototypeNamedProperty extends Removable {
    final Node propertyNode;

    ClassOrPrototypeNamedProperty(RemovableBuilder builder, Node propertyNode) {
      super(builder);
      this.propertyNode = propertyNode;
    }

    @Override
    public boolean isStaticProperty() {
      return propertyNode.isStaticMember();
    }

    @Override
    boolean isClassOrPrototypeNamedProperty() {
      return !isStaticProperty();
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(propertyNode, compiler);
    }

    @Override
    public String toString() {
      return "ClassOrPrototypeNamedProperty:" + propertyNode;
    }
  }

  /**
   * Represents a single property definition in the object literal passed as the second argument to
   * e.g. `Object.defineProperties(obj, {p1: {value: 1}, p2: {value: 3}});`.
   */
  private class ObjectDefinePropertiesDefinition extends Removable {
    final Node propertyNode;

    ObjectDefinePropertiesDefinition(RemovableBuilder builder, Node propertyNode) {
      super(builder);
      this.propertyNode = propertyNode;
    }

    @Override
    public boolean isObjectDefinePropertiesDefinition() {
      return true;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(propertyNode, compiler);
    }
  }

  private class FunctionDeclaration extends Removable {
    final Node functionDeclarationNode;

    FunctionDeclaration(RemovableBuilder builder, Node functionDeclarationNode) {
      super(builder);
      this.functionDeclarationNode = functionDeclarationNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(functionDeclarationNode, compiler);
    }

    @Override
    boolean isAssignedValueLocal() {
      // The declared function is always created locally.
      return true;
    }

    @Override
    public String toString() {
      return "FunctionDeclaration:" + functionDeclarationNode;
    }
  }

  private class NameDeclarationStatement extends Removable {
    private final Node declarationStatement;

    public NameDeclarationStatement(RemovableBuilder builder, Node declarationStatement) {
      super(builder);
      checkArgument(NodeUtil.isNameDeclaration(declarationStatement), declarationStatement);
      this.declarationStatement = declarationStatement;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      Node nameNode = declarationStatement.getOnlyChild();
      Node valueNode = nameNode.getFirstChild();
      if (valueNode != null && astAnalyzer.mayHaveSideEffects(valueNode)) {
        compiler.reportChangeToEnclosingScope(declarationStatement);
        valueNode.detach();
        declarationStatement.replaceWith(IR.exprResult(valueNode).useSourceInfoFrom(valueNode));
      } else {
        NodeUtil.deleteNode(declarationStatement, compiler);
      }
    }

    @Override
    boolean isVariableAssignment() {
      return true;
    }

    @Override
    boolean isAssignedValueLocal() {
      Node nameNode = declarationStatement.getOnlyChild();
      Node valueNode = nameNode.getFirstChild();
      return valueNode == null
          || isLocalDefaultValueAssignment(nameNode, valueNode)
          || NodeUtil.evaluatesToLocalValue(valueNode);
    }

    @Override
    public String toString() {
      return "NameDeclStmt:" + declarationStatement;
    }
  }

  /**
   * True if targetNode is a qualified name and the valueNode is of the form `targetQualifiedName ||
   * localValue`.
   */
  private static boolean isLocalDefaultValueAssignment(Node targetNode, Node valueNode) {
    return valueNode.isOr()
        && targetNode.isQualifiedName()
        && valueNode.getFirstChild().isEquivalentTo(targetNode)
        && NodeUtil.evaluatesToLocalValue(valueNode.getLastChild());
  }

  enum Kind {
    // X = something;
    VARIABLE,
    // X.propertyName = something;
    // X.prototype.propertyName = something;
    NAMED_PROPERTY,
    // X[expression] = something;
    // X.prototype[expression] = something;
    COMPUTED_PROPERTY;
  }

  private class Assign extends Removable {

    final Node assignNode;
    final Kind kind;

    Assign(
        RemovableBuilder builder,
        Node assignNode,
        Kind kind,
        @Nullable Node propertyNode) {
      super(builder);
      checkArgument(NodeUtil.isAssignmentOp(assignNode), assignNode);
      if (kind == Kind.VARIABLE) {
        checkArgument(
            propertyNode == null,
            "got property node for simple variable assignment: %s",
            propertyNode);
      } else {
        checkArgument(propertyNode != null, "missing property node");
        if (kind == Kind.NAMED_PROPERTY) {
          checkArgument(propertyNode.isString(), "property name is not a string: %s", propertyNode);
        }
      }
      this.assignNode = assignNode;
      this.kind = kind;
    }

    /** True for `varName = value` assignments. */
    @Override
    boolean isVariableAssignment() {
      return kind == Kind.VARIABLE;
    }

    @Override
    boolean isAssignedValueLocal() {
      if (NodeUtil.isExpressionResultUsed(assignNode)) {
        // assigned value may escape or be aliased
        return false;
      } else {
        Node targetNode = assignNode.getFirstChild();
        Node valueNode = targetNode.getNext();
        return NodeUtil.evaluatesToLocalValue(valueNode)
            || isLocalDefaultValueAssignment(targetNode, valueNode);
      }
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      // If we don't know where the variable comes from or where it may go, then we don't know
      // whether it is safe to remove assignments to properties on it.
      return isNamedPropertyAssignment() || isComputedPropertyAssignment();
    }

    /** True for `varName.propName = value` and `varName.prototype.propName = value` assignments. */
    @Override
    boolean isNamedPropertyAssignment() {
      return kind == Kind.NAMED_PROPERTY;
    }

    /** True for `varName[expr] = value` and `varName.prototype[expr] = value` assignments. */
    boolean isComputedPropertyAssignment() {
      return kind == Kind.COMPUTED_PROPERTY;
    }

    @Override
    public boolean isStaticProperty() {
      Node lhs = assignNode.getFirstChild();
      if (lhs.isGetProp()) {
        // something.propName = someValue
        Node getPropLhs = lhs.getFirstChild();
        JSType typeI = getPropLhs.getJSType();
        return typeI != null && (typeI.isConstructor() || typeI.isInterface());
      } else {
        return false;
      }
    }

    /** Replace the current assign with its right hand side. */
    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (alreadyRemoved(assignNode)) {
        return;
      }
      Node parent = assignNode.getParent();
      compiler.reportChangeToEnclosingScope(parent);
      Node lhs = assignNode.getFirstChild();
      Node rhs = assignNode.getSecondChild();
      boolean mustPreserveRhs =
          astAnalyzer.mayHaveSideEffects(rhs) || NodeUtil.isExpressionResultUsed(assignNode);
      boolean mustPreserveGetElmExpr =
          lhs.isGetElem() && astAnalyzer.mayHaveSideEffects(lhs.getLastChild());

      if (mustPreserveRhs && mustPreserveGetElmExpr) {
        Node replacement =
            IR.comma(lhs.getLastChild().detach(), rhs.detach()).useSourceInfoFrom(assignNode);
        replaceNodeWith(assignNode, replacement);
      } else if (mustPreserveGetElmExpr) {
        replaceNodeWith(assignNode, lhs.getLastChild().detach());
      } else if (mustPreserveRhs) {
        replaceNodeWith(assignNode, rhs.detach());
      } else {
        removeExpressionCompletely(assignNode);
      }
    }

    @Override
    public String toString() {
      return "Assign:" + assignNode;
    }
  }

  /** Represents `(someObjectExpression).prototype.propertyName = someValue`. */
  private class AnonymousPrototypeNamedPropertyAssign extends Removable {
    final Node assignNode;

    AnonymousPrototypeNamedPropertyAssign(RemovableBuilder builder, Node assignNode) {
      super(builder);
      checkNotNull(builder.propertyName);
      checkArgument(assignNode.isAssign(), assignNode);
      this.assignNode = assignNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (alreadyRemoved(assignNode)) {
        return;
      }
      Node parent = assignNode.getParent();
      compiler.reportChangeToEnclosingScope(parent);
      Node lhs = assignNode.getFirstChild();
      Node rhs = assignNode.getLastChild();

      checkState(lhs.isGetProp(), lhs);
      Node objDotPrototype = lhs.getFirstChild();
      checkState(objDotPrototype.isGetProp(), objDotPrototype);
      Node objExpression = objDotPrototype.getFirstChild();
      Node prototype = objDotPrototype.getLastChild();
      checkState(prototype.getString().equals("prototype"), prototype);

      boolean mustPreserveRhs =
          astAnalyzer.mayHaveSideEffects(rhs) || NodeUtil.isExpressionResultUsed(assignNode);
      boolean mustPreserveObjExpression = astAnalyzer.mayHaveSideEffects(objExpression);

      if (mustPreserveRhs && mustPreserveObjExpression) {
        Node replacement =
            IR.comma(objExpression.detach(), rhs.detach()).useSourceInfoFrom(assignNode);
        replaceNodeWith(assignNode, replacement);
      } else if (mustPreserveObjExpression) {
        replaceNodeWith(assignNode, objExpression.detach());
      } else if (mustPreserveRhs) {
        replaceNodeWith(assignNode, rhs.detach());
      } else {
        removeExpressionCompletely(assignNode);
      }
    }

    @Override
    boolean isPrototypeProperty() {
      return true;
    }

    @Override
    public String toString() {
      return "AnonymousPrototypeNamedPropertyAssign:" + assignNode;
    }
  }

  /**
   * Represents a call to a class setup method such as `goog.inherits()` or
   * `goog.addSingletonGetter()`.
   */
  private class ClassSetupCall extends Removable {
    final Node callNode;

    ClassSetupCall(RemovableBuilder builder, Node callNode) {
      super(builder);
      this.callNode = callNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      Node parent = callNode.getParent();

      Node replacement = null;
      // Need to keep call args that have side effects.
      // Easiest thing to do is break apart the call node as we go.
      // First child is the callee (aka. Object.defineProperties or equivalent)
      callNode.removeFirstChild();
      for (Node arg = callNode.getLastChild(); arg != null; arg = callNode.getLastChild()) {
        arg.detach();
        if (astAnalyzer.mayHaveSideEffects(arg)) {
          if (replacement == null) {
            replacement = arg;
          } else {
            replacement = IR.comma(arg, replacement).srcref(callNode);
          }
        } else {
          NodeUtil.markFunctionsDeleted(arg, compiler);
        }
      }
      // NOTE: The call must either be its own statement or the LHS of a comma expression,
      // because it doesn't have a meaningful return value.
      if (replacement != null) {
        replaceNodeWith(callNode, replacement);
      } else if (parent.isExprResult()) {
        NodeUtil.deleteNode(parent, compiler);
      } else {
        checkState(parent.isComma());
        if (parent.getFirstChild() == callNode) {
          // `(goog.inherits(A, B), something)` -> `something`
          Node rhs = checkNotNull(callNode.getNext());
          compiler.reportChangeToEnclosingScope(parent);
          parent.replaceWith(rhs.detach());
        } else {

          // As we have been asked to remove the RHS of the comma expression, we know the value is
          // unused, as such it is safe to expose the LHS's value as the result of the
          // expression.

          // `(something, Object.defineProperties(A, B))` -> `something`
          Node lhs = parent.getFirstChild();
          compiler.reportChangeToEnclosingScope(parent);
          parent.replaceWith(lhs.detach());
        }
      }
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      // If we aren't sure where X comes from and what aliases it might have, we cannot be sure
      // it's safe to remove the class setup for it.
      return true;
    }

    @Override
    public String toString() {
      return "ClassSetupCall:" + callNode;
    }
  }

  private static boolean alreadyRemoved(Node n) {
    Node parent = n.getParent();
    if (parent == null) {
      return true;
    }
    if (parent.isRoot()) {
      return false;
    }
    return alreadyRemoved(parent);
  }

  private class VarInfo {
    /**
     * Objects that represent variable declarations, assignments, or class setup calls that can
     * be removed.
     *
     * NOTE: Once we realize that we cannot remove the variable, this list will be cleared and
     * no more will be added.
     */
    final List<Removable> removables = new ArrayList<>();

    boolean isEntirelyRemovable = true;

    boolean hasNonLocalOrNonLiteralValue = false;
    boolean requiresLocalLiteralValueForRemoval = false;

    void addRemovable(Removable removable) {
      if (!removable.isAssignedValueLocal()
          && (removable.isVariableAssignment() || removable.isPrototypeAssignment())) {
        hasNonLocalOrNonLiteralValue = true;
      }
      if (removable.preventsRemovalOfVariableWithNonLocalValueOrPrototype()) {
        requiresLocalLiteralValueForRemoval = true;
      }
      if (hasNonLocalOrNonLiteralValue && requiresLocalLiteralValueForRemoval) {
        setIsExplicitlyNotRemovable();
      }

      if (isEntirelyRemovable) {
        // Store for possible removal later.
        removables.add(removable);
      } else {
        considerForIndependentRemoval(removable);
      }
    }

    /**
     * Marks this variable as referenced and evaluates any continuations if not previously marked as
     * referenced.
     *
     * @return true if the variable was not already marked as referenced
     */
    boolean markAsReferenced() {
      return setIsExplicitlyNotRemovable();
    }

    boolean isRemovable() {
      return isEntirelyRemovable;
    }

    boolean setIsExplicitlyNotRemovable() {
      if (isEntirelyRemovable) {
        isEntirelyRemovable = false;
        for (Removable r : removables) {
          considerForIndependentRemoval(r);
        }
        removables.clear();
        return true;
      } else {
        return false;
      }
    }

    void removeAllRemovables() {
      checkState(isEntirelyRemovable);
      for (Removable removable : removables) {
        removable.remove(compiler);
      }
      removables.clear();
    }

  }

  /**
   * Makes a new PolyfillInfo, including the correct Removable. Parses the name to determine whether
   * this is a global, static, or prototype polyfill.
   */
  private PolyfillInfo createPolyfillInfo(Node call, Scope scope, String name) {
    checkState(call.getParent().isExprResult());
    // Make the removable and polyfill info.  Add continuations for all arguments.
    RemovableBuilder builder = new RemovableBuilder();
    for (Node n = call.getFirstChild().getNext(); n != null; n = n.getNext()) {
      builder.addContinuation(new Continuation(n, scope));
    }
    Polyfill removable = builder.buildPolyfill(call.getParent());
    int lastDot = name.lastIndexOf(".");
    if (lastDot < 0) {
      return new GlobalPolyfillInfo(removable, name);
    }
    String owner = name.substring(0, lastDot);
    String prop = name.substring(lastDot + 1);
    boolean typed = call.getJSType() != null;
    if (owner.endsWith(DOT_PROTOTYPE)) {
      owner = owner.substring(0, owner.length() - DOT_PROTOTYPE.length());
      return new PrototypePropertyPolyfillInfo(
          removable, prop, typed ? compiler.getTypeRegistry().getType(scope, owner) : null);
    }
    ObjectType ownerInstanceType =
        typed ? ObjectType.cast(compiler.getTypeRegistry().getType(scope, owner)) : null;
    JSType ownerCtorType = ownerInstanceType != null ? ownerInstanceType.getConstructor() : null;
    return new StaticPropertyPolyfillInfo(removable, prop, ownerCtorType, owner);
  }

  private static final String DOT_PROTOTYPE = ".prototype";

  /**
   * Stores information about definitions and usages of polyfills.
   *
   * <p>The polyfill removal strategy is as follows. First, look for all the polyfill definitions,
   * whose names are stores as strings passed as the first argument to {@code $jscomp.polyfill}.
   * Each definition falls into one of three categories: (1) global names, such as {@code Map} or
   * {@code Promise}; (2) static properties, such as {@code Array.from} or {@code Reflect.get},
   * which must always have exactly two name components; or (3) prototype properties, such as {@code
   * String.prototype.repeat} or {@code Promise.prototype.finally}, which must always have exactly
   * three name components. The definition can be removed once it is found that there are no
   * references to it.
   *
   * <p>References are ignored if they are "guarded". This allows removing, e.g, the Promise
   * polyfill if it is only referenced in `if (typeof Promise === 'function') { use(Promise); }`.
   * Note that a guarded reference to a polyfill does not guarantee its removal, either. Polyfills
   * may have nonguarded references as well.
   *
   * <p>Determining whether a node is a reference depends on the type of polyfill. When type
   * information is available, the type of the expected owner (i.e. the global object for global
   * polyfills, the namespace or class for static polyfills, or an instance of the owning class (or
   * its implicit prototype) for prototype polyfills) is used exclusively to determine this with
   * very good accuracy. Types are considered to match if a direct cast would be allowed without a
   * warning (i.e. some element of the union is a direct subtype or supertype).
   *
   * <p>When type information is not available (or is too loose) then we fall back on a heuristic:
   *
   * <ul>
   *   <li>globals are referenced by any same-named NAME node or any GETPROP node whose last child
   *       has the same string (this allows matching {@code goog.global.Map}, but will also match
   *       {@code MyOuter.Map}).
   *   <li>static properties are referenced by any GETPROP node whose last child is the same as the
   *       polyfill's property name and whose owner references the polyfill owner per the above
   *       rule.
   *   <li>prototype properties are referenced by any GETPROP node whose last child is the same as
   *       the polyfill's property name, regardless of its owner.
   * </ul>
   *
   * <p>Note that this results in both false positives and false negatives in untyped code: we may
   * remove polyfills that are actually used (e.g. if {@code Array.from} is accessed via a subclass
   * as {@code SubArray.from} or in a subclass' static method as {@code this.from}) and we may
   * retain polyfills that are not used (e.g. if a user-defined nested class shares the same name as
   * a global builtin, as in {@code Foo.Map}). For greater consistency we may shift this balance in
   * the future to eliminate the possibility of incorrect removals, at the cost of more incorrect
   * retentions.
   */
  private abstract class PolyfillInfo {
    /** The {@link Polyfill} instance corresponding to the polyfill's definition. */
    final Polyfill removable;
    /** The rightmost component of the polyfill's qualified name (does not contain a dot). */
    final String key;
    /** Whether the polyfill is unreferenced and this can be removed safely. */
    boolean isRemovable = true;

    PolyfillInfo(Polyfill removable, String key) {
      this.removable = removable;
      this.key = key;
    }

    /**
     * Accepts a NAME or GETPROP node whose (property) string matches {@code key} and checks whether
     * the node should be considered as a possible reference to this polyfill. If so, mark the
     * polyfill as referenced and therefore not removable.
     */
    void considerPossibleReference(Node n) {
      if (isRemovable && !guardedUsages.contains(n)) {
        considerPossibleReferenceInternal(n);
        if (!isRemovable) {
          removable.applyContinuations();
        }
      }
    }

    String getName() {
      return key;
    }

    /** Template method to check the node. */
    abstract void considerPossibleReferenceInternal(Node n);
  }

  private class GlobalPolyfillInfo extends PolyfillInfo {

    GlobalPolyfillInfo(Polyfill removable, String name) {
      super(removable, name);
    }

    @Override
    void considerPossibleReferenceInternal(Node possiblyReferencingNode) {
      if (possiblyReferencingNode.isName()) {
        // A matching NAME node must be a reference (there's no need to check that the referenced
        // Var is global, since local variables have all been renamed by normalization).
        isRemovable = false;
      } else if (possiblyReferencingNode.isGetProp()) {
        // Assume that the owner is possibly the global `this` and skip removal.
        isRemovable = false;
      }
    }
  }

  private class StaticPropertyPolyfillInfo extends PolyfillInfo {
    // Type of the owner, if available.
    @Nullable final JSType polyfillOwnerType;
    // Name of the owning type, used only when there's no type information.
    final String polyfillOwnerName;

    StaticPropertyPolyfillInfo(
        Polyfill removable, String key, @Nullable JSType owner, String ownerName) {
      super(removable, key);
      this.polyfillOwnerName = checkNotNull(ownerName);
      this.polyfillOwnerType = owner;
    }

    @Override
    String getName() {
      return polyfillOwnerName + "." + key;
    }

    @Override
    void considerPossibleReferenceInternal(Node possiblyReferencingNode) {
      if (!possiblyReferencingNode.isGetProp()) {
        return;
      }
      isRemovable = false;
    }
  }

  private class PrototypePropertyPolyfillInfo extends PolyfillInfo {
    @Nullable final JSType polyfillOwnerType;

    PrototypePropertyPolyfillInfo(Polyfill removable, String key, @Nullable JSType owner) {
      super(removable, key);
      this.polyfillOwnerType = owner;
    }

    @Override
    String getName() {
      String ownerName =
          (polyfillOwnerType == null) ? "<anonymous>" : polyfillOwnerType.getDisplayName();
      return ownerName + ".prototype." + key;
    }

    @Override
    void considerPossibleReferenceInternal(Node possiblyReferencingNode) {
      if (!possiblyReferencingNode.isGetProp()) {
        return;
      }
      // Prototype properties are simply not removable.
      isRemovable = false;
    }
  }

  /**
   * Represents declarations in the standard for-loop initialization.
   *
   * <p>e.g. the `let i = 0` part of `for (let i = 0; i < 10; ++i) {...}`. These must be handled
   * differently from declaration statements because:
   *
   * <ol>
   *   <li>For-loop declarations may declare more than one variable. The normalization doesn't break
   *       them up as it does for declaration statements.
   *   <li>Removal must be handled differently.
   *   <li>We don't currently preserve initializers with side effects here. Instead, we just
   *       consider such cases non-removable.
   * </ol>
   */
  private class VanillaForNameDeclaration extends Removable {

    private final Node nameNode;

    private VanillaForNameDeclaration(RemovableBuilder builder, Node nameNode) {
      super(builder);
      this.nameNode = nameNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      Node declaration = checkNotNull(nameNode.getParent());
      compiler.reportChangeToEnclosingScope(declaration);
      // NOTE: We don't need to preserve the initializer value, because we currently do not remove
      //     for-loop vars whose initializing values have side effects.
      if (nameNode.getPrevious() == null && nameNode.getNext() == null) {
        // only child, so we can remove the whole declaration
        declaration.replaceWith(IR.empty().useSourceInfoFrom(declaration));
      } else {
        declaration.removeChild(nameNode);
      }
      NodeUtil.markFunctionsDeleted(nameNode, compiler);
    }
  }

  void removeExpressionCompletely(Node expression) {
    checkState(!NodeUtil.isExpressionResultUsed(expression), expression);
    Node parent = expression.getParent();
    if (parent.isExprResult()) {
      NodeUtil.deleteNode(parent, compiler);
    } else if (parent.isComma()) {
      // Expression is probably the first child of the comma,
      // but it could be the second if the entire comma expression value is unused.
      Node otherChild = expression.getNext();
      if (otherChild == null) {
        otherChild = expression.getPrevious();
      }
      replaceNodeWith(parent, otherChild.detach());
    } else {
      // value isn't needed, but we need to keep the AST valid.
      replaceNodeWith(expression, IR.number(0).useSourceInfoFrom(expression));
    }
  }

  void replaceNodeWith(Node n, Node replacement) {
    compiler.reportChangeToEnclosingScope(n);
    n.replaceWith(replacement);
    NodeUtil.markFunctionsDeleted(n, compiler);
  }
}
