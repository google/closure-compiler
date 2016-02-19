/*
 * Copyright 2012 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites "goog.defineClass" into a form that is suitable for
 * type checking and dead code elimination.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class ClosureRewriteClass extends AbstractPostOrderCallback
    implements HotSwapCompilerPass {

  // Errors
  static final DiagnosticType GOOG_CLASS_TARGET_INVALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_TARGET_INVALID",
      "Unsupported class definition expression.");

  static final DiagnosticType GOOG_CLASS_SUPER_CLASS_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_SUPER_CLASS_NOT_VALID",
      "The super class must be null or a valid name reference");

  static final DiagnosticType GOOG_CLASS_DESCRIPTOR_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_DESCRIPTOR_NOT_VALID",
      "The class descriptor must be an object literal");

  static final DiagnosticType GOOG_CLASS_CONSTRUCTOR_MISSING = DiagnosticType.error(
      "JSC_GOOG_CLASS_CONSTRUCTOR_MISSING",
      "The constructor expression is missing for the class descriptor");

  static final DiagnosticType GOOG_CLASS_CONSTRUCTOR_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_CONSTRUCTOR_NOT_VALID",
      "The constructor expression must be a function literal");

  static final DiagnosticType GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE = DiagnosticType.error(
      "JSC_GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE",
      "Should not have a constructor expression for an interface");

  static final DiagnosticType GOOG_CLASS_STATICS_NOT_VALID = DiagnosticType.error(
      "JSC_GOOG_CLASS_STATICS_NOT_VALID",
      "The class statics descriptor must be an object or function literal");

  static final DiagnosticType GOOG_CLASS_UNEXPECTED_PARAMS = DiagnosticType.error(
      "JSC_GOOG_CLASS_UNEXPECTED_PARAMS",
      "The class definition has too many arguments.");

  static final DiagnosticType GOOG_CLASS_ES6_COMPUTED_PROP_NAMES_NOT_SUPPORTED =
      DiagnosticType.error(
          "JSC_GOOG_CLASS_ES6_COMPUTED_PROP_NAMES_NOT_SUPPORTED",
          "Computed property names not supported in goog.defineClass.");

  static final DiagnosticType GOOG_CLASS_ES6_SHORTHAND_ASSIGNMENT_NOT_SUPPORTED =
      DiagnosticType.error(
          "JSC_GOOG_CLASS_ES6_SHORTHAND_ASSIGNMENT_NOT_SUPPORTED",
          "Shorthand assignments not supported in goog.defineClass.");

  static final DiagnosticType GOOG_CLASS_ES6_ARROW_FUNCTION_NOT_SUPPORTED =
      DiagnosticType.error(
          "JSC_GOOG_CLASS_ES6_ARROW_FUNCTION_NOT_SUPPORTED",
          "Arrow functions not supported in goog.defineClass. Object literal method"
          + " definition may be an alternative.");

  // Warnings
  static final DiagnosticType GOOG_CLASS_NG_INJECT_ON_CLASS = DiagnosticType.warning(
      "JSC_GOOG_CLASS_NG_INJECT_ON_CLASS",
      "@ngInject should be declared on the constructor, not on the class.");

  private final AbstractCompiler compiler;

  public ClosureRewriteClass(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isCall() && isGoogDefineClass(n) && !validateUsage(n)) {
      compiler.report(JSError.make(n, GOOG_CLASS_TARGET_INVALID));
    }
    maybeRewriteClassDefinition(n);
  }

  private boolean validateUsage(Node n) {
    // There are only three valid usage patterns for of goog.defineClass
    //   var ClassName = googDefineClass
    //   namespace.ClassName = googDefineClass
    //   and within an objectlit, used by the goog.defineClass.
    Node parent = n.getParent();
    switch (parent.getType()) {
      case Token.NAME:
        return true;
      case Token.ASSIGN:
        return n == parent.getLastChild() && parent.getParent().isExprResult();
      case Token.STRING_KEY:
        return isContainedInGoogDefineClass(parent);
    }
    return false;
  }

  private boolean isContainedInGoogDefineClass(Node n) {
    while (n != null) {
      n = n.getParent();
      if (n.isCall()) {
        if (isGoogDefineClass(n)) {
          return true;
        }
      } else if (!n.isObjectLit() && !n.isStringKey()) {
        break;
      }
    }
    return false;
  }

  private void maybeRewriteClassDefinition(Node n) {
    if (NodeUtil.isNameDeclaration(n)) {
      Node target = n.getFirstChild();
      Node value = target.getFirstChild();
      maybeRewriteClassDefinition(n, target, value);
    } else if (NodeUtil.isExprAssign(n)) {
      Node assign = n.getFirstChild();
      Node target = assign.getFirstChild();
      Node value = assign.getLastChild();
      maybeRewriteClassDefinition(n, target, value);
    }
  }

  private void maybeRewriteClassDefinition(
      Node n, Node target, Node value) {
    if (isGoogDefineClass(value)) {
      if (!target.isQualifiedName()) {
        compiler.report(JSError.make(n, GOOG_CLASS_TARGET_INVALID));
      }
      ClassDefinition def = extractClassDefinition(target, value);
      if (def != null) {
        value.detachFromParent();
        target.detachFromParent();
        rewriteGoogDefineClass(n, def);
      }
    }
  }

  private static class MemberDefinition {
    final JSDocInfo info;
    final Node name;
    final Node value;

    MemberDefinition(JSDocInfo info, Node name, Node value) {
      this.info = info;
      this.name = name;
      this.value = value;
    }
  }

  private static final class ClassDefinition {
    final Node name;
    final JSDocInfo classInfo;
    final Node superClass;
    final MemberDefinition constructor;
    final List<MemberDefinition> staticProps;
    final List<MemberDefinition> props;
    final Node classModifier;

    ClassDefinition(
        Node name,
        JSDocInfo classInfo,
        Node superClass,
        MemberDefinition constructor,
        List<MemberDefinition> staticProps,
        List<MemberDefinition> props,
        Node classModifier) {
      this.name = name;
      this.classInfo = classInfo;
      this.superClass = superClass;
      this.constructor = constructor;
      this.staticProps = staticProps;
      this.props = props;
      this.classModifier = classModifier;
    }
  }

  /**
   * Validates the class definition and if valid, destructively extracts
   * the class definition from the AST.
   */
  private ClassDefinition extractClassDefinition(
      Node targetName, Node callNode) {

    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(targetName);

    // name = goog.defineClass(superClass, {...}, [modifier, ...])
    Node superClass = NodeUtil.getArgumentForCallOrNew(callNode, 0);
    if (superClass == null ||
        (!superClass.isNull() && !superClass.isQualifiedName())) {
      compiler.report(JSError.make(callNode, GOOG_CLASS_SUPER_CLASS_NOT_VALID));
      return null;
    }

    if (NodeUtil.isNullOrUndefined(superClass)
        || superClass.matchesQualifiedName("Object")) {
      superClass = null;
    }

    Node description = NodeUtil.getArgumentForCallOrNew(callNode, 1);
    if (!validateObjLit(description, callNode)) {
      // Errors will be reported in the validate method. Keeping here clean
      return null;
    }

    int paramCount = callNode.getChildCount() - 1;
    if (paramCount > 2) {
      compiler.report(JSError.make(callNode, GOOG_CLASS_UNEXPECTED_PARAMS));
      return null;
    }

    Node constructor = extractProperty(description, "constructor");
    if (classInfo != null && classInfo.isInterface()) {
      if (constructor != null) {
        compiler.report(JSError.make(description, GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE));
        return null;
      }
    } else if (constructor == null) {
      // report missing constructor
      compiler.report(JSError.make(description, GOOG_CLASS_CONSTRUCTOR_MISSING));
      return null;
    } else {
      if (!constructor.isFunction()) {
        compiler.report(JSError.make(constructor, GOOG_CLASS_CONSTRUCTOR_NOT_VALID));
      }
    }

    if (constructor == null) {
      constructor = IR.function(
          IR.name("").srcref(callNode),
          IR.paramList().srcref(callNode),
          IR.block().srcref(callNode));
      constructor.srcref(callNode);
    }

    JSDocInfo info = NodeUtil.getBestJSDocInfo(constructor);

    Node classModifier = null;
    Node statics = null;
    Node staticsProp = extractProperty(description, "statics");
    if (staticsProp != null) {
      if (staticsProp.isObjectLit()){
        if (!validateObjLit(staticsProp, staticsProp.getParent())) {
          // Errors will be reported in the validate method. Keeping here clean
          return null;
        }
        statics = staticsProp;
      } else if (staticsProp.isFunction()) {
        classModifier = staticsProp;
      } else {
        compiler.report(
            JSError.make(staticsProp, GOOG_CLASS_STATICS_NOT_VALID));
        return null;
      }
    }

    if (statics == null) {
      statics = IR.objectlit();
    }

    // Ok, now rip apart the definition into its component pieces.
    // Remove the "special" property key nodes.
    maybeDetach(constructor.getParent());
    maybeDetach(statics.getParent());
    if (classModifier != null) {
      maybeDetach(classModifier.getParent());
    }
    ClassDefinition def = new ClassDefinition(
        targetName,
        classInfo,
        maybeDetach(superClass),
        new MemberDefinition(info, null, maybeDetach(constructor)),
        objectLitToList(maybeDetach(statics)),
        objectLitToList(description),
        maybeDetach(classModifier));
    return def;
  }

  private static Node maybeDetach(Node node) {
    if (node != null && node.getParent() != null) {
      node.detachFromParent();
    }
    return node;
  }

  /**
   * @param objlit              the object literal being checked.
   * @param parent              the parent of the object literal node
   * @return false if the node is not an object literal, or if it contains any
   *         property that is neither unquoted plain property nor member
   *         function definition (ES6 feature)
   */
  private boolean validateObjLit(Node objlit, Node parent) {
    if (objlit == null || !objlit.isObjectLit()) {
      reportErrorOnContext(parent);
      return false;
    }

    for (Node key : objlit.children()) {
      if (key.isMemberFunctionDef()) {
        continue;
      }
      if (key.isComputedProp()) {
        // report using computed property name
        compiler.report(JSError.make(objlit,
            GOOG_CLASS_ES6_COMPUTED_PROP_NAMES_NOT_SUPPORTED));
        return false;
      }
      if (key.isStringKey() && !key.hasChildren()) {
        // report using shorthand assignment
        compiler.report(JSError.make(objlit,
            GOOG_CLASS_ES6_SHORTHAND_ASSIGNMENT_NOT_SUPPORTED));
        return false;
      }
      if (key.isStringKey()
          && key.hasChildren()
          && key.getFirstChild().isArrowFunction()){
        // report using arrow function
        compiler.report(JSError.make(objlit,
            GOOG_CLASS_ES6_ARROW_FUNCTION_NOT_SUPPORTED));
        return false;
      }
      if (!key.isStringKey() || key.isQuotedString()) {
        reportErrorOnContext(parent);
        return false;
      }
    }
    return true;
  }

  private void reportErrorOnContext(Node parent){
    if (parent.isStringKey()){
      compiler.report(JSError.make(parent, GOOG_CLASS_STATICS_NOT_VALID));
    } else {
      // Report error in the context that the objlit is an
      // argument of goog.defineClass call.
      Preconditions.checkState(parent.isCall());
      compiler.report(JSError.make(parent, GOOG_CLASS_DESCRIPTOR_NOT_VALID));
    }
  }

  /**
   * @return The first property in the objlit that matches the key.
   */
  private static Node extractProperty(Node objlit, String keyName) {
    for (Node keyNode : objlit.children()) {
      if (keyNode.getString().equals(keyName)) {
        return keyNode.getFirstChild();
      }
    }
    return null;
  }

  private static List<MemberDefinition> objectLitToList(
      Node objlit) {
    List<MemberDefinition> result = new ArrayList<>();
    for (Node keyNode : objlit.children()) {
      result.add(
          new MemberDefinition(
                NodeUtil.getBestJSDocInfo(keyNode),
                keyNode,
                keyNode.removeFirstChild()));
    }
    objlit.detachChildren();
    return result;
  }

  private void rewriteGoogDefineClass(Node exprRoot, final ClassDefinition cls) {
    // For simplicity add everything into a block, before adding it to the AST.
    Node block = IR.block();

    // remove the original jsdoc info if it was attached to the value.
    cls.constructor.value.setJSDocInfo(null);
    if (NodeUtil.isNameDeclaration(exprRoot)) {
      // example: var ctr = function(){}
      Node decl = IR.declaration(cls.name.cloneTree(), cls.constructor.value, exprRoot.getType())
          .srcref(exprRoot);
      JSDocInfo mergedClassInfo = mergeJsDocFor(cls, decl);
      decl.setJSDocInfo(mergedClassInfo);
      block.addChildToBack(decl);
    } else {
      // example: ns.ctr = function(){}
      Node assign = IR.assign(cls.name.cloneTree(), cls.constructor.value)
          .srcref(exprRoot)
          .setJSDocInfo(cls.constructor.info);

      JSDocInfo mergedClassInfo = mergeJsDocFor(cls, assign);
      assign.setJSDocInfo(mergedClassInfo);

      Node expr = IR.exprResult(assign).srcref(exprRoot);
      block.addChildToBack(expr);
    }

    if (cls.superClass != null) {
      // example: goog.inherits(ctr, superClass)
      block.addChildToBack(
          fixupSrcref(IR.exprResult(
              IR.call(
                  NodeUtil.newQName(compiler, "goog.inherits")
                      .srcrefTree(cls.superClass),
                  cls.name.cloneTree(),
                  cls.superClass.cloneTree()).srcref(cls.superClass))));
    }

    for (MemberDefinition def : cls.staticProps) {
      // remove the original jsdoc info if it was attached to the value.
      def.value.setJSDocInfo(null);

      // example: ctr.prop = value
      block.addChildToBack(
          fixupSrcref(IR.exprResult(
          fixupSrcref(IR.assign(
              IR.getprop(cls.name.cloneTree(),
                  IR.string(def.name.getString()).srcref(def.name))
                  .srcref(def.name),
              def.value)).setJSDocInfo(def.info))));
      // Handle inner class definitions.
      maybeRewriteClassDefinition(block.getLastChild());
    }

    for (MemberDefinition def : cls.props) {
      // remove the original jsdoc info if it was attached to the value.
      def.value.setJSDocInfo(null);

      // example: ctr.prototype.prop = value
      Node exprResult =
          IR.exprResult(
              IR.assign(
                      NodeUtil.newQName(
                          compiler,
                          cls.name.getQualifiedName() + ".prototype." + def.name.getString()),
                      def.value)
                  .setJSDocInfo(def.info));
      exprResult.useSourceInfoIfMissingFromForTree(def.name);
      
      // The length needs to be set explicitly to include the string key node and the function node.
      // If we just used the length of def.name or def.value alone, then refactorings which try to
      // delete the method would not work correctly.
      exprResult.setLength(
          def.value.getSourceOffset() + def.value.getLength() - def.name.getSourceOffset());
      block.addChildToBack(exprResult);

      // Handle inner class definitions.
      maybeRewriteClassDefinition(block.getLastChild());
    }

    if (cls.classModifier != null) {
      // Inside the modifier function, replace references to the argument
      // with the class name.
      //   function(cls) { cls.Foo = bar; }
      // becomes
      //   function(cls) { theClassName.Foo = bar; }
      // The cls parameter is unused, but leave it there so that it
      // matches the JsDoc.
      // TODO(tbreisacher): Add a warning if the param is shadowed or reassigned.
      Node argList = cls.classModifier.getSecondChild();
      Node arg = argList.getFirstChild();
      final String argName = arg.getString();
      NodeTraversal.traverseEs6(compiler, cls.classModifier.getLastChild(),
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName() && n.getString().equals(argName)) {
                parent.replaceChild(n, cls.name.cloneTree());
              }
            }
          });

      block.addChildToBack(
          IR.exprResult(
              fixupFreeCall(
                  IR.call(
                      cls.classModifier,
                      cls.name.cloneTree())
                      .srcref(cls.classModifier)))
              .srcref(cls.classModifier));
    }

    Node parent = exprRoot.getParent();
    Node stmts = block.removeChildren();
    parent.addChildrenAfter(stmts, exprRoot);
    parent.removeChild(exprRoot);

    compiler.reportCodeChange();
  }

  private static Node fixupSrcref(Node node) {
    node.srcref(node.getFirstChild());
    return node;
  }

  private static Node fixupFreeCall(Node call) {
    Preconditions.checkState(call.isCall());
    call.putBooleanProp(Node.FREE_CALL, true);
    return call;
  }

  /**
   * @return Whether the call represents a class definition.
   */
  private static boolean isGoogDefineClass(Node value) {
    if (value != null && value.isCall()) {
      return value.getFirstChild().matchesQualifiedName("goog.defineClass");
    }
    return false;
  }

  static final String VIRTUAL_FILE = "<ClosureRewriteClass.java>";

  private JSDocInfo mergeJsDocFor(ClassDefinition cls, Node associatedNode) {
    // avoid null checks
    JSDocInfo classInfo = (cls.classInfo != null)
        ? cls.classInfo
        : new JSDocInfoBuilder(true).build(true);

    JSDocInfo ctorInfo = (cls.constructor.info != null)
        ? cls.constructor.info
        : new JSDocInfoBuilder(true).build(true);

    Node superNode = cls.superClass;

    // Start with a clone of the constructor info if there is one.
    JSDocInfoBuilder mergedInfo = cls.constructor.info != null
        ? JSDocInfoBuilder.copyFrom(ctorInfo)
        : new JSDocInfoBuilder(true);

    // merge block description
    String blockDescription = Joiner.on("\n").skipNulls().join(
        classInfo.getBlockDescription(),
        ctorInfo.getBlockDescription());
    if (!blockDescription.isEmpty()) {
      mergedInfo.recordBlockDescription(blockDescription);
    }

    // merge suppressions
    Set<String> suppressions = new HashSet<>();
    suppressions.addAll(classInfo.getSuppressions());
    suppressions.addAll(ctorInfo.getSuppressions());
    if (!suppressions.isEmpty()) {
      mergedInfo.recordSuppressions(suppressions);
    }

    // Use class deprecation if set.
    if (classInfo.isDeprecated()) {
      mergedInfo.recordDeprecated();
    }

    String deprecationReason = null;
    if (classInfo.getDeprecationReason() != null) {
      deprecationReason = classInfo.getDeprecationReason();
      mergedInfo.recordDeprecationReason(deprecationReason);
    }

    // Use class visibility if specifically set
    Visibility visibility = classInfo.getVisibility();
    if (visibility != null && visibility != JSDocInfo.Visibility.INHERITED) {
      mergedInfo.recordVisibility(classInfo.getVisibility());
    }

    if (classInfo.isConstant()) {
      mergedInfo.recordConstancy();
    }

    if (classInfo.isExport()) {
      mergedInfo.recordExport();
    }

    // If @ngInject is on the ctor, it's already been copied above.
    if (classInfo.isNgInject()) {
      compiler.report(JSError.make(associatedNode, GOOG_CLASS_NG_INJECT_ON_CLASS));
      mergedInfo.recordNgInject(true);
    }

    if (classInfo.makesUnrestricted() || ctorInfo.makesUnrestricted()) {
      mergedInfo.recordUnrestricted();
    } else if (classInfo.makesDicts() || ctorInfo.makesDicts()) {
      mergedInfo.recordDict();
    } else {
      // @struct by default
      mergedInfo.recordStruct();
    }

    // @constructor is implied, @interface must be explicit
    boolean isInterface = classInfo.isInterface() || ctorInfo.isInterface();
    if (isInterface) {
      if (classInfo.usesImplicitMatch() || ctorInfo.usesImplicitMatch()) {
        mergedInfo.recordImplicitMatch();
      } else {
        mergedInfo.recordInterface();
      }
      List<JSTypeExpression> extendedInterfaces = null;
      if (classInfo.getExtendedInterfacesCount() > 0) {
        extendedInterfaces = classInfo.getExtendedInterfaces();
      } else if (ctorInfo.getExtendedInterfacesCount() == 0
          && superNode != null) {
        extendedInterfaces = ImmutableList.of(new JSTypeExpression(
            new Node(Token.BANG,
                IR.string(superNode.getQualifiedName())),
            VIRTUAL_FILE));
      }
      if (extendedInterfaces != null) {
        for (JSTypeExpression extend : extendedInterfaces) {
          mergedInfo.recordExtendedInterface(extend);
        }
      }
    } else {
      // @constructor by default
      mergedInfo.recordConstructor();

      if (classInfo.getBaseType() != null) {
        mergedInfo.recordBaseType(classInfo.getBaseType());
      } else if (superNode != null) {
        // a "super" implies @extends, build a default.
        JSTypeExpression baseType = new JSTypeExpression(
            new Node(Token.BANG,
              IR.string(superNode.getQualifiedName())),
            VIRTUAL_FILE);
        mergedInfo.recordBaseType(baseType);
      }

      // @implements from the class if they exist
      List<JSTypeExpression> interfaces = classInfo.getImplementedInterfaces();
      for (JSTypeExpression implemented : interfaces) {
        mergedInfo.recordImplementedInterface(implemented);
      }
    }

    // merge @template types if they exist
    List<String> templateNames = new ArrayList<>();
    templateNames.addAll(classInfo.getTemplateTypeNames());
    templateNames.addAll(ctorInfo.getTemplateTypeNames());
    for (String typeName : templateNames) {
      mergedInfo.recordTemplateTypeName(typeName);
    }
    return mergedInfo.build();
  }
}
