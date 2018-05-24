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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Compiler pass for Chrome-specific needs. It handles the following Chrome JS features:
 *
 * <ul>
 *   <li>namespace declaration using {@code cr.define()},
 *   <li>unquoted property declaration using {@code {cr|Object}.defineProperty()}.
 * </ul>
 *
 * <p>For the details, see tests inside ChromePassTest.java.
 */
public class ChromePass extends AbstractPostOrderCallback implements CompilerPass {
  final AbstractCompiler compiler;

  private final Set<String> createdObjects;

  private static final String CR_DEFINE = "cr.define";
  private static final String CR_EXPORT_PATH = "cr.exportPath";
  private static final String OBJECT_DEFINE_PROPERTY = "Object.defineProperty";
  private static final String CR_DEFINE_PROPERTY = "cr.defineProperty";
  private static final String CR_MAKE_PUBLIC = "cr.makePublic";

  private static final String CR_DEFINE_COMMON_EXPLANATION =
      "It should be called like this:"
          + " cr.define('name.space', function() '{ ... return {Export: Internal}; }');";

  static final DiagnosticType CR_DEFINE_WRONG_NUMBER_OF_ARGUMENTS =
      DiagnosticType.error(
          "JSC_CR_DEFINE_WRONG_NUMBER_OF_ARGUMENTS",
          "cr.define() should have exactly 2 arguments. " + CR_DEFINE_COMMON_EXPLANATION);

  static final DiagnosticType CR_EXPORT_PATH_TOO_FEW_ARGUMENTS =
      DiagnosticType.error(
          "JSC_CR_EXPORT_PATH_TOO_FEW_ARGUMENTS",
          "cr.exportPath() should have at least 1 argument: path name.");

  static final DiagnosticType CR_DEFINE_INVALID_FIRST_ARGUMENT =
      DiagnosticType.error(
          "JSC_CR_DEFINE_INVALID_FIRST_ARGUMENT",
          "Invalid first argument for cr.define(). " + CR_DEFINE_COMMON_EXPLANATION);

  static final DiagnosticType CR_DEFINE_INVALID_SECOND_ARGUMENT =
      DiagnosticType.error(
          "JSC_CR_DEFINE_INVALID_SECOND_ARGUMENT",
          "Invalid second argument for cr.define(). " + CR_DEFINE_COMMON_EXPLANATION);

  static final DiagnosticType CR_DEFINE_INVALID_RETURN_IN_FUNCTION =
      DiagnosticType.error(
          "JSC_CR_DEFINE_INVALID_RETURN_IN_SECOND_ARGUMENT",
          "Function passed as second argument of cr.define() should return the"
              + " dictionary in its last statement. "
              + CR_DEFINE_COMMON_EXPLANATION);

  static final DiagnosticType CR_DEFINE_PROPERTY_INVALID_PROPERTY_KIND =
      DiagnosticType.error(
          "JSC_CR_DEFINE_PROPERTY_INVALID_PROPERTY_KIND",
          "Invalid cr.PropertyKind passed to cr.defineProperty(): expected ATTR,"
              + " BOOL_ATTR or JS, found \"{0}\".");

  static final DiagnosticType CR_MAKE_PUBLIC_HAS_NO_JSDOC =
      DiagnosticType.error(
          "JSC_CR_MAKE_PUBLIC_HAS_NO_JSDOC",
          "Private method exported by cr.makePublic() has no JSDoc.");

  static final DiagnosticType CR_MAKE_PUBLIC_MISSED_DECLARATION =
      DiagnosticType.error(
          "JSC_CR_MAKE_PUBLIC_MISSED_DECLARATION",
          "Method \"{1}_\" exported by cr.makePublic() on \"{0}\" has no declaration.");

  static final DiagnosticType CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT =
      DiagnosticType.error(
          "JSC_CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT",
          "Invalid second argument passed to cr.makePublic(): should be array of strings.");

