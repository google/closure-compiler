/*
 * Copyright 2006 Google Inc.
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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces goog.provide calls, removes goog.require calls, verifies that
 * goog.require has a corresponding goog.provide and some closure specific
 * simplifications.
 *
*
*
*
 */
class ProcessClosurePrimitives extends AbstractPostOrderCallback
    implements CompilerPass {

  static final DiagnosticType NULL_ARGUMENT_ERROR = DiagnosticType.error(
      "JSC_NULL_ARGUMENT_ERROR",
      "method \"{0}\" called without an argument");

  static final DiagnosticType INVALID_ARGUMENT_ERROR = DiagnosticType.error(
      "JSC_INVALID_ARGUMENT_ERROR",
      "method \"{0}\" called with a non-string argument");

  static final DiagnosticType TOO_MANY_ARGUMENTS_ERROR = DiagnosticType.error(
      "JSC_TOO_MANY_ARGUMENTS_ERROR",
      "method \"{0}\" called with more than one argument");

  static final DiagnosticType DUPLICATE_NAMESPACE_ERROR = DiagnosticType.error(
      "JSC_DUPLICATE_NAMESPACE_ERROR",
      "namespace \"{0}\" cannot be provided twice");

  static final DiagnosticType FUNCTION_NAMESPACE_ERROR = DiagnosticType.error(
      "JSC_FUNCTION_NAMESPACE_ERROR",
      "\"{0}\" cannot be both provided and declared as a function");

  static final DiagnosticType MISSING_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_MISSING_PROVIDE_ERROR",
      "required \"{0}\" namespace never provided");

  static final DiagnosticType LATE_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_LATE_PROVIDE_ERROR",
      "required \"{0}\" namespace not provided yet");

  static final DiagnosticType INVALID_PROVIDE_ERROR = DiagnosticType.error(
      "JSC_INVALID_PROVIDE_ERROR",
      "\"{0}\" is not a valid JS property name");

  static final DiagnosticType XMODULE_REQUIRE_ERROR = DiagnosticType.warning(
      "JSC_XMODULE_REQUIRE_ERROR",
      "namespace \"{0}\" provided in module {1} " +
      "but required in module {2}");

  static final DiagnosticType NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR =
      DiagnosticType.error(
          "JSC_NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR",
      "goog.setCssNameMapping only takes an object literal with string values");

  static final DiagnosticType BASE_CLASS_ERROR = DiagnosticType.error(
      "JSC_BASE_CLASS_ERROR",
      "incorrect use of goog.base: {0}");

  /** The root Closure namespace */
  static final String GOOG = "goog";

  private final AbstractCompiler compiler;
  private final List<UnrecognizedRequire> unrecognizedRequires;
  private final Map<String, ProvidedNode> providedNodes;
  private final Set<String> exportedVariables = Sets.newHashSet();
  private final CheckLevel requiresLevel;
  private final boolean rewriteNewDateGoogNow;

  ProcessClosurePrimitives(AbstractCompiler compiler,
                           CheckLevel requiresLevel,
                           boolean rewriteNewDateGoogNow) {
    this.compiler = compiler;
    this.unrecognizedRequires = new ArrayList<UnrecognizedRequire>();
    this.providedNodes = new HashMap<String, ProvidedNode>();
    this.requiresLevel = requiresLevel;
    this.rewriteNewDateGoogNow = rewriteNewDateGoogNow;

    // goog is special-cased because it is provided in Closure's base library.
    providedNodes.put(GOOG, new ProvidedNode(null, null, null));
  }

  Set<String> getExportedVariableNames() {
    return exportedVariables;
  }

  /**
   * {@inheritDoc}
   */
  public void process(Node externs, Node root) {
    new NodeTraversal(compiler, this).traverse(root);

    for (ProvidedNode pn : providedNodes.values()) {
      if (pn != null) {
        pn.maybeRemove();
      }
    }

    if (requiresLevel.isOn()) {
      for (UnrecognizedRequire r : unrecognizedRequires) {
        DiagnosticType error;
        if (providedNodes.get(r.namespace) != null) {
          // The namespace ended up getting provided after it was required.
          error = LATE_PROVIDE_ERROR;
        } else {
          error = MISSING_PROVIDE_ERROR;
        }

        compiler.report(JSError.make(
            r.inputName, r.requireNode, requiresLevel, error, r.namespace));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.CALL:
        boolean isExpr = parent.getType() == Token.EXPR_RESULT;
        Node left = n.getFirstChild();
        if (left.getType() == Token.GETPROP) {
          Node name = left.getFirstChild();
          if (name.getType() == Token.NAME &&
              GOOG.equals(name.getString())) {
            // For the sake of simplicity, we report code changes
            // when we see a provides/requires, and don't worry about
            // reporting the change when we actually do the replacement.
            String methodName = name.getNext().getString();
            if ("base".equals(methodName)) {
              processBaseClassCall(t, n);
            } else if (!isExpr) {
              // All other methods must be called in an EXPR.
              break;
            } else if ("require".equals(methodName)) {
              Node arg = left.getNext();
              if (verifyArgument(t, left, arg)) {
                String ns = arg.getString();
                ProvidedNode provided = providedNodes.get(ns);
                if (provided == null) {
                  unrecognizedRequires.add(
                      new UnrecognizedRequire(n, ns, t.getSourceName()));
                } else {
                  JSModule module = t.getModule();
                  if (module != provided.module /* covers null case */ &&
                      !compiler.getModuleGraph().dependsOn(module,
                          provided.module)) {
                    compiler.report(
                        JSError.make(t, n, XMODULE_REQUIRE_ERROR, ns,
                            provided.module.getName(),
                            module.getName()));
                  }
                }

                // Requires should be removed before runtime.  The one
                // exception is if the type has not been provided yet and
                // errors for broken requires are turned off, in which case,
                // we will be doing a later pass that may error, so we can
                // leave this here this time and let it error next time if it
                // is still not provided.
                if (provided != null || requiresLevel.isOn()) {
                  parent.getParent().removeChild(parent);
                  compiler.reportCodeChange();
                }
              }
            } else if ("provide".equals(methodName)) {
              Node arg = left.getNext();
              if (verifyProvide(t, left, arg)) {
                String ns = arg.getString();
                if (providedNodes.get(ns) != null) {
                  compiler.report(
                      JSError.make(t, n, DUPLICATE_NAMESPACE_ERROR, ns));
                } else if (!providedNodes.containsKey(ns)) {
                  replaceProvide(t, parent, parent.getParent(), ns);
                } else {
                  // Namespace was already inserted for a sub-namespace.
                  parent.getParent().removeChild(parent);
                  providedNodes.put(ns,
                      new ProvidedNode(null, null, t.getModule()));
                }
                compiler.reportCodeChange();
              }
            } else if ("exportSymbol".equals(methodName)) {
              Node arg = left.getNext();
              if (arg.getType() == Token.STRING) {
                int dot = arg.getString().indexOf('.');
                if (dot == -1) {
                  exportedVariables.add(arg.getString());
                } else {
                  exportedVariables.add(arg.getString().substring(0, dot));
                }
              }
            } else if ("addDependency".equals(methodName)) {
              CodingConvention convention = compiler.getCodingConvention();
              List<String> typeDecls =
                  convention.identifyTypeDeclarationCall(n);
              if (typeDecls != null) {
                for (String typeDecl : typeDecls) {
                  compiler.getTypeRegistry().forwardDeclareType(typeDecl);
                }
              }

              // We can't modify parent, so just create a node that will
              // get compiled out.
              parent.replaceChild(n, Node.newNumber(0));
              compiler.reportCodeChange();
            } else if ("setCssNameMapping".equals(methodName)) {
              processSetCssNameMapping(t, n, parent);
            }
          }
        }
        break;
      case Token.ASSIGN:
        // If this is an assignment to a provided name, remove the provided
        // object.
        if (t.inGlobalScope() &&
            parent.getType() == Token.EXPR_RESULT) {
          Node nameNode = n.getFirstChild();
          String name = nameNode.getQualifiedName();
          if (name != null) {
            if (parent.getBooleanProp(Node.IS_NAMESPACE)) {
              processProvideFromPreviousPass(t, name, parent);
            } else {
              ProvidedNode pn = providedNodes.get(name);
              if (pn != null) {
                pn.addCandidate(parent, parent.getParent());
              }
            }
          }
        }
        break;
      case Token.NAME:
        // If this is a declaration of a provided variable, remove the provided
        // object.
        if (t.inGlobalScope() &&
            parent.getType() == Token.VAR) {
          String name = n.getString();
          if (parent.getBooleanProp(Node.IS_NAMESPACE)) {
            processProvideFromPreviousPass(t, name, parent);
          } else {
            ProvidedNode pn = providedNodes.get(name);
            if (pn != null) {
              pn.addCandidate(parent, parent.getParent());
            }
          }
        }
        break;
      case Token.FUNCTION:
        // If this is a declaration of a provided named function, this is an
        // error. Hosited functions will explode if the're provided.
        if (t.inGlobalScope() &&
            !NodeUtil.isFunctionAnonymous(n)) {
          String name = n.getFirstChild().getString();
          ProvidedNode pn = providedNodes.get(name);
          if (pn != null) {
            compiler.report(JSError.make(t, n, FUNCTION_NAMESPACE_ERROR, name));
          }
        }
        break;

      case Token.NEW:
        trySimplifyNewDate(t, n, parent);
        break;

      case Token.GETPROP:
        if (n.getFirstChild().getType() == Token.NAME &&
            parent.getType() != Token.CALL &&
            parent.getType() != Token.ASSIGN &&
            "goog.base".equals(n.getQualifiedName())) {
          reportBadBaseClassUse(t, n, "May only be called directly.");
        }
        break;
    }
  }

  /**
   * Processes the base class call.
   */
  private void processBaseClassCall(NodeTraversal t, Node n) {
    // Two things must hold for every goog.base call:
    // 1) We must be calling it on "this".
    // 2) We must be calling it on a prototype method of the same name as
    //    the one we're in, OR we must be calling it from a constructor.
    // If both of those things are true, then we can rewrite:
    // <pre>
    // function Foo() {
    //   goog.base(this);
    // }
    // goog.inherits(Foo, BaseFoo);
    // Foo.prototype.bar = function() {
    //   goog.base(this, 'bar', 1);
    // };
    // </pre>
    // as the easy-to-optimize:
    // <pre>
    // function Foo() {
    //   BaseFoo.call(this);
    // }
    // goog.inherits(Foo, BaseFoo);
    // Foo.prototype.bar = function() {
    //   Foo.superClass_.bar.call(this, 1);
    // };
    //
    // Most of the logic here is just to make sure the AST's
    // structure is what we expect it to be.

    Node callee = n.getFirstChild();
    Node thisArg = callee.getNext();
    if (thisArg == null || thisArg.getType() != Token.THIS) {
      reportBadBaseClassUse(t, n, "First argument must be 'this'.");
      return;
    }

    Node enclosingFnNameNode = getEnclosingDeclNameNode(t);
    if (enclosingFnNameNode == null) {
      reportBadBaseClassUse(t, n, "Could not find enclosing method.");
      return;
    }

    String enclosingQname = enclosingFnNameNode.getQualifiedName();
    if (enclosingQname.indexOf(".prototype.") == -1) {
      // Handle constructors.
      Node enclosingParent = enclosingFnNameNode.getParent();
      Node maybeInheritsExpr = (enclosingParent.getType() == Token.ASSIGN ?
          enclosingParent.getParent() : enclosingParent).getNext();
      Node baseClassNode = null;
      if (maybeInheritsExpr != null &&
          maybeInheritsExpr.getType() == Token.EXPR_RESULT &&
          maybeInheritsExpr.getFirstChild().getType() == Token.CALL) {
        Node callNode = maybeInheritsExpr.getFirstChild();
        if ("goog.inherits".equals(
                callNode.getFirstChild().getQualifiedName()) &&
            callNode.getLastChild().isQualifiedName()) {
          baseClassNode = callNode.getLastChild();
        }
      }

      if (baseClassNode == null) {
        reportBadBaseClassUse(
            t, n, "Could not find goog.inherits for base class");
        return;
      }

      // We're good to go.
      n.replaceChild(
          callee,
          NodeUtil.newQualifiedNameNode(
            String.format("%s.call", baseClassNode.getQualifiedName()),
            callee, "goog.base"));
      compiler.reportCodeChange();
    } else {
      // Handle methods.
      Node methodNameNode = thisArg.getNext();
      if (methodNameNode == null || methodNameNode.getType() != Token.STRING) {
        reportBadBaseClassUse(t, n, "Second argument must name a method.");
        return;
      }

      String methodName = methodNameNode.getString();
      String ending = ".prototype." + methodName;
      if (enclosingQname == null ||
          !enclosingQname.endsWith(ending)) {
        reportBadBaseClassUse(
            t, n, "Enclosing method does not match " + methodName);
        return;
      }

      // We're good to go.
      Node className =
          enclosingFnNameNode.getFirstChild().getFirstChild();
      n.replaceChild(
          callee,
          NodeUtil.newQualifiedNameNode(
            String.format("%s.superClass_.%s.call",
                className.getQualifiedName(), methodName),
            callee, "goog.base"));
      n.removeChild(methodNameNode);
      compiler.reportCodeChange();
    }
  }

  /**
   * Returns the qualified name node of the function whose scope we're in,
   * or null if it cannot be found.
   */
  private Node getEnclosingDeclNameNode(NodeTraversal t) {
    Node scopeRoot = t.getScopeRoot();
    if (NodeUtil.isFunctionDeclaration(scopeRoot)) {
      // function x() {...}
      return scopeRoot.getFirstChild();
    } else {
      Node parent = scopeRoot.getParent();
      if (parent != null) {
        if (parent.getType() == Token.ASSIGN ||
            parent.getLastChild() == scopeRoot &&
            parent.getFirstChild().isQualifiedName()) {
          // x.y.z = function() {...};
          return parent.getFirstChild();
        } else if (parent.getType() == Token.NAME) {
          // var x = function() {...};
          return parent;
        }
      }
    }

    return null;
  }

  /** Reports an incorrect use of super-method calling. */
  private void reportBadBaseClassUse(
      NodeTraversal t, Node n, String extraMessage) {
    compiler.report(JSError.make(t, n, BASE_CLASS_ERROR, extraMessage));
  }

  /**
   * Processes the output of processed-provide from a previous pass.  This will
   * update our data structures in the same manner as if the provide had been
   * processed in this pass.
   */
  private void processProvideFromPreviousPass(
      NodeTraversal t, String name, Node parent) {
    if (!providedNodes.containsKey(name)) {
      // Record this provide created on a previous pass.
      providedNodes.put(name, new ProvidedNode(
          parent, parent.getParent(), t.getModule()));
      // Make sure it has the proper prefixes.
      maybeProvidePrefixes(parent, parent.getParent(), name);
    } else {
      // Remove this provide if it came from a previous pass since we have an
      // replacement already.
      if (isNamespacePlaceholder(parent)) {
        parent.getParent().removeChild(parent);
      }
    }
  }

  /**
   * Processes a call to goog.setCssNameMapping(). Either the argument to
   * goog.setCssNameMapping() is valid, in which case it will be used to create
   * a CssRenamingMap for the compiler of this CompilerPass, or it is invalid
   * and a JSCompiler error will be reported.
   * @see #visit(NodeTraversal, Node, Node)
   */
  private void processSetCssNameMapping(NodeTraversal t, Node n, Node parent) {
    Node left = n.getFirstChild();
    Node arg = left.getNext();
    if (verifyArgument(t, left, arg, Token.OBJECTLIT)) {
      // Translate OBJECTLIT into SubstitutionMap. All keys and
      // values must be strings, or an error will be thrown.
      final Map<String,String> cssNames = Maps.newHashMap();
      JSError error = null;
      for (Node key = arg.getFirstChild(); key != null;
          key = key.getNext().getNext()) {
        Node value = key.getNext();
        if (key.getType() != Token.STRING
            || value == null
            || value.getType() != Token.STRING) {
          error = JSError.make(t, n,
              NON_STRING_PASSED_TO_SET_CSS_NAME_MAPPING_ERROR);
        }
        if (error != null) {
          compiler.report(error);
          break;
        }
        cssNames.put(key.getString(), value.getString());
      }

      // If there were no errors, create a CssRenamingMap from cssNames, update
      // the compiler to use it and remove the call to goog.setCssNameMapping().
      if (error == null) {
        CssRenamingMap cssRenamingMap = new CssRenamingMap() {
          public String get(String value) {
            if (cssNames.containsKey(value)) {
              return cssNames.get(value);
            } else {
              return value;
            }
          }
        };
        compiler.setCssRenamingMap(cssRenamingMap);
        parent.getParent().removeChild(parent);
        compiler.reportCodeChange();
      }
    }
  }

  /**
   * Try to simplify "new Date(goog.now())" to "new Date()".
   */
  private void trySimplifyNewDate(NodeTraversal t, Node n, Node parent) {
    if (!rewriteNewDateGoogNow) {
      return;
    }
    Preconditions.checkArgument(n.getType() == Token.NEW);
    Node date = n.getFirstChild();
    if (!NodeUtil.isName(date) || !"Date".equals(date.getString())) {
      return;
    }
    Node callGoogNow = date.getNext();
    if (callGoogNow == null || !NodeUtil.isCall(callGoogNow) ||
        callGoogNow.getNext() != null) {
      return;
    }
    Node googNow = callGoogNow.getFirstChild();
    String googNowQName = googNow.getQualifiedName();
    if (googNowQName == null || !"goog.now".equals(googNowQName)
        || googNow.getNext() != null) {
      return;
    }
    n.removeChild(callGoogNow);
    compiler.reportCodeChange();
  }

  /**
   * Verifies that a provide method call has exactly one argument,
   * and that it's a string literal and that the contents of the string are
   * valid JS tokens. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyProvide(NodeTraversal t, Node methodName, Node arg) {
    if (!verifyArgument(t, methodName, arg)) {
      return false;
    }

    for (String part : arg.getString().split("\\.")) {
      if (!NodeUtil.isValidPropertyName(part)) {
        compiler.report(JSError.make(t, arg, INVALID_PROVIDE_ERROR, part));
        return false;
      }
    }
    return true;
  }

  /**
   * Verifies that a method call has exactly one argument, and that it's a
   * string literal. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyArgument(NodeTraversal t, Node methodName, Node arg) {
    return verifyArgument(t, methodName, arg, Token.STRING);
  }

  /**
   * Verifies that a method call has exactly one argument, and that it is of the
   * desired type. Reports a compile error if it doesn't.
   *
   * @return Whether the argument checked out okay
   */
  private boolean verifyArgument(NodeTraversal t, Node methodName, Node arg,
      int desiredType) {
    DiagnosticType diagnostic = null;
    if (arg == null) {
      diagnostic = NULL_ARGUMENT_ERROR;
    } else if (arg.getType() != desiredType) {
      diagnostic = INVALID_ARGUMENT_ERROR;
    } else if (arg.getNext() != null) {
      diagnostic = TOO_MANY_ARGUMENTS_ERROR;
    }
    if (diagnostic != null) {
      compiler.report(
          JSError.make(t, methodName,
                       diagnostic, methodName.getQualifiedName()));
      return false;
    }
    return true;
  }

  /**
   * Replaces a goog.provide call with one or more variable assignments.
   *
   * @param t The current traversal.
   * @param node The EXPR_RESULT node to be replaced.
   * @param parent The parent of {@code node}.
   * @param ns The namespace to provide.
   */
  private void replaceProvide(
      NodeTraversal t, Node node, Node parent, String ns) {
    Node newNode;
    if (ns.indexOf('.') == -1) {
      newNode = makeVarDeclNode(ns, node);
      parent.replaceChild(node, newNode);
    } else {
      newNode = makeAssignmentExprNode(ns, node);
      parent.replaceChild(node, newNode);
      maybeProvidePrefixes(newNode, parent, ns);
    }
    providedNodes.put(ns, new ProvidedNode(newNode, parent, t.getModule()));
  }

  /**
   * Provides prefix namespaces if they haven't already been defined.
   *
   * @param node Node to insert definitions before
   * @param parent Parent of {@code node}
   * @param ns The namespace whose prefixes may need to be provided
   */
  private void maybeProvidePrefixes(Node node, Node parent, String ns) {
    int pos = ns.lastIndexOf('.');
    Node nodeToAddBefore = node;
    while (pos != -1) {
      String prefixNs = ns.substring(0, pos);
      pos = prefixNs.lastIndexOf('.');
      if (providedNodes.containsKey(prefixNs)) {
        break;
      } else {
        // Use a null value in this map to indicate that the namespace has
        // been declared implicitly and is not removable.
        providedNodes.put(prefixNs, null);
        Node newNode = (pos == -1
                        ? makeVarDeclNode(prefixNs, node)
                        : makeAssignmentExprNode(prefixNs, node));
        parent.addChildBefore(newNode, nodeToAddBefore);
        nodeToAddBefore = newNode;
      }
    }
  }

  /**
   * Creates a simple namespace variable declaration
   * (e.g. <code>var foo = {};</code>).
   *
   * @param namespace A simple namespace (must be a valid js identifier)
   * @param sourceNode The node to get source information from.
   */
  private Node makeVarDeclNode(String namespace, Node sourceNode) {
    Node name = Node.newString(Token.NAME, namespace);
    name.addChildToFront(new Node(Token.OBJECTLIT));

    Node decl = new Node(Token.VAR, name);
    decl.putBooleanProp(Node.IS_NAMESPACE, true);

    // TODO(nicksantos): ew ew ew. Create a mutator package.
    if (compiler.getCodingConvention().isConstant(namespace)) {
      name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }

    Preconditions.checkState(isNamespacePlaceholder(decl));
    decl.copyInformationFromForTree(sourceNode);
    return decl;
  }

  /**
   * Creates a dotted namespace assignment expression
   * (e.g. <code>foo.bar = {};</code>).
   *
   * @param namespace A dotted namespace
   * @param node A node from which to copy source info.
   */
  private Node makeAssignmentExprNode(String namespace, Node node) {
    Node decl = new Node(Token.EXPR_RESULT,
        new Node(Token.ASSIGN,
            NodeUtil.newQualifiedNameNode(namespace, node, namespace),
            new Node(Token.OBJECTLIT)));
    decl.putBooleanProp(Node.IS_NAMESPACE, true);
    Preconditions.checkState(isNamespacePlaceholder(decl));
    decl.copyInformationFromForTree(node);
    return decl;
  }

  // -------------------------------------------------------------------------

  /**
   * Information required to replace a provided node later in the traversal.
   */
  private class ProvidedNode {
    private final Node providedNode;
    private final Node providedParent;
    private final JSModule module;
    private Node replacementCandidate;
    private Node replacementCandidateParent;

    ProvidedNode(Node node, Node parent, JSModule module) {
      Preconditions.checkArgument((node == null) ||
                                  NodeUtil.isVar(node) ||
                                  NodeUtil.isExpressionNode(node));
      Preconditions.checkArgument((node == null) == (parent == null));
      this.providedNode = node;
      this.providedParent = parent;
      this.replacementCandidate = null;
      this.replacementCandidateParent = null;
      this.module = module;
    }

    /**
     * Record function declaration, variable declaration or assignment that
     * refers to the same name as the provide statement.  Give preference to
     * declarations; if no declation exists record a reference to an
     * assignment so it repurposed later.
     */
    void addCandidate(Node node, Node parent) {
      Preconditions.checkArgument(NodeUtil.isExpressionNode(node) || // assign
                                  NodeUtil.isFunction(node) ||
                                  NodeUtil.isVar(node));
      Preconditions.checkArgument(providedNode != node);

      if ((replacementCandidate == null) || !NodeUtil.isExpressionNode(node)) {
        replacementCandidate = node;
        replacementCandidateParent = parent;
      }
    }

    /**
     * Remove the definition added to replace the provide statement if a
     * duplicate definition exists.  If no suitable definition exists, but
     * an assignment is found, convert the assignment into a variable
     * definition.
     */
    void maybeRemove() {
      if ((providedNode != null)
          && (replacementCandidate != null)
          && isNamespacePlaceholder(providedNode)) {
        providedParent.removeChild(providedNode);
        compiler.reportCodeChange();

        if (NodeUtil.isExpressionNode(replacementCandidate)) {
          replacementCandidate.putBooleanProp(Node.IS_NAMESPACE, true);
          Node assignNode = replacementCandidate.getFirstChild();
          Node nameNode = assignNode.getFirstChild();
          if (nameNode.getType() == Token.NAME) {
            // Need to convert this assign to a var declaration.
            Node valueNode = nameNode.getNext();
            assignNode.removeChild(nameNode);
            assignNode.removeChild(valueNode);
            nameNode.addChildToFront(valueNode);
            Node varNode = new Node(Token.VAR, nameNode);
            varNode.copyInformationFrom(replacementCandidate);
            replacementCandidateParent.replaceChild(replacementCandidate,
                                                    varNode);
            nameNode.setJSDocInfo(assignNode.getJSDocInfo());
            compiler.reportCodeChange();
          }
        }
      }
    }
  }

  /**
   * @return Whether the node is namespace placeholder.
   */
  private static boolean isNamespacePlaceholder(Node n) {
    if (!n.getBooleanProp(Node.IS_NAMESPACE)) {
      return false;
    }

    Node value = null;
    if (n.getType() == Token.EXPR_RESULT) {
      Node assign = n.getFirstChild();
      value = assign.getLastChild();
    } else if (n.getType() == Token.VAR) {
      Node name = n.getFirstChild();
      value = name.getFirstChild();
    }

    return value != null
      && value.getType() == Token.OBJECTLIT
      && !value.hasChildren();
  }

  // -------------------------------------------------------------------------

  /**
   * Information required to create a {@code MISSING_PROVIDE_ERROR} warning.
   */
  private class UnrecognizedRequire {
    final Node requireNode;
    final String namespace;
    final String inputName;

    UnrecognizedRequire(Node requireNode, String namespace, String inputName) {
      this.requireNode = requireNode;
      this.namespace = namespace;
      this.inputName = inputName;
    }
  }
}
