/*
 * Copyright 2006 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.Normalize.PropagateConstantAnnotationsOverVars;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.JSType;
import java.util.List;
import java.util.Map;

/**
 * Flattens global objects/namespaces by replacing each '.' with '$' in
 * their names. This reduces the number of property lookups the browser has
 * to do and allows the {@link RenameVars} pass to shorten namespaced names.
 * For example, goog.events.handleEvent() -> goog$events$handleEvent() -> Za().
 *
 * <p>If a global object's name is assigned to more than once, or if a property
 * is added to the global object in a complex expression, then none of its
 * properties will be collapsed (for safety/correctness).
 *
 * <p>If, after a global object is declared, it is never referenced except when
 * its properties are read or set, then the object will be removed after its
 * properties have been collapsed.
 *
 * <p>Uninitialized variable stubs are created at a global object's declaration
 * site for any of its properties that are added late in a local scope.
 *
 * <p> Static properties of constructors are always collapsed, unsafely!
 * For other objects: if, after an object is declared, it is referenced directly
 * in a way that might create an alias for it, then none of its properties will
 * be collapsed.
 * This behavior is a safeguard to prevent the values associated with the
 * flattened names from getting out of sync with the object's actual property
 * values. For example, in the following case, an alias a$b, if created, could
 * easily keep the value 0 even after a.b became 5:
 * <code> a = {b: 0}; c = a; c.b = 5; </code>.
 *
 * <p>This pass doesn't flatten property accesses of the form: a[b].
 *
 * <p>For lots of examples, see the unit test.
 *
 */
class CollapseProperties implements CompilerPass {
  // Warnings
  static final DiagnosticType UNSAFE_NAMESPACE_WARNING =
      DiagnosticType.warning(
          "JSC_UNSAFE_NAMESPACE",
          "incomplete alias created for namespace {0}");

  static final DiagnosticType NAMESPACE_REDEFINED_WARNING =
      DiagnosticType.warning(
          "JSC_NAMESPACE_REDEFINED",
          "namespace {0} should not be redefined");

  static final DiagnosticType UNSAFE_THIS = DiagnosticType.warning(
      "JSC_UNSAFE_THIS",
      "dangerous use of ''this'' in static method {0}");

  private final AbstractCompiler compiler;
  private final PropertyCollapseLevel propertyCollapseLevel;

  /** Global namespace tree */
  private List<Name> globalNames;

  /** Maps names (e.g. "a.b.c") to nodes in the global namespace tree */
  private Map<String, Name> nameMap;

  CollapseProperties(AbstractCompiler compiler, PropertyCollapseLevel propertyCollapseLevel) {
    this.compiler = compiler;
    this.propertyCollapseLevel = propertyCollapseLevel;
  }

  @Override
  public void process(Node externs, Node root) {
    GlobalNamespace namespace = new GlobalNamespace(compiler, root);
    nameMap = namespace.getNameIndex();
    globalNames = namespace.getNameForest();
    checkNamespaces();

    for (Name name : globalNames) {
      flattenReferencesToCollapsibleDescendantNames(name, name.getBaseName());
    }

    // We collapse property definitions after collapsing property references
    // because this step can alter the parse tree above property references,
    // invalidating the node ancestry stored with each reference.
    for (Name name : globalNames) {
      collapseDeclarationOfNameAndDescendants(name, name.getBaseName());
    }

    // This shouldn't be necessary, this pass should already be setting new constants as constant.
    // TODO(b/64256754): Investigate.
    (new PropagateConstantAnnotationsOverVars(compiler, false)).process(externs, root);
  }

  private boolean canCollapse(Name name) {
    if (!name.canCollapse()) {
      return false;
    }

    if (propertyCollapseLevel == PropertyCollapseLevel.MODULE_EXPORT && !name.isModuleExport()) {
      return false;
    }

    return true;
  }

  private boolean canEliminate(Name name) {
    if (!name.canEliminate()) {
      return false;
    }

    if (name.props == null
        || name.props.isEmpty()
        || propertyCollapseLevel != PropertyCollapseLevel.MODULE_EXPORT) {
      return true;
    }

    return false;
  }