  public ChromePass(AbstractCompiler compiler) {
    this.compiler = compiler;
    // The global variable "cr" is declared in ui/webui/resources/js/cr.js.
    this.createdObjects = new HashSet<>(Arrays.asList("cr"));
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node node, Node parent) {
    if (node.isCall()) {
      Node callee = node.getFirstChild();
      if (callee.matchesQualifiedName(CR_DEFINE)) {
        visitNamespaceDefinition(node, parent);
      } else if (callee.matchesQualifiedName(CR_EXPORT_PATH)) {
        visitExportPath(node, parent);
      } else if (callee.matchesQualifiedName(OBJECT_DEFINE_PROPERTY)
          || callee.matchesQualifiedName(CR_DEFINE_PROPERTY)) {
        visitPropertyDefinition(node, parent);
      } else if (callee.matchesQualifiedName(CR_MAKE_PUBLIC)) {
        visitMakePublic(node, parent);
      }
    }
  }

  private void visitPropertyDefinition(Node call, Node parent) {
    Node callee = call.getFirstChild();
    String target = call.getSecondChild().getQualifiedName();
    if (callee.matchesQualifiedName(CR_DEFINE_PROPERTY) && !target.endsWith(".prototype")) {
      target += ".prototype";
    }

    Node property = call.getChildAtIndex(2);

    Node getPropNode =
        NodeUtil.newQName(compiler, target + "." + property.getString()).srcrefTree(call);

    boolean isCrDefinePropertyCall = callee.matchesQualifiedName(CR_DEFINE_PROPERTY);
    if (isCrDefinePropertyCall) {
      // The 3rd argument (PropertyKind) is actually used at runtime, so it takes precedence.
      Node propertyType = getTypeByCrPropertyKind(call.getChildAtIndex(3));
      if (propertyType != null) {
        setJsDocWithType(getPropNode, propertyType);
      } else {
        // Otherwise, if there's a @type above the cr.defineProperty() call, move it to the getter.
        JSDocInfo sourceJsDocInfo = call.getJSDocInfo();
        if (sourceJsDocInfo != null && sourceJsDocInfo.hasType()) {
          getPropNode.setJSDocInfo(sourceJsDocInfo);
        } else {
          // Else, just give it a @type {?}.
          setJsDocWithType(getPropNode, new Node(Token.QMARK));
        }
      }
      // Nuke the JsDoc info above cr.defineProperty() calls so that the CheckJSDocInfo pass won't
      // report any warnings.
      call.setJSDocInfo(null);
    } else {
      setJsDocWithType(getPropNode, new Node(Token.QMARK));
    }

    Node definitionNode = IR.exprResult(getPropNode).srcref(parent);

    parent.getParent().addChildAfter(definitionNode, parent);
    compiler.reportChangeToEnclosingScope(isCrDefinePropertyCall ? call : getPropNode);
  }

  @Nullable
  private Node getTypeByCrPropertyKind(@Nullable Node propertyKind) {
    if (propertyKind == null || propertyKind.matchesQualifiedName("cr.PropertyKind.JS")) {
      // This is valid, it just doesn't tell us much about the type.
      return null;
    }
    if (propertyKind.matchesQualifiedName("cr.PropertyKind.ATTR")) {
      return IR.string("string");
    }
    if (propertyKind.matchesQualifiedName("cr.PropertyKind.BOOL_ATTR")) {
      return IR.string("boolean");
    }
    compiler.report(
        JSError.make(
            propertyKind,
            CR_DEFINE_PROPERTY_INVALID_PROPERTY_KIND,
            propertyKind.getQualifiedName()));
    return null;
  }

  private static void setJsDocWithType(Node target, Node type) {
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordType(new JSTypeExpression(type, "<ChromePass.java>"));
    target.setJSDocInfo(builder.build());
  }

