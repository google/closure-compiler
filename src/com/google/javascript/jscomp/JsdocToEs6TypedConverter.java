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

import static com.google.javascript.rhino.TypeDeclarationsIR.anyType;
import static com.google.javascript.rhino.TypeDeclarationsIR.arrayType;
import static com.google.javascript.rhino.TypeDeclarationsIR.booleanType;
import static com.google.javascript.rhino.TypeDeclarationsIR.functionType;
import static com.google.javascript.rhino.TypeDeclarationsIR.namedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.numberType;
import static com.google.javascript.rhino.TypeDeclarationsIR.optionalParameter;
import static com.google.javascript.rhino.TypeDeclarationsIR.parameterizedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.recordType;
import static com.google.javascript.rhino.TypeDeclarationsIR.stringType;
import static com.google.javascript.rhino.TypeDeclarationsIR.undefinedType;
import static com.google.javascript.rhino.TypeDeclarationsIR.unionType;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.Token;

import java.util.LinkedHashMap;

import javax.annotation.Nullable;

/**
 * Converts JS with types in jsdocs to an extended JS syntax that includes types.
 * (Still keeps the jsdocs intact.)
 *
 * @author alexeagle@google.com (Alex Eagle)
 *
 * TODO(alexeagle): handle inline-style JSDoc annotations as well.
 */
