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
import com.google.javascript.jscomp.Es6ToEs3Converter.ClassDeclarationMetadata;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TypeDeclarationsIR;

/**
 * Converts {@link Node#getDeclaredTypeExpression()} to {@link JSDocInfo#getType()} type
 * annotations. Types are marked as inline types.
 */
public final class Es6TypedToEs6Converter
    extends AbstractPostOrderCallback implements HotSwapCompilerPass {
  static final DiagnosticType CANNOT_CONVERT_MEMBER_VARIABLES = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_FIELDS",
      "Can only convert class member variables (fields) in declarations or the right hand side of "
          + "a simple assignment.");

  static final DiagnosticType CANNOT_CONVERT_BOUNDED_GENERICS = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_BOUNDED_GENERICS",
      "Bounded generics are not yet implemented.");

  static final DiagnosticType TYPE_ALIAS_ALREADY_DECLARED = DiagnosticType.error(
      "JSC_TYPE_ALIAS_ALREADY_DECLARED",
      "Type alias already declared as a variable: {0}");

  static final DiagnosticType TYPE_QUERY_NOT_SUPPORTED = DiagnosticType.error(
      "JSC_TYPE_QUERY_NOT_SUPPORTED",
      "Type query is currently not supported.");

  private final AbstractCompiler compiler;

  Es6TypedToEs6Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node scriptRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.CLASS:
        maybeAddGenerics(n, n);
        visitClass(n, parent);
        break;
      case Token.INTERFACE:
        maybeAddGenerics(n, n);
        visitInterface(n);
        break;
      case Token.ENUM:
        visitEnum(n, parent);
        break;
      case Token.NAME:
      case Token.REST:
        maybeVisitColonType(n, n);
        break;
      case Token.FUNCTION:
        // For member functions (eg. class Foo<T> { f() {} }), the JSDocInfo
        // needs to go on the synthetic MEMBER_FUNCTION_DEF node.
        Node jsDocNode = parent.getType() == Token.MEMBER_FUNCTION_DEF
            ? parent
            : n;
        maybeAddGenerics(n, jsDocNode);
        maybeVisitColonType(n, jsDocNode); // Return types are colon types on the function node
        break;
      case Token.TYPE_ALIAS:
        visitTypeAlias(t, n, parent);
        break;
      case Token.DECLARE:
        visitAmbientDeclaration(n, parent);
        break;
      default:
    }
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
          return;
        }
      }
      name.putProp(Node.GENERIC_TYPE_LIST, null);
      jsDocNode.setJSDocInfo(doc.build());
    }
  }

  private void visitClass(Node n, Node parent) {
    Node interfaces = (Node) n.getProp(Node.IMPLEMENTS);
    if (interfaces != null) {
      JSDocInfoBuilder doc = JSDocInfoBuilder.maybeCopyFrom(n.getJSDocInfo());
      for (Node child : interfaces.children()) {
        Node type = convertWithLocation(child);
        doc.recordImplementedInterface(new JSTypeExpression(type, n.getSourceFileName()));
      }
      n.putProp(Node.IMPLEMENTS, null);
      n.setJSDocInfo(doc.build());
    }

    Node classMembers = n.getLastChild();
    ClassDeclarationMetadata metadata = ClassDeclarationMetadata.create(n, parent);

    for (Node member : classMembers.children()) {
      // Functions are handled by the regular Es6ToEs3Converter
      if (!member.isMemberVariableDef() && !member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE)) {
        continue;
      }

      if (metadata == null) {
        compiler.report(JSError.make(n, CANNOT_CONVERT_MEMBER_VARIABLES));
        return;
      }

      metadata.insertNodeAndAdvance(createPropertyDefinition(member, metadata.fullClassName));
      compiler.reportCodeChange();
    }
  }

  private void visitInterface(Node n) {
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
    n.setJSDocInfo(doc.build());

    Node insertionPoint = n;
    Node members = n.getLastChild();
    for (Node member : members.children()) {
      // Synthesize a block for method signatures.
      if (member.isMemberFunctionDef()) {
        Node function = member.getFirstChild();
        function.getLastChild().setType(Token.BLOCK);
        continue;
      }

      Node newNode = createPropertyDefinition(member, name.getString());
      insertionPoint.getParent().addChildAfter(newNode, insertionPoint);
      insertionPoint = newNode;
    }

    // Convert interface to class
    n.setType(Token.CLASS);
    Node empty = new Node(Token.EMPTY).useSourceInfoIfMissingFrom(n);
    n.replaceChild(superTypes, empty);
    members.setType(Token.CLASS_MEMBERS);
    compiler.reportCodeChange();
  }

  private Node createPropertyDefinition(Node member, String name) {
    member.detachFromParent();
    Node nameAccess = NodeUtil.newQName(compiler, name);
    Node prototypeAcess = NodeUtil.newPropertyAccess(compiler, nameAccess, "prototype");
    Node qualifiedMemberAccess =
        Es6ToEs3Converter.getQualifiedMemberAccess(compiler, member, nameAccess,
            prototypeAcess);
    // Copy type information.
    maybeVisitColonType(member, member);
    qualifiedMemberAccess.setJSDocInfo(member.getJSDocInfo());
    Node newNode = NodeUtil.newExpr(qualifiedMemberAccess);
    return newNode.useSourceInfoIfMissingFromForTree(member);
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
    Node var = IR.var(name.detachFromParent());
    Node objectlit = IR.objectlit(stringKeys);
    name.addChildToFront(objectlit);

    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordEnumParameterType(
        new JSTypeExpression(IR.string("number"), n.getSourceFileName()));
    var.setJSDocInfo(builder.build());

    parent.replaceChild(n, var.useSourceInfoIfMissingFromForTree(n));
    compiler.reportCodeChange();
  }

  private void maybeClearColonType(Node n, boolean hasColonType) {
    if (hasColonType) {
      n.setDeclaredTypeExpression(null);
      compiler.reportCodeChange();
    }
  }

  private void maybeVisitColonType(Node n, Node jsDocNode) {
    Node type = n.getDeclaredTypeExpression();
    boolean hasColonType = type != null;
    if (n.isRest() && hasColonType) {
      type = new Node(Token.ELLIPSIS, convertWithLocation(type.removeFirstChild()));
    } else {
      type = maybeProcessOptionalParameter(n, type);
    }
    if (type == null) {
      return;
    }

    JSDocInfo info = jsDocNode.getJSDocInfo();
    Preconditions.checkState(info == null || info.getType() == null,
        "Nodes must not have both type declarations and JSDoc types");
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(info);

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

    info = builder.build();
    jsDocNode.setJSDocInfo(info);

    maybeClearColonType(n, hasColonType);
  }

  private void visitTypeAlias(NodeTraversal t, Node n, Node parent) {
    String alias = n.getString();
    if (t.getScope().isDeclared(alias, true)) {
      compiler.report(
          JSError.make(n, TYPE_ALIAS_ALREADY_DECLARED, alias));
    }
    Node var = IR.var(IR.name(n.getString())).useSourceInfoFromForTree(n);
    JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
    builder.recordTypedef(new JSTypeExpression(
        convertWithLocation(n.getFirstChild()), n.getSourceFileName()));
    var.setJSDocInfo(builder.build());
    parent.replaceChild(n, var);
    compiler.reportCodeChange();
  }

  private void visitAmbientDeclaration(Node n, Node parent) {
    Node child = n.removeFirstChild();
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(child.getJSDocInfo());
    builder.addSuppression("duplicate");
    switch (child.getType()) {
      case Token.FUNCTION:
        child.replaceChild(child.getLastChild(), IR.block().useSourceInfoFrom(child));
        break;
      case Token.CLASS:
        Node members = child.getLastChild();
        for (Node member : members.children()) {
          if (member.isMemberFunctionDef()) {
            Node function = member.getFirstChild();
            function.replaceChild(
                function.getLastChild(), IR.block().copyInformationFrom(function));
          }
        }
        break;
      case Token.LET:
        child.setType(Token.VAR);
        break;
      case Token.CONST:
        builder.recordConstancy();
        child.setType(Token.VAR);
        break;
    }
    child.setJSDocInfo(builder.build());

    parent.replaceChild(n, child);
    compiler.reportCodeChange();
  }

  private Node maybeCreateAnyType(Node n, Node type) {
    return type == null ? TypeDeclarationsIR.anyType().copyInformationFrom(n) : type;
  }

  private Node maybeProcessOptionalParameter(Node n, Node type) {
    if (n.getBooleanProp(Node.OPT_PARAM_ES6_TYPED)) {
      n.putBooleanProp(Node.OPT_PARAM_ES6_TYPED, false);
      type = maybeCreateAnyType(n, type);
      return new Node(Token.EQUALS, convertWithLocation(type));
    } else {
      return type == null ? null : convertWithLocation(type);
    }
  }

  private Node convertWithLocation(Node type) {
    return convertDeclaredTypeToJSDoc(type).copyInformationFrom(type);
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
      case Token.ANY_TYPE:
        return new Node(Token.QMARK);
        // Named types.
      case Token.NAMED_TYPE:
        return convertNamedType(type);
      case Token.ARRAY_TYPE: {
        Node arrayType = IR.string("Array");
        Node memberType = convertWithLocation(type.getFirstChild());
        arrayType.addChildToFront(new Node(Token.BLOCK, memberType).copyInformationFrom(type));
        return new Node(Token.BANG, arrayType);
      }
      case Token.PARAMETERIZED_TYPE: {
        Node namedType = type.getFirstChild();
        Node result = convertWithLocation(namedType);
        Node typeParameterTarget =
            result.getType() == Token.BANG ? result.getFirstChild() : result;
        Node parameters = IR.block().copyInformationFrom(type);
        typeParameterTarget.addChildToFront(parameters);
        for (Node param = namedType.getNext(); param != null; param = param.getNext()) {
          parameters.addChildToBack(convertWithLocation(param));
        }
        return result;
      }
      // Composite types.
      case Token.FUNCTION_TYPE:
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
        function.addChildToBack(paramList);
        function.addChildToBack(convertWithLocation(returnType));
        return function;
      case Token.UNION_TYPE:
        Node pipe = new Node(Token.PIPE);
        for (Node child : type.children()) {
          pipe.addChildToBack(convertWithLocation(child));
        }
        return pipe;
      case Token.RECORD_TYPE:
        Node lb = new Node(Token.LB);
        for (Node stringKey : type.children()) {
          Node colon = new Node(Token.COLON);
          Node original = stringKey.removeFirstChild();
          colon.addChildToBack(stringKey.detachFromParent());
          colon.addChildToBack(convertWithLocation(original));
          lb.addChildrenToBack(colon);
        }
        return new Node(Token.LC, lb);
      case Token.TYPEOF:
        // Currently, TypeQuery is not supported in Closure's type system.
        compiler.report(JSError.make(type, TYPE_QUERY_NOT_SUPPORTED));
        return new Node(Token.QMARK);
      default:
        // TODO(moz): Implement.
        break;
    }
    throw new IllegalArgumentException(
        "Unexpected node type for type conversion: " + type.getType());
  }

  private Node convertNamedType(Node type) {
    Node propTree = type.getFirstChild();
    String dotted = propTree.getQualifiedName();
    // In the native type syntax, nominal types are non-nullable by default.
    // NOTE(dimvar): This adds ! in front of type variables as well.
    // Minor issue, not worth fixing for now.
    // To fix, we must first transpile declarations of generic types, collect
    // the type variables in scope, and use them during transpilation.
    return new Node(Token.BANG, IR.string(dotted));
  }
}