  private void visitMakePublic(Node call, Node exprResult) {
    Node scope = exprResult.getParent();
    String className = call.getSecondChild().getQualifiedName();
    String prototype = className + ".prototype";
    Node methods = call.getChildAtIndex(2);

    if (methods == null || !methods.isArrayLit()) {
      compiler.report(JSError.make(exprResult, CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT));
      return;
    }

    Set<String> methodNames = new HashSet<>();
    for (Node methodName : methods.children()) {
      if (!methodName.isString()) {
        compiler.report(JSError.make(methodName, CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT));
        return;
      }
      methodNames.add(methodName.getString());
    }

    for (Node child : scope.children()) {
      if (isAssignmentToPrototype(child, prototype)) {
        Node objectLit = child.getFirstChild().getSecondChild();
        for (Node stringKey : objectLit.children()) {
          String field = stringKey.getString();
          maybeAddPublicDeclaration(field, methodNames, className, stringKey, scope, exprResult);
        }
      } else if (isAssignmentToPrototypeMethod(child, prototype)) {
        Node assignNode = child.getFirstChild();
        String qualifiedName = assignNode.getFirstChild().getQualifiedName();
        String field = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        maybeAddPublicDeclaration(field, methodNames, className, assignNode, scope, exprResult);
      } else if (isDummyPrototypeMethodDeclaration(child, prototype)) {
        String qualifiedName = child.getFirstChild().getQualifiedName();
        String field = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        maybeAddPublicDeclaration(
            field, methodNames, className, child.getFirstChild(), scope, exprResult);
      }
    }

    for (String missedDeclaration : methodNames) {
      compiler.report(
          JSError.make(
              exprResult, CR_MAKE_PUBLIC_MISSED_DECLARATION, className, missedDeclaration));
    }
  }

  private static boolean isAssignmentToPrototype(Node node, String prototype) {
    Node assignNode;
    return node.isExprResult()
        && (assignNode = node.getFirstChild()).isAssign()
        && assignNode.getFirstChild().matchesQualifiedName(prototype);
  }

  private static boolean isAssignmentToPrototypeMethod(Node node, String prototype) {
    Node assignNode;
    return node.isExprResult()
        && (assignNode = node.getFirstChild()).isAssign()
        && assignNode.getFirstChild().getQualifiedName().startsWith(prototype + ".");
  }

  private static boolean isDummyPrototypeMethodDeclaration(Node node, String prototype) {
    Node getPropNode;
    return node.isExprResult()
        && (getPropNode = node.getFirstChild()).isGetProp()
        && getPropNode.getQualifiedName().startsWith(prototype + ".");
  }

  private void maybeAddPublicDeclaration(
      String field,
      Set<String> publicAPIStrings,
      String className,
      Node jsDocSourceNode,
      Node scope,
      Node exprResult) {
    if (field.endsWith("_")) {
      String publicName = field.substring(0, field.length() - 1);
      if (publicAPIStrings.contains(publicName)) {
        Node methodDeclaration = NodeUtil.newQName(compiler, className + "." + publicName);
        if (jsDocSourceNode.getJSDocInfo() != null) {
          methodDeclaration.setJSDocInfo(jsDocSourceNode.getJSDocInfo());
          Node publicDeclaration = IR.exprResult(methodDeclaration).srcrefTree(exprResult);
          scope.addChildBefore(publicDeclaration, exprResult);
          compiler.reportChangeToEnclosingScope(publicDeclaration);
        } else {
          compiler.report(JSError.make(jsDocSourceNode, CR_MAKE_PUBLIC_HAS_NO_JSDOC));
        }
        publicAPIStrings.remove(publicName);
      }
    }
  }

  private void visitExportPath(Node crExportPathNode, Node parent) {
    if (crExportPathNode.getChildCount() < 2) {
      compiler.report(JSError.make(crExportPathNode, CR_EXPORT_PATH_TOO_FEW_ARGUMENTS));
      return;
    }

    Node pathArg = crExportPathNode.getSecondChild();
    if (pathArg.isString()) {
      // TODO(dbeam): support cr.exportPath('ns').value.
      createAndInsertObjectsForQualifiedName(parent, pathArg.getString());
    }
  }