public final class JsdocToEs6TypedConverter
    extends AbstractPostOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  public JsdocToEs6TypedConverter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo bestJSDocInfo = NodeUtil.getBestJSDocInfo(n);
    switch (n.getType()) {
      case Token.FUNCTION:
        if (bestJSDocInfo != null) {
          setTypeExpression(n, bestJSDocInfo.getReturnType());
        }
        break;
      case Token.NAME:
      case Token.GETPROP:
        if (parent == null) {
          break;
        }
        if (parent.isVar() || parent.isAssign() || parent.isExprResult()) {
          if (bestJSDocInfo != null) {
            setTypeExpression(n, bestJSDocInfo.getType());
          }
        } else if (parent.isParamList()) {
          JSDocInfo parentDocInfo = NodeUtil.getBestJSDocInfo(parent.getParent());
          if (parentDocInfo == null) {
            break;
          }
          JSTypeExpression parameterType =
              parentDocInfo.getParameterType(n.getString());
          if (parameterType != null) {
            Node attachTypeExpr = n;
            // Modify the primary AST to represent a function parameter as a
            // REST node, if the type indicates it is a rest parameter.
            if (parameterType.getRoot().getType() == Token.ELLIPSIS) {
              attachTypeExpr = IR.rest(n.getString());
              n.getParent().replaceChild(n, attachTypeExpr);
              compiler.reportCodeChange();
            }
            setTypeExpression(attachTypeExpr, parameterType);
          }
        }
        break;
      default:
        break;
    }
  }

  private void setTypeExpression(Node n, JSTypeExpression type) {
    TypeDeclarationNode node = TypeDeclarationsIRFactory.convert(type);
    if (node != null) {
      n.setDeclaredTypeExpression(node);
      compiler.reportCodeChange();
    }
  }

  /**
   * Converts root nodes of JSTypeExpressions into TypeDeclaration ASTs.
   *
   * @author alexeagle@google.com (Alex Eagle)
   */
  public static final class TypeDeclarationsIRFactory {

    // Allow functional-style Iterables.transform over collections of nodes.
    private static final Function<Node, TypeDeclarationNode> CONVERT_TYPE_NODE =
        new Function<Node, TypeDeclarationNode>() {
          @Override
          public TypeDeclarationNode apply(Node node) {
            return convertTypeNodeAST(node);
          }
        };

    @Nullable
    public static TypeDeclarationNode convert(@Nullable JSTypeExpression typeExpr) {
      if (typeExpr == null) {
        return null;
      }
      return convertTypeNodeAST(typeExpr.getRoot());
    }

    /**
     * The root of a JSTypeExpression is very different from an AST node, even
     * though we use the same Java class to represent them.
     * This function converts root nodes of JSTypeExpressions into TypeDeclaration ASTs,
     * to make them more similar to ordinary AST nodes.
     *
     * @return the root node of a TypeDeclaration AST, or null if no type is
     *         available for the node.
     */
    // TODO(dimvar): Eventually, we want to just parse types to the new
    // representation directly, and delete this function.
    @Nullable
    public static TypeDeclarationNode convertTypeNodeAST(Node n) {
      int token = n.getType();
      switch (token) {
        case Token.STAR:
        case Token.EMPTY: // for function types that don't declare a return type
          return anyType();
        case Token.VOID:
          return undefinedType();
        case Token.BANG:
          // TODO(alexeagle): non-nullable is assumed to be the default
          return convertTypeNodeAST(n.getFirstChild());
        case Token.STRING:
          String typeName = n.getString();
          switch (typeName) {
            case "boolean":
              return booleanType();
            case "number":
              return numberType();
            case "string":
              return stringType();
            case "null":
            case "undefined":
            case "void":
              return null;
            default:
              TypeDeclarationNode root = namedType(typeName);
              if (n.getChildCount() > 0 && n.getFirstChild().isBlock()) {
                Node block = n.getFirstChild();
                if ("Array".equals(typeName)) {
                  return arrayType(convertTypeNodeAST(block.getFirstChild()));
                }
                return parameterizedType(root,
                    Iterables.filter(
                        Iterables.transform(block.children(), CONVERT_TYPE_NODE),
                        Predicates.notNull()));
              }
              return root;
          }
        case Token.QMARK:
          Node child = n.getFirstChild();
          return child == null
              ? anyType()
              // For now, our ES6_TYPED language doesn't support nullable
              // so we drop it before building the tree.
              // : nullable(convertTypeNodeAST(child));
              : convertTypeNodeAST(child);
        case Token.LC:
          LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
          for (Node field : n.getFirstChild().children()) {
            boolean isFieldTypeDeclared = field.getType() == Token.COLON;
            Node fieldNameNode = isFieldTypeDeclared ? field.getFirstChild() : field;
            String fieldName = fieldNameNode.getString();
            if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
              fieldName = fieldName.substring(1, fieldName.length() - 1);
            }
            TypeDeclarationNode fieldType = isFieldTypeDeclared
                ? convertTypeNodeAST(field.getLastChild()) : null;
            properties.put(fieldName, fieldType);
          }
          return recordType(properties);
        case Token.ELLIPSIS:
          return arrayType(convertTypeNodeAST(n.getFirstChild()));
        case Token.PIPE:
          ImmutableList<TypeDeclarationNode> types = FluentIterable
              .from(n.children()).transform(CONVERT_TYPE_NODE)
              .filter(Predicates.notNull()).toList();
          switch (types.size()) {
            case 0:
              return null;
            case 1:
              return types.get(0);
            default:
              return unionType(types);
          }
        case Token.FUNCTION:
          Node returnType = anyType();
          LinkedHashMap<String, TypeDeclarationNode> requiredParams = new LinkedHashMap<>();
          LinkedHashMap<String, TypeDeclarationNode> optionalParams = new LinkedHashMap<>();
          String restName = null;
          TypeDeclarationNode restType = null;
          for (Node child2 : n.children()) {
            if (child2.isParamList()) {
              int paramIdx = 1;
              for (Node param : child2.children()) {
                String paramName = "p" + paramIdx++;
                if (param.getType() == Token.ELLIPSIS) {
                  if (param.getFirstChild() != null) {
                    restType = arrayType(convertTypeNodeAST(param.getFirstChild()));
                  }
                  restName = paramName;
                } else {
                  TypeDeclarationNode paramNode = convertTypeNodeAST(param);
                  if (paramNode.getType() == Token.OPTIONAL_PARAMETER) {
                    optionalParams.put(paramName,
                        (TypeDeclarationNode) paramNode.removeFirstChild());
                  } else {
                    requiredParams.put(paramName, convertTypeNodeAST(param));
                  }
                }
              }
            } else if (child2.isNew()) {
              // TODO(alexeagle): keep the constructor signatures on the tree, and emit them following
              // the syntax in TypeScript 1.4 spec, section 3.7.8 Constructor Type Literals
            } else if (child2.isThis()) {
              // Not expressible in TypeScript syntax, so we omit them from the tree.
              // They could be added as properties on the result node.
            } else {
              returnType = convertTypeNodeAST(child2);
            }
          }
          return functionType(returnType, requiredParams, optionalParams, restName, restType);
        case Token.EQUALS:
          TypeDeclarationNode optionalParam = convertTypeNodeAST(n.getFirstChild());
          return optionalParam == null ? null : optionalParameter(optionalParam);
        default:
          throw new IllegalArgumentException(
              "Unsupported node type: " + Token.name(n.getType())
                  + " " + n.toStringTree());
      }
    }
  }
}
