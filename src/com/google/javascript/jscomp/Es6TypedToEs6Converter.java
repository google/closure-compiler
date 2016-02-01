/*
 * Copyright 2015 The Closure Compiler Authors.
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
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.Es6ToEs3Converter.ClassDeclarationMetadata;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeDeclarationsIR;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Converts {@link Node#getDeclaredTypeExpression()} to {@link JSDocInfo#getType()} type
 * annotations. Types are marked as inline types.
 */
public final class Es6TypedToEs6Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  static final DiagnosticType CANNOT_CONVERT_MEMBER_VARIABLES = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_FIELDS",
      "Can only convert class member variables (fields) in declarations or the right hand side of "
          + "a simple assignment.");

  static final DiagnosticType CANNOT_CONVERT_BOUNDED_GENERICS = DiagnosticType.warning(
      "JSC_CANNOT_CONVERT_BOUNDED_GENERICS",
      "Bounded generics are not yet implemented.");

  static final DiagnosticType TYPE_ALIAS_ALREADY_DECLARED = DiagnosticType.error(
      "JSC_TYPE_ALIAS_ALREADY_DECLARED",
      "Type alias already declared as a variable: {0}");

  static final DiagnosticType TYPE_QUERY_NOT_SUPPORTED = DiagnosticType.warning(
      "JSC_TYPE_QUERY_NOT_SUPPORTED",
      "Type query is currently not supported.");

  static final DiagnosticType UNSUPPORTED_RECORD_TYPE = DiagnosticType.error(
      "JSC_UNSUPPORTED_RECORD_TYPE",
      "Currently only member variables are supported in record types, please consider "
          + "using interfaces instead.");

  static final DiagnosticType COMPUTED_PROP_ACCESS_MODIFIER = DiagnosticType.warning(
      "JSC_COMPUTED_PROP_ACCESS_MODIFIER",
      "Accessibility is not checked on computed properties");

  static final DiagnosticType NON_AMBIENT_NAMESPACE_NOT_SUPPORTED = DiagnosticType.error(
      "JSC_NON_AMBIENT_NAMESPACE_NOT_SUPPORTED",
      "Non-ambient namespaces are not supported");

  static final DiagnosticType CALL_SIGNATURE_NOT_SUPPORTED = DiagnosticType.error(
      "JSC_CALL_SIGNATURE_NOT_SUPPORTED",
      "Call signature and construct signatures are not supported yet");

  static final DiagnosticType OVERLOAD_NOT_SUPPORTED = DiagnosticType.warning(
      "JSC_OVERLOAD_NOT_SUPPORTED",
      "Function and method overloads are not supported and type information might be lost");

  static final DiagnosticType SPECIALIZED_SIGNATURE_NOT_SUPPORTED = DiagnosticType.warning(
      "JSC_SPECIALIZED_SIGNATURE_NOT_SUPPORTED",
      "Specialized signatures are not supported and type information might be lost");

  static final DiagnosticType DECLARE_IN_NON_EXTERNS = DiagnosticType.warning(
      "JSC_DECLARE_IN_NON_EXTERNS",
      "Found a declare statement in program code.\n"
      + "If you are generating externs, this should be fine.\n"
      + "If not, make sure to pass your .d.ts file as an extern file.");

  private final AbstractCompiler compiler;
  private final Map<Node, Namespace> nodeNamespaceMap;
  private final Set<String> convertedNamespaces;
  private Namespace currNamespace;

  private final Deque<Map<String, Node>> overloadStack;
  private final Set<Node> processedOverloads;

  Es6TypedToEs6Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.nodeNamespaceMap = new HashMap<>();
    this.convertedNamespaces = new HashSet<>();
    this.overloadStack = new ArrayDeque<>();
    this.processedOverloads = new HashSet<>();
  }

  @Override
  public void process(Node externs, Node scriptRoot) {
    ScanNamespaces scanner = new ScanNamespaces();
    NodeTraversal.traverseEs6(compiler, externs, scanner);
    NodeTraversal.traverseEs6(compiler, scriptRoot, scanner);
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    ScanNamespaces scanner = new ScanNamespaces();
    NodeTraversal.traverseEs6(compiler, scriptRoot, scanner);
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.NAMESPACE:
        if (currNamespace == null && parent.getType() != Token.DECLARE) {
          compiler.report(JSError.make(n, NON_AMBIENT_NAMESPACE_NOT_SUPPORTED));
          return false;
        }
        currNamespace = nodeNamespaceMap.get(n);
        pushOverloads();
        return true;
      case Token.SCRIPT:
      case Token.INTERFACE:
      case Token.CLASS:
        pushOverloads();
        return true;
      default:
        return true;
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.CLASS:
        visitClass(n, parent);
        popOverloads();
        break;
      case Token.INTERFACE:
        visitInterface(n, parent);
        popOverloads();
        break;
      case Token.ENUM:
        visitEnum(n, parent);
        break;
      case Token.NAME:
      case Token.REST:
        maybeVisitColonType(n, n);
        break;
      case Token.FUNCTION:
        visitFunction(n, parent);
        break;
      case Token.TYPE_ALIAS:
        visitTypeAlias(t, n, parent);
        break;
      case Token.DECLARE:
        visitAmbientDeclaration(n, parent);
        break;
      case Token.EXPORT:
        visitExport(n, parent);
        break;
      case Token.NAMESPACE:
        visitNamespaceDeclaration(n, parent);
        popOverloads();
        break;
      case Token.VAR:
      case Token.LET:
      case Token.CONST:
        visitVarInsideNamespace(n, parent);
        break;
      case Token.SCRIPT:
        popOverloads();
        break;
      default:
    }
  }

  private void visitNamespaceDeclaration(Node n, Node parent) {
    popNamespace(n, parent);
    for (Node name = NodeUtil.getRootOfQualifiedName(n.getFirstChild()); name != n;
        name = name.getParent()) {
      String fullName = maybePrependCurrNamespace(name.getQualifiedName());
      if (!convertedNamespaces.contains(fullName)) {
        JSDocInfoBuilder doc = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
        doc.recordConstancy();
        Node namespaceDec = NodeUtil.newQNameDeclaration(
            compiler, fullName, IR.objectlit(), doc.build()).useSourceInfoFromForTree(n);
        parent.addChildBefore(namespaceDec, n);
        convertedNamespaces.add(fullName);
      }
    }

    replaceWithNodes(n, n.getLastChild().children());
  }

  private void maybeAddGenerics(Node n, Node jsDocNode) {
    Node name = n.getFirstChild();
    Node generics = (Node) name.getProp(Node.GENERIC_TYPE_LIST);
    if (generics != null) {
      JSDocInfoBuilder doc = JSDocInfoBuilder.maybeCopyFrom(jsDocNode.getJSDocInfo());
      // Discard the type bound (the "extends" part) for now
      for (Node typeName : generics.children()) {
        doc.recordTemplateTypeName(typeName.getString());
        if (typeName.hasChildren()) {
          compiler.report(JSError.make(name, CANNOT_CONVERT_BOUNDED_GENERICS));
          typeName.removeChildren();
        }
      }
      name.removeProp(Node.GENERIC_TYPE_LIST);
      jsDocNode.setJSDocInfo(doc.build());
    }
  }

  private void visitClass(Node n, Node parent) {
    maybeAddGenerics(n, n);
    JSDocInfoBuilder doc = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
    Node interfaces = (Node) n.getProp(Node.IMPLEMENTS);
    if (interfaces != null) {
      for (Node child : interfaces.children()) {
        Node type = convertWithLocation(child);
        doc.recordImplementedInterface(new JSTypeExpression(type, n.getSourceFileName()));
      }
      n.removeProp(Node.IMPLEMENTS);
    }

    Node superType = n.getSecondChild();
    Node newSuperType = maybeGetQualifiedNameNode(superType);
    if (newSuperType != superType) {
      n.replaceChild(superType, newSuperType);
    }

    Node classMembers = n.getLastChild();
    ClassDeclarationMetadata metadata = ClassDeclarationMetadata.create(n, parent);

    for (Node member : classMembers.children()) {
      if (member.isCallSignature()) {
        compiler.report(JSError.make(n, CALL_SIGNATURE_NOT_SUPPORTED));
        continue;
      }

      if (member.isIndexSignature()) {
        doc.recordImplementedInterface(createIObject(member));
        continue;
      }

      // Functions are handled by the regular Es6ToEs3Converter
      if (!member.isMemberVariableDef() && !member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE)) {
        maybeAddVisibility(member);
        continue;
      }

      if (metadata == null) {
        compiler.report(JSError.make(n, CANNOT_CONVERT_MEMBER_VARIABLES));
        return;
      }

      metadata.insertNodeAndAdvance(createPropertyDefinition(member, metadata.fullClassName));
      compiler.reportCodeChange();
    }

    n.setJSDocInfo(doc.build());
    maybeCreateQualifiedDeclaration(n, parent);
  }

  private void visitInterface(Node n, Node parent) {
    maybeAddGenerics(n, n);
    Node name = n.getFirstChild();
    Node superTypes = name.getNext();
    JSDocInfoBuilder doc = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
    doc.recordInterface();
    if (!superTypes.isEmpty()) {
      for (Node child : superTypes.children()) {
        Node type = convertWithLocation(child);
        doc.recordExtendedInterface(new JSTypeExpression(type, n.getSourceFileName()));
      }
    }

    Node insertionPoint = n;
    Node members = n.getLastChild();
    for (Node member : members.children()) {
      if (member.isCallSignature()) {
        compiler.report(JSError.make(n, CALL_SIGNATURE_NOT_SUPPORTED));
      }

      if (member.isIndexSignature()) {
        doc.recordExtendedInterface(createIObject(member));
      }

      // Synthesize a block for method signatures, or convert it to a member variable if optional.
      if (member.isMemberFunctionDef()) {
        Node function = member.getFirstChild();
        if (function.isOptionalEs6Typed()) {
          member = convertMemberFunctionToMemberVariable(member);
        } else {
          function.getLastChild().setType(Token.BLOCK);
        }
      }

      if (member.isMemberVariableDef()) {
        Node newNode = createPropertyDefinition(member, name.getString());
        insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
        insertionPoint = newNode;
      }
    }
    n.setJSDocInfo(doc.build());

    // Convert interface to class
    n.setType(Token.CLASS);
    Node empty = new Node(Token.EMPTY).useSourceInfoIfMissingFrom(n);
    n.replaceChild(superTypes, empty);
    members.setType(Token.CLASS_MEMBERS);

    maybeCreateQualifiedDeclaration(n, parent);
    compiler.reportCodeChange();
  }

  private JSTypeExpression createIObject(Node indexSignature) {
    Node indexType = convertWithLocation(indexSignature.getFirstChild()
        .getDeclaredTypeExpression());
    Node declaredType = convertWithLocation(indexSignature.getDeclaredTypeExpression());
    Node block = new Node(Token.BLOCK, indexType, declaredType);
    Node iObject = IR.string("IObject");
    iObject.addChildrenToFront(block);
    JSTypeExpression bang = new JSTypeExpression(new Node(Token.BANG, iObject)
        .useSourceInfoIfMissingFromForTree(indexSignature), indexSignature.getSourceFileName());
    indexSignature.detachFromParent();
    compiler.reportCodeChange();
    return bang;
  }

  private Node createPropertyDefinition(Node member, String className) {
    member.detachFromParent();
    className = maybePrependCurrNamespace(className);
    Node nameAccess = NodeUtil.newQName(compiler, className);
    Node prototypeAccess = NodeUtil.newPropertyAccess(compiler, nameAccess, "prototype");
    Node qualifiedMemberAccess = getQualifiedMemberAccess(
        compiler, member, nameAccess, prototypeAccess);
    // Copy type information.
    maybeVisitColonType(member, member);
    maybeAddVisibility(member);

    qualifiedMemberAccess.setJSDocInfo(member.getJSDocInfo());
    Node newNode = NodeUtil.newExpr(qualifiedMemberAccess);
    return newNode.useSourceInfoIfMissingFromForTree(member);
  }

  /**
   * Constructs a Node that represents an access to the given class member, qualified by either the
   * static or the instance access context, depending on whether the member is static.
   *
   * <p><b>WARNING:</b> {@code member} may be modified/destroyed by this method, do not use it
   * afterwards.
   */
  private static Node getQualifiedMemberAccess(AbstractCompiler compiler, Node member,
      Node staticAccess, Node instanceAccess) {
    Node context = member.isStaticMember() ? staticAccess : instanceAccess;
    context = context.cloneTree();
    if (member.isComputedProp()) {
      return IR.getelem(context, member.removeFirstChild());
    } else {
      return NodeUtil.newPropertyAccess(compiler, context, member.getString());
    }
  }

  private void visitEnum(Node n, Node parent) {
    Node name = n.getFirstChild();
    Node members = n.getLastChild();
    double nextValue = 0;
    Node[] stringKeys = new Node[members.getChildCount()];
    for (int i = 0; i < members.getChildCount(); i++) {
      Node child = members.getChildAtIndex(i);
      if (child.hasChildren()) {
        nextValue = child.getFirstChild().getDouble() + 1;
      } else {
        child.addChildToFront(IR.number(nextValue++));
      }
      stringKeys[i] = child;
    }
    for (Node child : stringKeys) {
      child.detachFromParent();
    }

    String oldName = name.getString();
    String qName = maybePrependCurrNamespace(oldName);
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
    builder.recordEnumParameterType(
        new JSTypeExpression(IR.string("number"), n.getSourceFileName()));
    Node newDec = NodeUtil.newQNameDeclaration(
        compiler,
        qName,
        IR.objectlit(stringKeys),
        builder.build()).useSourceInfoFromForTree(n);
    n.setJSDocInfo(null);
    parent.replaceChild(n, newDec);
    compiler.reportCodeChange();
  }

  private void visitFunction(Node n, Node parent) {
    // For member functions (eg. class Foo<T> { f() {} }), the JSDocInfo
    // needs to go on the synthetic MEMBER_FUNCTION_DEF node.
    boolean isMemberFunctionDef = parent.isMemberFunctionDef();

    // Currently, we remove the overloading signature and drop the type information on the original
    // signature.
    String name = isMemberFunctionDef ? parent.getString() : n.getFirstChild().getString();
    if (!name.isEmpty() && overloadStack.peek().containsKey(name)) {
      compiler.report(JSError.make(n, OVERLOAD_NOT_SUPPORTED));
      if (isMemberFunctionDef) {
        parent.detachFromParent();
      } else {
        n.detachFromParent();
      }
      if (!processedOverloads.contains(overloadStack)) {
        Node original = overloadStack.peek().get(name);
        processedOverloads.add(original);
        Node paramList = original.getSecondChild();
        paramList.removeChildren();
        Node originalParent = original.getParent();
        Node originalJsDocNode = originalParent.isMemberFunctionDef() || originalParent.isAssign()
            ? originalParent : original;
        JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
        builder.recordType(new JSTypeExpression(
            convertWithLocation(TypeDeclarationsIR.namedType("Function")), n.getSourceFileName()));
        originalJsDocNode.setJSDocInfo(builder.build());
      }
      compiler.reportCodeChange();
      return;
    }
    overloadStack.peek().put(name, n);

    Node jsDocNode = isMemberFunctionDef ? parent : n;
    maybeAddGenerics(n, jsDocNode);
    // Return types are colon types on the function node. Optional member functions are handled
    // separately.
    if (!(isMemberFunctionDef && n.isOptionalEs6Typed())) {
      maybeVisitColonType(n, jsDocNode);
    }
    if (n.getLastChild().isEmpty()) {
      n.replaceChild(n.getLastChild(), IR.block().useSourceInfoFrom(n));
    }
    if (!isMemberFunctionDef) {
      maybeCreateQualifiedDeclaration(n, parent);
    }
  }

  private void maybeAddVisibility(Node n) {
    Visibility access = (Visibility) n.getProp(Node.ACCESS_MODIFIER);
    if (access != null) {
      if (n.isComputedProp()) {
        compiler.report(JSError.make(n, COMPUTED_PROP_ACCESS_MODIFIER));
      }
      JSDocInfoBuilder memberDoc = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
      memberDoc.recordVisibility(access);
      n.setJSDocInfo(memberDoc.build());
      n.removeProp(Node.ACCESS_MODIFIER);
    }
  }

  private void maybeVisitColonType(Node n, Node jsDocNode) {
    Node type = n.getDeclaredTypeExpression();
    boolean hasColonType = type != null;
    if (n.isRest() && hasColonType) {
      type = new Node(Token.ELLIPSIS, convertWithLocation(type.removeFirstChild()));
    } else if (n.isMemberVariableDef()) {
      if (type != null) {
        type = maybeProcessOptionalProperty(n, type);
      }
    } else {
      type = maybeProcessOptionalParameter(n, type);
    }
    if (type == null) {
      return;
    }

    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(jsDocNode.getJSDocInfo());
    JSTypeExpression typeExpression = new JSTypeExpression(type, n.getSourceFileName());
    switch (n.getType()) {
      case Token.FUNCTION:
        builder.recordReturnType(typeExpression);
        break;
      case Token.MEMBER_VARIABLE_DEF:
        builder.recordType(typeExpression);
        break;
      default:
        builder.recordType(typeExpression);
        builder.recordInlineType();
    }

    jsDocNode.setJSDocInfo(builder.build());

    if (hasColonType) {
      n.setDeclaredTypeExpression(null);
      compiler.reportCodeChange();
    }
  }

  private void visitTypeAlias(NodeTraversal t, Node n, Node parent) {
    String alias = n.getString();
    if (t.getScope().isDeclared(alias, true)) {
      compiler.report(
          JSError.make(n, TYPE_ALIAS_ALREADY_DECLARED, alias));
    }
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
    builder.recordTypedef(new JSTypeExpression(
        convertWithLocation(n.getFirstChild()), n.getSourceFileName()));

    Node newName =
        maybeGetQualifiedNameNode(IR.name(n.getString())).useSourceInfoIfMissingFromForTree(n);
    Node newDec1 = NodeUtil.newQNameDeclaration(
        compiler,
        newName.getQualifiedName(),
        null,
        builder.build()).useSourceInfoFromForTree(n);
    parent.replaceChild(n, newDec1);
    compiler.reportCodeChange();
  }

  private void visitAmbientDeclaration(Node n, Node parent) {
    if (!n.isFromExterns()) {
      compiler.report(JSError.make(n, DECLARE_IN_NON_EXTERNS));
    }

    Node insertionPoint = n;
    Node topLevel = parent;
    boolean insideExport = parent.getType() == Token.EXPORT;
    if (insideExport) {
      insertionPoint = parent;
      topLevel = parent.getParent();
    }
    // The node can have multiple children if transformed from an ambient namespace declaration.
    for (Node c : n.children()) {
      if (c.getType() == Token.CONST) {
        JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(c.getJSDocInfo());
        builder.recordConstancy();
        c.setType(Token.VAR);
        c.setJSDocInfo(builder.build());
      }

      Node toAdd = c.detachFromParent();
      if (insideExport && !toAdd.isExprResult()) {
        // We want to keep the "export" declaration in externs
        toAdd = new Node(Token.EXPORT, toAdd).srcref(parent);
      }
      topLevel.addChildBefore(toAdd, insertionPoint);
    }
    insertionPoint.detachFromParent();
    compiler.reportCodeChange();
  }

  private void visitExport(Node n, Node parent) {
    if (currNamespace != null) {
      replaceWithNodes(n, n.children());
    } else if (n.hasMoreThanOneChild()) {
      Node insertPoint = n;
      for (Node c = n.getSecondChild(); c != null; c = c.getNext()) {
        Node toAdd;
        if (!c.isExprResult()) {
          toAdd = n.cloneNode();
          toAdd.addChildToFront(c.detachFromParent());
        } else {
          toAdd = c.detachFromParent();
        }
        parent.addChildAfter(toAdd, insertPoint);
        insertPoint = toAdd;
      }
      compiler.reportCodeChange();
    }
  }

  private void replaceWithNodes(Node n, Iterable<Node> replacements) {
    Node insertPoint = n;
    for (Node c : replacements) {
      Node detached = c.detachFromParent();
      n.getParent().addChildAfter(detached, insertPoint);
      insertPoint = detached;
    }
    n.detachFromParent();
    compiler.reportCodeChange();
  }

  private void visitVarInsideNamespace(Node n, Node parent) {
    if (currNamespace != null) {
      Node insertPoint = n;
      for (Node child : n.children()) {
        Node name = child;
        String oldName = name.getString();
        String qName = maybePrependCurrNamespace(oldName);
        JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(child.getJSDocInfo());
        if (n.isConst()) {
          builder.recordConstancy();
        }

        Node newDec = NodeUtil.newQNameDeclaration(
            compiler,
            qName,
            child.removeFirstChild(),
            builder.build()).useSourceInfoFromForTree(n);
        parent.addChildAfter(newDec, insertPoint);
        insertPoint = newDec;
      }

      n.detachFromParent();
      compiler.reportCodeChange();
    }
  }

  private Node maybeCreateAnyType(Node n, Node type) {
    return type == null ? TypeDeclarationsIR.anyType().useSourceInfoIfMissingFrom(n) : type;
  }

  private Node maybeProcessOptionalParameter(Node n, Node type) {
    if (n.isOptionalEs6Typed()) {
      n.putBooleanProp(Node.OPT_ES6_TYPED, false);
      type = maybeCreateAnyType(n, type);
      return new Node(Token.EQUALS, convertWithLocation(type));
    } else {
      return type == null ? null : convertWithLocation(type);
    }
  }

  private Node maybeProcessOptionalProperty(Node n, Node type) {
    if (n.isOptionalEs6Typed()) {
      n.putBooleanProp(Node.OPT_ES6_TYPED, false);
      TypeDeclarationNode baseType = (TypeDeclarationNode) maybeCreateAnyType(n, type);
      type = TypeDeclarationsIR.unionType(
          ImmutableList.of(baseType, TypeDeclarationsIR.undefinedType()));
      type.useSourceInfoIfMissingFromForTree(baseType);
    } else {
      type = maybeCreateAnyType(n, type);
    }

    return convertWithLocation(type);
  }

  private Node convertWithLocation(Node type) {
    return convertDeclaredTypeToJSDoc(type).useSourceInfoIfMissingFrom(type);
  }

  private Node convertDeclaredTypeToJSDoc(Node type) {
    Preconditions.checkArgument(type instanceof TypeDeclarationNode);
    switch (type.getType()) {
      // "Primitive" types.
      case Token.STRING_TYPE:
        return IR.string("string");
      case Token.BOOLEAN_TYPE:
        return IR.string("boolean");
      case Token.NUMBER_TYPE:
        return IR.string("number");
      case Token.VOID_TYPE:
        return IR.string("void");
      case Token.UNDEFINED_TYPE:
        return IR.string("undefined");
      case Token.ANY_TYPE:
        return new Node(Token.QMARK);
        // Named types.
      case Token.NAMED_TYPE:
        return convertNamedType(type);
      case Token.ARRAY_TYPE: {
        Node arrayType = IR.string("Array");
        Node memberType = convertWithLocation(type.getFirstChild());
        arrayType.addChildToFront(
            new Node(Token.BLOCK, memberType).useSourceInfoIfMissingFrom(type));
        return new Node(Token.BANG, arrayType);
      }
      case Token.PARAMETERIZED_TYPE: {
        Node namedType = type.getFirstChild();
        Node result = convertWithLocation(namedType);
        Node typeParameterTarget =
            result.getType() == Token.BANG ? result.getFirstChild() : result;
        Node parameters = IR.block().useSourceInfoIfMissingFrom(type);
        typeParameterTarget.addChildToFront(parameters);
        for (Node param = namedType.getNext(); param != null; param = param.getNext()) {
          parameters.addChildToBack(convertWithLocation(param));
        }
        return result;
      }
      // Composite types.
      case Token.FUNCTION_TYPE: {
        Node returnType = type.getFirstChild();
        Node paramList = new Node(Token.PARAM_LIST);
        for (Node param = returnType.getNext(); param != null; param = param.getNext()) {
          Node paramType = param.getDeclaredTypeExpression();
          if (param.isRest()) {
            if (paramType == null) {
              paramType = new Node(Token.ELLIPSIS, new Node(Token.QMARK));
            } else {
              paramType = new Node(Token.ELLIPSIS,
                  convertWithLocation(paramType.getFirstChild()));
            }
          } else {
            paramType = maybeProcessOptionalParameter(param,
                maybeCreateAnyType(param, paramType));
          }
          paramList.addChildToBack(paramType);
        }
        Node function = new Node(Token.FUNCTION);
        // TODO(moz): We should always add a PARAM_LIST in JsDocInfoParser
        if (paramList.hasChildren()) {
          function.addChildToBack(paramList);
        }
        function.addChildToBack(convertWithLocation(returnType));
        return function;
      }
      case Token.UNION_TYPE:
        Node pipe = new Node(Token.PIPE);
        for (Node child : type.children()) {
          pipe.addChildToBack(convertWithLocation(child));
        }
        return pipe;
      case Token.RECORD_TYPE: {
        Node lb = new Node(Token.LB);
        for (Node member : type.children()) {
          if (member.isMemberFunctionDef()) {
            member = convertMemberFunctionToMemberVariable(member);
          } else if (!member.isMemberVariableDef()) {
            compiler.report(JSError.make(type, UNSUPPORTED_RECORD_TYPE));
            continue;
          }
          Node colon = new Node(Token.COLON);
          member.setType(Token.STRING_KEY);
          Node memberType =
              maybeProcessOptionalProperty(member, member.getDeclaredTypeExpression());
          member.setDeclaredTypeExpression(null);
          colon.addChildToBack(member.detachFromParent());
          colon.addChildToBack(memberType);
          lb.addChildrenToBack(colon);
        }
        return new Node(Token.LC, lb);
      }
      case Token.TYPEOF:
        // Currently, TypeQuery is not supported in Closure's type system.
        compiler.report(JSError.make(type, TYPE_QUERY_NOT_SUPPORTED));
        return new Node(Token.QMARK);
      case Token.STRING:
        compiler.report(JSError.make(type, SPECIALIZED_SIGNATURE_NOT_SUPPORTED));
        return new Node(Token.QMARK);
      default:
        // TODO(moz): Implement.
        break;
    }
    throw new IllegalArgumentException(
        "Unexpected node type for type conversion: " + type.getType());
  }

  private Node convertNamedType(Node type) {
    Node oldNameNode = type.getFirstChild();
    Node newNameNode = maybeGetQualifiedNameNode(oldNameNode);
    if (newNameNode != oldNameNode) {
      type.replaceChild(oldNameNode, newNameNode);
    }

    Node propTree = type.getFirstChild();
    String dotted = propTree.getQualifiedName();
    // In the native type syntax, nominal types are non-nullable by default.
    // NOTE(dimvar): This adds ! in front of type variables as well.
    // Minor issue, not worth fixing for now.
    // To fix, we must first transpile declarations of generic types, collect
    // the type variables in scope, and use them during transpilation.
    return new Node(Token.BANG, IR.string(dotted));
  }

  private void maybeCreateQualifiedDeclaration(Node n, Node parent) {
    if (currNamespace != null) {
      Node name = n.getFirstChild();
      String oldName = name.getString();
      String qName = maybePrependCurrNamespace(oldName);
      Node newName = n.isFunction() ? IR.name("") : IR.empty();
      newName.useSourceInfoFrom(n);
      n.replaceChild(name, newName);

      Node placeHolder = IR.empty();
      parent.replaceChild(n, placeHolder);
      Node newDec = NodeUtil.newQNameDeclaration(
          compiler,
          qName,
          n,
          n.getJSDocInfo()).useSourceInfoFromForTree(n);
      n.setJSDocInfo(null);
      parent.replaceChild(placeHolder, newDec);
      compiler.reportCodeChange();
    }
  }

  private Node convertMemberFunctionToMemberVariable(Node member) {
    Node function = member.getFirstChild();
    Node memberVariable = Node.newString(Token.MEMBER_VARIABLE_DEF, member.getString());
    memberVariable.useSourceInfoFrom(member);
    if (!processedOverloads.contains(function)) {
      Node returnType = maybeCreateAnyType(function, function.getDeclaredTypeExpression());
      LinkedHashMap<String, TypeDeclarationNode> required = new LinkedHashMap<>();
      LinkedHashMap<String, TypeDeclarationNode> optional = new LinkedHashMap<>();
      String restName = null;
      TypeDeclarationNode restType = null;

      for (Node param : function.getSecondChild().children()) {
        if (param.isName()) {
          if (param.isOptionalEs6Typed()) {
            optional.put(param.getString(), param.getDeclaredTypeExpression());
          } else {
            required.put(param.getString(), param.getDeclaredTypeExpression());
          }
        } else if (param.isRest()) {
          restName = param.getFirstChild().getString();
          restType = param.getDeclaredTypeExpression();
        }
      }

      TypeDeclarationNode type =
          TypeDeclarationsIR.functionType(returnType, required, optional, restName, restType);
      memberVariable.setDeclaredTypeExpression(type);
    } else {
      memberVariable.setDeclaredTypeExpression(TypeDeclarationsIR.namedType("Function"));
    }

    memberVariable.putBooleanProp(Node.OPT_ES6_TYPED, function.isOptionalEs6Typed());
    member.getParent().replaceChild(member, memberVariable);
    return memberVariable;
  }

  private Node maybeGetQualifiedNameNode(Node oldNameNode) {
    if (oldNameNode.isName()) {
      String oldName = oldNameNode.getString();
      for (Namespace definitionNamespace = currNamespace; definitionNamespace != null;
          definitionNamespace = definitionNamespace.parent) {
        if (definitionNamespace.typeNames.contains(oldName)) {
          return NodeUtil.newQName(compiler, definitionNamespace.name + "." + oldName)
              .useSourceInfoFromForTree(oldNameNode);
        }
      }
    }
    return oldNameNode;
  }

  private void pushOverloads() {
    overloadStack.push(new HashMap<String, Node>());
  }

  private void popOverloads() {
    overloadStack.pop();
  }

  private String maybePrependCurrNamespace(String oldName) {
    return currNamespace == null ? oldName : currNamespace.name + "." + oldName;
  }

  private void popNamespace(Node n, Node parent) {
    if (n.getType() == Token.NAMESPACE) {
      Node parentModuleRoot;
      Node grandParent = parent.getParent();
      switch (parent.getType()) {
        case Token.DECLARE:
        case Token.EXPORT:
          if (parent.getParent().getType() == Token.EXPORT) {
            parentModuleRoot = grandParent.getGrandparent();
          } else {
            parentModuleRoot = grandParent.getParent();
          }
          break;
        default:
          parentModuleRoot = grandParent;
      }
      currNamespace = nodeNamespaceMap.get(parentModuleRoot);
    }
  }

  private class ScanNamespaces implements NodeTraversal.Callback {
    private Map<String, Namespace> namespaces = new HashMap<>();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.SCRIPT:
        case Token.NAMESPACE_ELEMENTS:
          return true;
        case Token.BLOCK:
          return n.getFirstChild() != null && n.getFirstChild().isScript();
        case Token.DECLARE:
          return n.getFirstChild().getType() == Token.NAMESPACE;
        case Token.EXPORT:
          switch (n.getFirstChild().getType()) {
            case Token.CLASS:
            case Token.INTERFACE:
            case Token.ENUM:
            case Token.TYPE_ALIAS:
            case Token.NAMESPACE:
            case Token.DECLARE:
              return true;
          }
          return false;
        case Token.NAMESPACE:
          String[] segments = n.getFirstChild().getQualifiedName().split("\\.");
          for (String s : segments) {
            String currName = maybePrependCurrNamespace(s);
            if (!namespaces.containsKey(currName)) {
              currNamespace = new Namespace(currName, currNamespace);
              namespaces.put(currName, currNamespace);
            }
            currNamespace = namespaces.get(currName);
          }
          nodeNamespaceMap.put(n, currNamespace);
          return true;
        case Token.CLASS:
        case Token.INTERFACE:
        case Token.ENUM:
          if (currNamespace != null) {
            currNamespace.typeNames.add(n.getFirstChild().getString());
          }
          return true;
        case Token.TYPE_ALIAS:
          if (currNamespace != null) {
            currNamespace.typeNames.add(n.getString());
          }
          return true;
      }
      return false;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      popNamespace(n, parent);
    }
  }

  private static class Namespace {
    private final String name;
    private Set<String> typeNames;
    private Namespace parent;

    private Namespace(String name, Namespace parent) {
      this.name = name;
      this.parent = parent;
      this.typeNames = new HashSet<>();
    }
  }
}