  private void createAndInsertObjectsForQualifiedName(Node scriptChild, String namespace) {
    List<Node> objectsForQualifiedName = createObjectsForQualifiedName(namespace);
    for (Node n : objectsForQualifiedName) {
      scriptChild.getParent().addChildBefore(n, scriptChild);
    }
    if (!objectsForQualifiedName.isEmpty()) {
      compiler.reportChangeToEnclosingScope(scriptChild);
    }
  }

  private void visitNamespaceDefinition(Node crDefineCallNode, Node parent) {
    if (crDefineCallNode.getChildCount() != 3) {
      compiler.report(JSError.make(crDefineCallNode, CR_DEFINE_WRONG_NUMBER_OF_ARGUMENTS));
    }

    Node namespaceArg = crDefineCallNode.getSecondChild();
    Node function = crDefineCallNode.getChildAtIndex(2);

    if (!namespaceArg.isString()) {
      compiler.report(JSError.make(namespaceArg, CR_DEFINE_INVALID_FIRST_ARGUMENT));
      return;
    }

    // TODO(vitalyp): Check namespace name for validity here. It should be a valid chain of
    // identifiers.
    String namespace = namespaceArg.getString();

    createAndInsertObjectsForQualifiedName(parent, namespace);

    if (!function.isFunction()) {
      compiler.report(JSError.make(namespaceArg, CR_DEFINE_INVALID_SECOND_ARGUMENT));
      return;
    }

    Node returnNode;
    Node objectLit;
    Node functionBlock = function.getLastChild();
    if ((returnNode = functionBlock.getLastChild()) == null
        || !returnNode.isReturn()
        || (objectLit = returnNode.getFirstChild()) == null
        || !objectLit.isObjectLit()) {
      compiler.report(JSError.make(namespaceArg, CR_DEFINE_INVALID_RETURN_IN_FUNCTION));
      return;
    }

    Map<String, String> exports = objectLitToMap(objectLit);

    NodeTraversal.traverse(
        compiler,
        functionBlock,
        new RenameInternalsToExternalsCallback(namespace, exports, functionBlock));
  }

  private static Map<String, String> objectLitToMap(Node objectLit) {
    Map<String, String> res = new HashMap<>();

    for (Node keyNode : objectLit.children()) {
      String key = keyNode.getString();

      Node valueNode = keyNode.getFirstChild();
      if (valueNode.isName()) {
        String value = keyNode.getFirstChild().getString();
        res.put(value, key);
      }
    }

    return res;
  }

  /**
   * For a string "a.b.c" produce the following JS IR:
   *
   * <p>
   *
   * <pre>
   * var a = a || {};
   * a.b = a.b || {};
   * a.b.c = a.b.c || {};</pre>
   */
  private List<Node> createObjectsForQualifiedName(String namespace) {
    List<Node> objects = new ArrayList<>();
    String[] parts = namespace.split("\\.");

    createObjectIfNew(objects, parts[0], true);

    if (parts.length >= 2) {
      StringBuilder currPrefix = new StringBuilder().append(parts[0]);
      for (int i = 1; i < parts.length; ++i) {
        currPrefix.append(".").append(parts[i]);
        createObjectIfNew(objects, currPrefix.toString(), false);
      }
    }

    return objects;
  }

  private void createObjectIfNew(List<Node> objects, String name, boolean needVar) {
    if (!createdObjects.contains(name)) {
      objects.add(createJsNode((needVar ? "var " : "") + name + " = " + name + " || {};"));
      createdObjects.add(name);
    }
  }

  private Node createJsNode(String code) {
    // The parent node after parseSyntheticCode() is SCRIPT node, we need to get rid of it.
    return compiler.parseSyntheticCode(code).removeFirstChild();
  }