  /**
   * Runs through all namespaces (prefixes of classes and enums), and checks if any of them have
   * been used in an unsafe way.
   */
  private void checkNamespaces() {
    for (Name name : nameMap.values()) {
      if (name.isNamespaceObjectLit()
          && (name.getAliasingGets() > 0
              || name.getLocalSets() + name.getGlobalSets() > 1
              || name.getDeleteProps() > 0)) {
        boolean initialized = name.getDeclaration() != null;
        for (Ref ref : name.getRefs()) {
          if (ref == name.getDeclaration()) {
            continue;
          }

          if (ref.type == Ref.Type.DELETE_PROP) {
            if (initialized) {
              warnAboutNamespaceRedefinition(name, ref);
            }
          } else if (
              ref.type == Ref.Type.SET_FROM_GLOBAL
              || ref.type == Ref.Type.SET_FROM_LOCAL) {
            if (initialized && !isSafeNamespaceReinit(ref)) {
              warnAboutNamespaceRedefinition(name, ref);
            }

            initialized = true;
          } else if (ref.type == Ref.Type.ALIASING_GET) {
            warnAboutNamespaceAliasing(name, ref);
          }
        }
      }
    }
  }

  static boolean isSafeNamespaceReinit(Ref ref) {
    // allow "a = a || {}" or "var a = a || {}" or "var a;"
    Node valParent = getValueParent(ref);
    Node val = valParent.getLastChild();
    if (val != null && val.isOr()) {
      Node maybeName = val.getFirstChild();
      if (ref.node.matchesQualifiedName(maybeName)) {
        return true;
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
    // there are four types of declarations: VARs, LETs, CONSTs, and ASSIGNs
    Node n = ref.node.getParent();
    return (n != null && NodeUtil.isNameDeclaration(n)) ? ref.node : ref.node.getParent();
  }

  /**
   * Reports a warning because a namespace was aliased.
   *
   * @param nameObj A namespace that is being aliased
   * @param ref The reference that forced the alias
   */
  private void warnAboutNamespaceAliasing(Name nameObj, Ref ref) {
    compiler.report(
        JSError.make(ref.node,
                     UNSAFE_NAMESPACE_WARNING, nameObj.getFullName()));
  }

  /**
   * Reports a warning because a namespace was redefined.
   *
   * @param nameObj A namespace that is being redefined
   * @param ref The reference that set the namespace
   */
  private void warnAboutNamespaceRedefinition(Name nameObj, Ref ref) {
    compiler.report(
        JSError.make(ref.node,
                     NAMESPACE_REDEFINED_WARNING, nameObj.getFullName()));
  }

  /**
   * Flattens all references to collapsible properties of a global name except
   * their initial definitions. Recurs on subnames.
   *
   * @param n An object representing a global name
   * @param alias The flattened name for {@code n}
   */
  private void flattenReferencesToCollapsibleDescendantNames(
      Name n, String alias) {
    if (n.props == null || n.isCollapsingExplicitlyDenied()) {
      return;
    }

    for (Name p : n.props) {
      String propAlias = appendPropForAlias(alias, p.getBaseName());

      boolean isAllowedToCollapse =
          propertyCollapseLevel != PropertyCollapseLevel.MODULE_EXPORT || p.isModuleExport();

      if (isAllowedToCollapse && p.canCollapse()) {
        flattenReferencesTo(p, propAlias);
      } else if (isAllowedToCollapse
          && p.isSimpleStubDeclaration()
          && !p.isCollapsingExplicitlyDenied()) {
        flattenSimpleStubDeclaration(p, propAlias);
      }

      flattenReferencesToCollapsibleDescendantNames(p, propAlias);
    }
  }

  /**
   * Flattens a stub declaration.
   * This is mostly a hack to support legacy users.
   */
  private void flattenSimpleStubDeclaration(Name name, String alias) {
    Ref ref = Iterables.getOnlyElement(name.getRefs());
    Node nameNode = NodeUtil.newName(
        compiler, alias, ref.node,
        name.getFullName());
    Node varNode = IR.var(nameNode).useSourceInfoIfMissingFrom(nameNode);

    checkState(ref.node.getParent().isExprResult());
    Node parent = ref.node.getParent();
    Node grandparent = parent.getParent();
    grandparent.replaceChild(parent, varNode);
    compiler.reportChangeToEnclosingScope(varNode);
  }

  /**
   * Flattens all references to a collapsible property of a global name except
   * its initial definition.
   *
   * @param n A global property name (e.g. "a.b" or "a.b.c.d")
   * @param alias The flattened name (e.g. "a$b" or "a$b$c$d")
   */
  private void flattenReferencesTo(Name n, String alias) {
    String originalName = n.getFullName();
    for (Ref r : n.getRefs()) {
      if (r == n.getDeclaration()) {
        // Declarations are handled separately.
        continue;
      }
      Node rParent = r.node.getParent();
      // There are two cases when we shouldn't flatten a reference:
      // 1) Object literal keys, because duplicate keys show up as refs.
      // 2) References inside a complex assign. (a = x.y = 0). These are
      //    called TWIN references, because they show up twice in the
      //    reference list. Only collapse the set, not the alias.
      if (!NodeUtil.isObjectLitKey(r.node) && (r.getTwin() == null || r.isSet())) {
        flattenNameRef(alias, r.node, rParent, originalName);
      }
    }

    // Flatten all occurrences of a name as a prefix of its subnames. For
    // example, if {@code n} corresponds to the name "a.b", then "a.b" will be
    // replaced with "a$b" in all occurrences of "a.b.c", "a.b.c.d", etc.
    if (n.props != null) {
      for (Name p : n.props) {
        flattenPrefixes(alias, p, 1);
      }
    }
  }

  /**
   * Flattens all occurrences of a name as a prefix of subnames beginning
   * with a particular subname.
   *
   * @param n A global property name (e.g. "a.b.c.d")
   * @param alias A flattened prefix name (e.g. "a$b")
   * @param depth The difference in depth between the property name and
   *    the prefix name (e.g. 2)
   */
  private void flattenPrefixes(String alias, Name n, int depth) {
    // Only flatten the prefix of a name declaration if the name being
    // initialized is fully qualified (i.e. not an object literal key).
    String originalName = n.getFullName();
    Ref decl = n.getDeclaration();
    if (decl != null && decl.node != null && decl.node.isGetProp()) {
      flattenNameRefAtDepth(alias, decl.node, depth, originalName);
    }

    for (Ref r : n.getRefs()) {
      if (r == decl) {
        // Declarations are handled separately.
        continue;
      }

      // References inside a complex assign (a = x.y = 0)
      // have twins. We should only flatten one of the twins.
      if (r.getTwin() == null || r.isSet()) {
        flattenNameRefAtDepth(alias, r.node, depth, originalName);
      }
    }

    if (n.props != null) {
      for (Name p : n.props) {
        flattenPrefixes(alias, p, depth + 1);
      }
    }
  }

  /**
   * Flattens a particular prefix of a single name reference.
   *
   * @param alias A flattened prefix name (e.g. "a$b")
   * @param n The node corresponding to a subproperty name (e.g. "a.b.c.d")
   * @param depth The difference in depth between the property name and
   *    the prefix name (e.g. 2)
   * @param originalName String version of the property name.
   */
  private void flattenNameRefAtDepth(String alias, Node n, int depth,
      String originalName) {
    // This method has to work for both GETPROP chains and, in rare cases,
    // OBJLIT keys, possibly nested. That's why we check for children before
    // proceeding. In the OBJLIT case, we don't need to do anything.
    Token nType = n.getToken();
    boolean isQName = nType == Token.NAME || nType == Token.GETPROP;
    boolean isObjKey = NodeUtil.isObjectLitKey(n);
    checkState(isObjKey || isQName);
    if (isQName) {
      for (int i = 1; i < depth && n.hasChildren(); i++) {
        n = n.getFirstChild();
      }
      if (n.isGetProp() && n.getFirstChild().isGetProp()) {
        flattenNameRef(alias, n.getFirstChild(), n, originalName);
      }
    }
  }

  /**
   * Replaces a GETPROP a.b.c with a NAME a$b$c.
   *
   * @param alias A flattened prefix name (e.g. "a$b")
   * @param n The GETPROP node corresponding to the original name (e.g. "a.b")
   * @param parent {@code n}'s parent
   * @param originalName String version of the property name.
   */
  private void flattenNameRef(String alias, Node n, Node parent,
      String originalName) {
    Preconditions.checkArgument(
        n.isGetProp(), "Expected GETPROP, found %s. Node: %s", n.getToken(), n);

    // BEFORE:
    //   getprop
    //     getprop
    //       name a
    //       string b
    //     string c
    // AFTER:
    //   name a$b$c
    Node ref = NodeUtil.newName(compiler, alias, n, originalName);
    NodeUtil.copyNameAnnotations(n.getLastChild(), ref);
    if (parent.isCall() && n == parent.getFirstChild()) {
      // The node was a call target, we are deliberately flatten these as
      // we node the "this" isn't provided by the namespace. Mark it as such:
      parent.putBooleanProp(Node.FREE_CALL, true);
    }

    JSType type = n.getJSType();
    if (type != null) {
      ref.setJSType(type);
    }

    parent.replaceChild(n, ref);
    compiler.reportChangeToEnclosingScope(ref);
  }

  /**
   * Collapses definitions of the collapsible properties of a global name.
   * Recurs on subnames that also represent JavaScript objects with
   * collapsible properties.
   *
   * @param n A node representing a global name
   * @param alias The flattened name for {@code n}
   */
  private void collapseDeclarationOfNameAndDescendants(Name n, String alias) {
    boolean canCollapseChildNames = n.canCollapseUnannotatedChildNames();

    // Handle this name first so that nested object literals get unrolled.
    if (canCollapse(n)) {
      updateGlobalNameDeclaration(n, alias, canCollapseChildNames);
    }

    if (n.props == null) {
      return;
    }
    for (Name p : n.props) {
      collapseDeclarationOfNameAndDescendants(p, appendPropForAlias(alias, p.getBaseName()));
    }
  }

  /**
   * Updates the initial assignment to a collapsible property at global scope
   * by adding a VAR stub and collapsing the property. e.g. c = a.b = 1; => var a$b; c = a$b = 1;
   * This specifically handles "twinned" assignments, which are those where the assignment is also
   * used as a reference and which need special handling.
   *
   * @param alias The flattened property name (e.g. "a$b")
   * @param refName The name for the reference being updated.
   * @param ref An object containing information about the assignment getting updated
   */
  private void updateTwinnedDeclaration(String alias, Name refName, Ref ref) {
    checkNotNull(ref.getTwin());
    // Don't handle declarations of an already flat name, just qualified names.
    if (!ref.node.isGetProp()) {
      return;
    }
    Node rvalue = ref.node.getNext();
    Node parent = ref.node.getParent();
    Node grandparent = parent.getParent();

    if (rvalue != null && rvalue.isFunction()) {
      checkForHosedThisReferences(rvalue, refName.docInfo, refName);
    }

    // Create the new alias node.
    Node nameNode =
        NodeUtil.newName(compiler, alias, grandparent.getFirstChild(), refName.getFullName());
    NodeUtil.copyNameAnnotations(ref.node.getLastChild(), nameNode);

    // BEFORE:
    // ... (x.y = 3);
    //
    // AFTER:
    // var x$y;
    // ... (x$y = 3);

    Node current = grandparent;
    Node currentParent = grandparent.getParent();
    for (;
        !currentParent.isScript() && !currentParent.isBlock();
        current = currentParent, currentParent = currentParent.getParent()) {}

    // Create a stub variable declaration right
    // before the current statement.
    Node stubVar = IR.var(nameNode.cloneTree()).useSourceInfoIfMissingFrom(nameNode);
    currentParent.addChildBefore(stubVar, current);

    parent.replaceChild(ref.node, nameNode);
    compiler.reportChangeToEnclosingScope(nameNode);
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name.
   * This involves flattening the global name (if it's not just a global
   * variable name already), collapsing object literal keys into global
   * variables, declaring stub global variables for properties added later
   * in a local scope.
   *
   * It may seem odd that this function also takes care of declaring stubs
   * for direct children. The ultimate goal of this function is to eliminate
   * the global name entirely (when possible), so that "middlemen" namespaces
   * disappear, and to do that we need to make sure that all the direct children
   * will be collapsed as well.
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
   * @param canCollapseChildNames Whether it's possible to collapse children of
   *     this name. (This is mostly passed for convenience; it's equivalent to
   *     n.canCollapseChildNames()).
   */
  private void updateGlobalNameDeclaration(
      Name n, String alias, boolean canCollapseChildNames) {
    Ref decl = n.getDeclaration();
    if (decl == null) {
      // Some names do not have declarations, because they
      // are only defined in local scopes.
      return;
    }

    switch (decl.node.getParent().getToken()) {
      case ASSIGN:
        updateGlobalNameDeclarationAtAssignNode(
            n, alias, canCollapseChildNames);
        break;
      case VAR:
      case LET:
      case CONST:
        updateGlobalNameDeclarationAtVariableNode(n, canCollapseChildNames);
        break;
      case FUNCTION:
        updateGlobalNameDeclarationAtFunctionNode(n, canCollapseChildNames);
        break;
      case CLASS:
        updateGlobalNameDeclarationAtClassNode(n, canCollapseChildNames);
        break;
      default:
        break;
    }
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name
   * that occurs at an ASSIGN node. See comment for
   * {@link #updateGlobalNameDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name for {@code n} (e.g. "a", "a$b$c")
   */
  private void updateGlobalNameDeclarationAtAssignNode(
      Name n, String alias, boolean canCollapseChildNames) {
    // NOTE: It's important that we don't add additional nodes
    // (e.g. a var node before the exprstmt) because the exprstmt might be
    // the child of an if statement that's not inside a block).

    // All qualified names - even for variables that are initially declared as LETS and CONSTS -
    // are being declared as VAR statements, but this is not incorrect because
    // we are only collapsing for global names.
    Ref ref = n.getDeclaration();
    Node rvalue = ref.node.getNext();
    if (ref.getTwin() != null) {
      updateTwinnedDeclaration(alias, ref.name, ref);
      return;
    }
    Node varNode = new Node(Token.VAR);
    Node varParent = ref.node.getAncestor(3);
    Node grandparent = ref.node.getAncestor(2);
    boolean isObjLit = rvalue.isObjectLit();
    boolean insertedVarNode = false;

    if (isObjLit && canEliminate(n)) {
      // Eliminate the object literal altogether.
      varParent.replaceChild(grandparent, varNode);
      ref.node = null;
      insertedVarNode = true;
      compiler.reportChangeToEnclosingScope(varNode);
    } else if (!n.isSimpleName()) {
      // Create a VAR node to declare the name.
      if (rvalue.isFunction()) {
        checkForHosedThisReferences(rvalue, n.docInfo, n);
      }

      compiler.reportChangeToEnclosingScope(rvalue);
      ref.node.getParent().removeChild(rvalue);

      Node nameNode = NodeUtil.newName(compiler,
          alias, ref.node.getAncestor(2), n.getFullName());

      JSDocInfo info = NodeUtil.getBestJSDocInfo(ref.node.getParent());
      if (ref.node.getLastChild().getBooleanProp(Node.IS_CONSTANT_NAME)
          || (info != null && info.isConstant())) {
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }

      if (info != null) {
        varNode.setJSDocInfo(info);
      }
      varNode.addChildToBack(nameNode);
      nameNode.addChildToFront(rvalue);
      varParent.replaceChild(grandparent, varNode);

      // Update the node ancestry stored in the reference.
      ref.node = nameNode;
      insertedVarNode = true;
      compiler.reportChangeToEnclosingScope(varNode);
    }

    if (canCollapseChildNames) {
      if (isObjLit) {
        declareVariablesForObjLitValues(
            n, alias, rvalue, varNode, varNode.getPrevious(), varParent);
      }

      addStubsForUndeclaredProperties(n, alias, varParent, varNode);
    }

    if (insertedVarNode) {
      if (!varNode.hasChildren()) {
        varParent.removeChild(varNode);
      }
    }
  }

  /**
   * Warns about any references to "this" in the given FUNCTION. The function
   * is getting collapsed, so the references will change.
   */
  private void checkForHosedThisReferences(Node function, JSDocInfo docInfo,
      final Name name) {
    // A function is getting collapsed. Make sure that if it refers to "this",
    // it must be a constructor, interface, record, arrow function, or documented with @this.
    boolean isAllowedToReferenceThis =
        (docInfo != null && (docInfo.isConstructorOrInterface() || docInfo.hasThisType()))
        || function.isArrowFunction();
    if (!isAllowedToReferenceThis) {
      NodeTraversal.traverse(compiler, function.getLastChild(),
          new NodeTraversal.AbstractShallowCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isThis()) {
                compiler.report(
                    JSError.make(n, UNSAFE_THIS, name.getFullName()));
              }
            }
          });
    }
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name that occurs at a VAR
   * node. See comment for {@link #updateGlobalNameDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a")
   */
  private void updateGlobalNameDeclarationAtVariableNode(
      Name n, boolean canCollapseChildNames) {
    if (!canCollapseChildNames) {
      return;
    }

    Ref ref = n.getDeclaration();
    String name = ref.node.getString();
    Node rvalue = ref.node.getFirstChild();
    Node variableNode = ref.node.getParent();
    Node grandparent = variableNode.getParent();

    boolean isObjLit = rvalue.isObjectLit();

    if (isObjLit) {
      declareVariablesForObjLitValues(
          n, name, rvalue, variableNode, variableNode.getPrevious(), grandparent);
    }

    addStubsForUndeclaredProperties(n, name, grandparent, variableNode);

    if (isObjLit && canEliminate(n)) {
      variableNode.removeChild(ref.node);
      compiler.reportChangeToEnclosingScope(variableNode);
      if (!variableNode.hasChildren()) {
        grandparent.removeChild(variableNode);
      }

      // Clear out the object reference, since we've eliminated it from the
      // parse tree.
      ref.node = null;
    }
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name
   * that occurs at a FUNCTION node. See comment for
   * {@link #updateGlobalNameDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a")
   */
  private void updateGlobalNameDeclarationAtFunctionNode(
      Name n, boolean canCollapseChildNames) {
    if (!canCollapseChildNames || !canCollapse(n)) {
      return;
    }

    Ref ref = n.getDeclaration();
    String fnName = ref.node.getString();
    addStubsForUndeclaredProperties(n, fnName, ref.node.getAncestor(2), ref.node.getParent());
  }

  /**
   * Updates the first initialization (a.k.a "declaration") of a global name that occurs at a CLASS
   * node. See comment for {@link #updateGlobalNameDeclaration}.
   *
   * @param n An object representing a global name (e.g. "a")
   */
  private void updateGlobalNameDeclarationAtClassNode(Name n, boolean canCollapseChildNames) {
    if (!canCollapseChildNames || !canCollapse(n)) {
      return;
    }

    Ref ref = n.getDeclaration();
    String className = ref.node.getString();
    addStubsForUndeclaredProperties(
        n, className, ref.node.getAncestor(2), ref.node.getParent());
  }

  /**
   * Declares global variables to serve as aliases for the values in an object literal, optionally
   * removing all of the object literal's keys and values.
   *
   * @param alias The object literal's flattened name (e.g. "a$b$c")
   * @param objlit The OBJLIT node
   * @param varNode The VAR node to which new global variables should be added as children
   * @param nameToAddAfter The child of {@code varNode} after which new variables should be added
   *     (may be null)
   * @param varParent {@code varNode}'s parent
   */
  private void declareVariablesForObjLitValues(
      Name objlitName,
      String alias,
      Node objlit,
      Node varNode,
      Node nameToAddAfter,
      Node varParent) {
    int arbitraryNameCounter = 0;
    boolean discardKeys = !objlitName.shouldKeepKeys();

    for (Node key = objlit.getFirstChild(), nextKey; key != null;
         key = nextKey) {
      Node value = key.getFirstChild();
      nextKey = key.getNext();

      // A computed property, or a get or a set can not be rewritten as a VAR.
      if (key.isGetterDef() || key.isSetterDef() || key.isComputedProp()) {
        continue;
      }

      // We generate arbitrary names for keys that aren't valid JavaScript
      // identifiers, since those keys are never referenced. (If they were,
      // this object literal's child names wouldn't be collapsible.) The only
      // reason that we don't eliminate them entirely is the off chance that
      // their values are expressions that have side effects.
      boolean isJsIdentifier = !key.isNumber() && TokenStream.isJSIdentifier(key.getString());
      String propName = isJsIdentifier ? key.getString() : String.valueOf(++arbitraryNameCounter);

      // If the name cannot be collapsed, skip it.
      String qName = objlitName.getFullName() + '.' + propName;
      Name p = nameMap.get(qName);
      if (p != null && !canCollapse(p)) {
        continue;
      }

      String propAlias = appendPropForAlias(alias, propName);
      Node refNode = null;
      if (discardKeys) {
        objlit.removeChild(key);
        value.detach();
        // Don't report a change here because the objlit has already been removed from the tree.
      } else {
        // Substitute a reference for the value.
        refNode = IR.name(propAlias);
        if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
          refNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }

        key.replaceChild(value, refNode);
        compiler.reportChangeToEnclosingScope(refNode);
      }

      // Declare the collapsed name as a variable with the original value.
      Node nameNode = IR.name(propAlias);
      nameNode.addChildToFront(value);
      if (key.getBooleanProp(Node.IS_CONSTANT_NAME)) {
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      Node newVar = IR.var(nameNode).useSourceInfoIfMissingFromForTree(key);
      if (nameToAddAfter != null) {
        varParent.addChildAfter(newVar, nameToAddAfter);
      } else {
        varParent.addChildBefore(newVar, varNode);
      }
      compiler.reportChangeToEnclosingScope(newVar);
      nameToAddAfter = newVar;

      // Update the global name's node ancestry if it hasn't already been
      // done. (Duplicate keys in an object literal can bring us here twice
      // for the same global name.)
      if (isJsIdentifier && p != null) {
        if (!discardKeys) {
          Ref newAlias =
              p.getDeclaration().cloneAndReclassify(Ref.Type.ALIASING_GET);
          newAlias.node = refNode;
          p.addRef(newAlias);
        }

        p.getDeclaration().node = nameNode;

        if (value.isFunction()) {
          checkForHosedThisReferences(value, key.getJSDocInfo(), p);
        }
      }
    }
  }

  /**
   * Adds global variable "stubs" for any properties of a global name that are only set in a local
   * scope or read but never set.
   *
   * @param n An object representing a global name (e.g. "a", "a.b.c")
   * @param alias The flattened name of the object whose properties we are adding stubs for (e.g.
   *     "a$b$c")
   * @param parent The node to which new global variables should be added as children
   * @param addAfter The child of after which new variables should be added
   */
  private void addStubsForUndeclaredProperties(Name n, String alias, Node parent, Node addAfter) {
    checkState(n.canCollapseUnannotatedChildNames(), n);
    checkArgument(NodeUtil.isStatementBlock(parent), parent);
    checkNotNull(addAfter);
    if (n.props == null) {
      return;
    }
    for (Name p : n.props) {
      if (p.needsToBeStubbed()) {
        String propAlias = appendPropForAlias(alias, p.getBaseName());
        Node nameNode = IR.name(propAlias);
        Node newVar = IR.var(nameNode).useSourceInfoIfMissingFromForTree(addAfter);
        parent.addChildAfter(newVar, addAfter);
        addAfter = newVar;
        compiler.reportChangeToEnclosingScope(newVar);
        // Determine if this is a constant var by checking the first
        // reference to it. Don't check the declaration, as it might be null.
        if (p.getRefs().get(0).node.getLastChild().getBooleanProp(
            Node.IS_CONSTANT_NAME)) {
          nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
          compiler.reportChangeToEnclosingScope(nameNode);
        }
      }
    }
  }

  private String appendPropForAlias(String root, String prop) {
    if (prop.indexOf('$') != -1) {
      // Encode '$' in a property as '$0'. Because '0' cannot be the
      // start of an identifier, this will never conflict with our
      // encoding from '.' -> '$'.
      prop = prop.replace("$", "$0");
    }
    String result = root + '$' + prop;
    int id = 1;
    while (nameMap.containsKey(result)) {
      result = root + '$' + prop + '$' + id;
      id++;
    }
    return result;
  }
}
