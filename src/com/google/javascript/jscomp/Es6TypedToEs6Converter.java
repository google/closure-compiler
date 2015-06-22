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
      case Token.NAME:
      case Token.REST:
        visitColonType(n);
        break;
      case Token.FUNCTION:
        // For member functions (eg. class Foo<T> { f() {} }), the JSDocInfo
        // needs to go on the synthetic MEMBER_FUNCTION_DEF node.
        maybeAddGenerics(n, parent.getType() == Token.MEMBER_FUNCTION_DEF
            ? parent
            : n);
        visitColonType(n); // Return types are colon types on the function node
        break;
      default:

    }
  }

  private void maybeAddGenerics(Node src, Node dst) {
    Node name = src.getFirstChild();
    Node generics = (Node) name.getProp(Node.GENERIC_TYPE_LIST);
    if (generics != null) {
      JSDocInfoBuilder doc = JSDocInfoBuilder.maybeCopyFrom(dst.getJSDocInfo());
      // Discard the type bound (the "extends" part) for now
      for (Node typeName : generics.children()) {
        doc.recordTemplateTypeName(typeName.getString());
        if (typeName.hasChildren()) {
          compiler.report(JSError.make(name, CANNOT_CONVERT_BOUNDED_GENERICS));
          return;
        }
      }
      name.putProp(Node.GENERIC_TYPE_LIST, null);
      dst.setJSDocInfo(doc.build());
    }
  }

  private void visitClass(Node n, Node parent) {
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

      member.getParent().removeChild(member);

      Node classNameAccess = NodeUtil.newQName(compiler, metadata.fullClassName);
      Node prototypeAcess = NodeUtil.newPropertyAccess(compiler, classNameAccess, "prototype");
      Node qualifiedMemberAccess =
          Es6ToEs3Converter.getQualifiedMemberAccess(compiler, member, classNameAccess,
              prototypeAcess);
      // Copy type information.
      visitColonType(member);
      qualifiedMemberAccess.setJSDocInfo(member.getJSDocInfo());
      Node newNode = NodeUtil.newExpr(qualifiedMemberAccess);
      newNode.useSourceInfoIfMissingFromForTree(member);
      metadata.insertNodeAndAdvance(newNode);
      compiler.reportCodeChange();
    }
  }

  private void visitColonType(Node n) {
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

    JSDocInfo info = n.getJSDocInfo();
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
    n.setJSDocInfo(info);

    if (hasColonType) {
      n.setDeclaredTypeExpression(null); // clear out declared type
      compiler.reportCodeChange();
    }
  }

  private Node maybeCreateAnyType(Node type) {
    return type == null ? TypeDeclarationsIR.anyType() : type;
  }

  private Node maybeProcessOptionalParameter(Node n, Node type) {
    if (n.getBooleanProp(Node.OPT_PARAM_ES6_TYPED)) {
      n.putBooleanProp(Node.OPT_PARAM_ES6_TYPED, false);
      type = maybeCreateAnyType(type);
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
            paramType = maybeProcessOptionalParameter(param, maybeCreateAnyType(paramType));
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