  private class RenameInternalsToExternalsCallback extends AbstractPostOrderCallback {
    private final String namespaceName;
    private final Map<String, String> exports;
    private final Node namespaceBlock;

    public RenameInternalsToExternalsCallback(
        String namespaceName, Map<String, String> exports, Node namespaceBlock) {
      this.namespaceName = namespaceName;
      this.exports = exports;
      this.namespaceBlock = namespaceBlock;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if ((n.isFunction() || n.isClass())
          && parent == this.namespaceBlock
          && this.exports.containsKey(n.getFirstChild().getString())) {
        // It's a top-level function/constructor/class definition.
        //
        // Change
        //
        //   /** Some doc */
        //   function internalName() {}
        //
        // to
        //
        //   /** Some doc */
        //   my.namespace.name.externalName = function internalName() {};
        //
        // and change
        //
        //   /** Some doc */
        //   class InternalName {}
        //
        // to
        //
        //   /** Some doc */
        //   my.namespace.name.ExternalName = class InternalName {};
        //
        // by looking up in this.exports for internalName to find the correspondent
        // externalName.
        Node clone = n.cloneTree();
        if (clone.isClass()) {
          Node className = clone.getFirstChild();
          className.replaceWith(IR.empty().useSourceInfoFrom(className));
        }
        NodeUtil.markNewScopesChanged(clone, compiler);
        Node exprResult =
            IR.exprResult(IR.assign(buildQualifiedName(n.getFirstChild()), clone).srcref(n))
                .srcref(n);

        if (n.getJSDocInfo() != null) {
          exprResult.getFirstChild().setJSDocInfo(n.getJSDocInfo());
          clone.removeProp(Node.JSDOC_INFO_PROP);
        }
        this.namespaceBlock.replaceChild(n, exprResult);
        NodeUtil.markFunctionsDeleted(n, compiler);
        compiler.reportChangeToEnclosingScope(exprResult);
      } else if (n.isName()
          && this.exports.containsKey(n.getString())
          && !parent.isFunction()
          && !parent.isClass()) {
        if (NodeUtil.isNameDeclaration(parent)) {
          if (parent.getParent() == this.namespaceBlock) {
            // It's a top-level exported variable definition (maybe without an
            // assignment).
            // Change
            //
            //   var enum = { 'one': 1, 'two': 2 };
            //
            // to
            //
            //   my.namespace.name.enum = { 'one': 1, 'two': 2 };
            Node varContent = n.removeFirstChild();
            Node exprResult;
            if (varContent == null) {
              exprResult = IR.exprResult(buildQualifiedName(n)).srcref(parent);
            } else {
              exprResult =
                  IR.exprResult(IR.assign(buildQualifiedName(n), varContent).srcref(parent))
                      .srcref(parent);
            }
            if (parent.getJSDocInfo() != null) {
              exprResult.getFirstChild().setJSDocInfo(parent.getJSDocInfo().clone());
            }
            this.namespaceBlock.replaceChild(parent, exprResult);
            compiler.reportChangeToEnclosingScope(exprResult);
          }
        } else {
          // It's a local name referencing exported entity. Change to its global name.
          Node newNode = buildQualifiedName(n);
          if (n.getJSDocInfo() != null) {
            newNode.setJSDocInfo(n.getJSDocInfo().clone());
          }

          // If we alter the name of a called function, then it gets an explicit "this"
          // value.
          if (parent.isCall()) {
            parent.putBooleanProp(Node.FREE_CALL, false);
          }

          parent.replaceChild(n, newNode);
          compiler.reportChangeToEnclosingScope(newNode);
        }
      }
    }

    private Node buildQualifiedName(Node internalName) {
      String externalName = this.exports.get(internalName.getString());
      return NodeUtil.newQName(compiler, this.namespaceName + "." + externalName)
          .srcrefTree(internalName);
    }
  }
}
