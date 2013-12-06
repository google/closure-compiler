/*
 * Copyright 2013 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.Token;

import java.util.Map;
import java.util.Set;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class JSTypeCreatorFromJSDoc {

  /** Exception for when unrecognized type names are encountered */
  public static class UnknownTypeException extends Exception {
    UnknownTypeException(String cause) {
      super(cause);
    }
  }

  private SimpleErrorReporter reporter = new SimpleErrorReporter();

  public JSType getNodeTypeDeclaration(
      JSDocInfo jsdoc, DeclaredTypeRegistry registry) {
    if (jsdoc == null) {
      return null;
    }
    return getTypeFromJSTypeExpression(jsdoc.getType(), registry);
  }

  public Set<String> getWarnings() {
    Set<String> warnings = Sets.newHashSet();
    if (reporter.warnings() != null) {
      warnings.addAll(reporter.warnings());
    }
    return warnings;
  }

  public JSType getTypeFromJSTypeExpression(
      JSTypeExpression expr, DeclaredTypeRegistry registry) {
    if (expr == null) {
      return null;
    }
    JSType result = getTypeFromNode(expr.getRootNode(), registry);
    return result;
  }

  // Very similar to JSTypeRegistry#createFromTypeNodesInternal
  // n is a jsdoc node, not an AST node; the same class (Node) is used for both
  JSType getTypeFromNode(Node n, DeclaredTypeRegistry registry) {
    try {
      return getTypeFromNodeHelper(n, registry);
    } catch (UnknownTypeException e) {
      warn("Unknown type", n);
      return null;
    }
  }

  private JSType getTypeFromNodeHelper(Node n, DeclaredTypeRegistry registry)
      throws UnknownTypeException {
    Preconditions.checkNotNull(n);
    switch (n.getType()) {
      case Token.LC: {
        Map<String, JSType> fields = Maps.newHashMap();
        // For each of the fields in the record type.
        for (Node fieldTypeNode = n.getFirstChild().getFirstChild();
             fieldTypeNode != null;
             fieldTypeNode = fieldTypeNode.getNext()) {
          Preconditions.checkState(fieldTypeNode.getType() == Token.COLON);

          Node fieldNameNode = fieldTypeNode.getFirstChild();
          String fieldName = fieldNameNode.getString();
          if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
            fieldName = fieldName.substring(1, fieldName.length() - 1);
          }
          JSType fieldType =
              getTypeFromNodeHelper(fieldTypeNode.getLastChild(), registry);
          // TODO(blickly): Allow optional properties
          fields.put(fieldName, fieldType);
        }
        return JSType.fromObjectType(ObjectType.fromProperties(fields));
      }
      case Token.EMPTY: // for function types that don't declare a return type
        return JSType.UNKNOWN;
      case Token.STRING:
        String typeName = n.getString();
        if (typeName.equals("boolean")) {
          return JSType.BOOLEAN;
        } else if (typeName.equals("null")) {
          return JSType.NULL;
        } else if (typeName.equals("number")) {
          return JSType.NUMBER;
        }  else if (typeName.equals("string")) {
          return JSType.STRING;
        } else if (typeName.equals("undefined")) {
          return JSType.UNDEFINED;
        } else { // it must be a class name
          JSType namedType = registry.getNamedTypeByName(typeName);
          if (namedType != null) {
            return namedType;
          }
          throw new UnknownTypeException("Unhandled type: " + typeName);
        }
      case Token.PIPE: {
        JSType union = JSType.BOTTOM;
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          union = JSType.join(union, getTypeFromNodeHelper(child, registry));
        }
        return union;
      }
      case Token.BANG: {
        return getTypeFromNodeHelper(n.getFirstChild(), registry)
          .removeType(JSType.NULL);
      }
      case Token.QMARK: {
        Node child = n.getFirstChild();
        if (child == null) {
          return JSType.UNKNOWN;
        } else {
          return JSType.join(JSType.NULL, getTypeFromNodeHelper(child, registry));
        }
      }
      case Token.STAR:
        return JSType.TOP;
      case Token.FUNCTION: {
        FunctionTypeBuilder builder = new FunctionTypeBuilder();
        Node child = n.getFirstChild();
        if (child.getType() == Token.PARAM_LIST) {
          for (Node arg = child.getFirstChild(); arg != null;
               arg = arg.getNext()) {
            try {
              switch (arg.getType()) {
                case Token.EQUALS:
                  builder.addOptFormal(
                      getTypeFromNodeHelper(arg.getFirstChild(), registry));
                  break;
                case Token.ELLIPSIS:
                  builder.addRestFormals(
                      getTypeFromNodeHelper(arg.getFirstChild(), registry));
                  break;
                default:
                  builder.addReqFormal(getTypeFromNodeHelper(arg, registry));
                  break;
              }
            } catch (IllegalStateException e) {
              warn("Wrong parameter order: required parameters are first, " +
                  "then optional, then varargs", n);
            }
          }
          child = child.getNext();
        }
        builder.addRetType(getTypeFromNodeHelper(child, registry));
        return builder.buildType();
      }
      default:
        throw new IllegalArgumentException("Unsupported type exp: " +
            Token.name(n.getType()) + " " + n.toStringTree());
    }
  }

  public boolean hasKnownType(
      Node n, DeclaredTypeRegistry registry) {
    try {
      getTypeFromNodeHelper(n, registry);
    } catch (UnknownTypeException e) {
      return false;
    }
    return true;
  }

  public NominalType getClassType(Node n, DeclaredTypeRegistry registry) {
    Preconditions.checkArgument(n.getType() == Token.BANG);
    JSType wrappedClass = getTypeFromNode(n, registry);
    Preconditions.checkState(wrappedClass != null);
    return wrappedClass.getClassTypeIfUnique();
  }

  public ImmutableSet<NominalType> getImplementedInterfaces(
      JSDocInfo jsdoc, DeclaredTypeRegistry registry) {
    ImmutableSet.Builder<NominalType> builder = ImmutableSet.builder();
    for (JSTypeExpression texp: jsdoc.getImplementedInterfaces()) {
      Node expRoot = texp.getRootNode();
      if (hasKnownType(expRoot, registry)) {
        builder.add(getClassType(expRoot, registry));
      } else {
        warn("Cannot implement unknown type", expRoot);
      }
    }
    return builder.build();
  }

  public ImmutableSet<NominalType> getExtendedInterfaces(
      JSDocInfo jsdoc, DeclaredTypeRegistry registry) {
    ImmutableSet.Builder<NominalType> builder = ImmutableSet.builder();
    for (JSTypeExpression texp: jsdoc.getExtendedInterfaces()) {
      builder.add(getClassType(texp.getRootNode(), registry));
    }
    return builder.build();
  }

  /**
   * Consumes either a "classic" function jsdoc with @param, @return, etc,
   * or a jsdoc with @type{function ...} and finds the types of the formal
   * parameters and the return value. It returns a builder because the callers
   * of this function must separately handle @constructor, @interface, etc.
   */
  public FunctionTypeBuilder getFunctionType(
      JSDocInfo jsdoc, Node funNode, DeclaredTypeRegistry registry) {
    try {
      if (jsdoc != null && jsdoc.getType() != null) {
        Node jsdocNode = jsdoc.getType().getRootNode();
        if (jsdocNode.getType() == Token.FUNCTION) {
          return getFunTypeFromAtTypeJsdoc(jsdoc, funNode, registry);
        } else {
          warn("The function is annotated with a non-function jsdoc. " +
              "Ignoring jsdoc.", funNode);
        }
      }
      return getFunTypeFromTypicalFunctionJsdoc(
          jsdoc, funNode, registry, false);
    } catch (IllegalStateException e) {
      warn("Wrong parameter order: required parameters are first, " +
          "then optional, then varargs. Ignoring jsdoc.", funNode);
      return getFunTypeFromTypicalFunctionJsdoc(null, funNode, registry, true);
    }
  }

  private FunctionTypeBuilder getFunTypeFromAtTypeJsdoc(
      JSDocInfo jsdoc, Node funNode, DeclaredTypeRegistry registry) {
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node childJsdoc = jsdoc.getType().getRootNode().getFirstChild();
    Node param = funNode.getFirstChild().getNext().getFirstChild();
    Node paramType;
    boolean warnedForMissingTypes = false;
    boolean warnedForInlineJsdoc = false;

    if (childJsdoc.getType() == Token.PARAM_LIST) {
      paramType = childJsdoc.getFirstChild();
      childJsdoc = childJsdoc.getNext(); // go to the return type
    } else { // empty parameter list
      paramType = null;
    }

    while (param != null) {
      if (paramType == null) {
        if (!warnedForMissingTypes) {
          warn("The function has more formal parameters than the types " +
              "declared in the JSDoc", funNode);
          warnedForMissingTypes = true;
        }
        builder.addOptFormal(null);
      } else {
        if (!warnedForInlineJsdoc && param.getJSDocInfo() != null) {
          warn("The function cannot have both an @type jsdoc and inline " +
              "jsdocs. Ignoring inline jsdocs.", param);
          warnedForInlineJsdoc = true;
        }
        switch (paramType.getType()) {
          case Token.EQUALS:
            builder.addOptFormal(getTypeFromNode(
                    paramType.getFirstChild(), registry));
            break;
          case Token.ELLIPSIS:
            if (!warnedForMissingTypes) {
              warn("The function has more formal parameters than the types " +
                  "declared in the JSDoc", funNode);
              warnedForMissingTypes = true;
              builder.addOptFormal(null);
            }
            break;
          default:
            builder.addReqFormal(
                getTypeFromNode(paramType, registry));
            break;
        }
        paramType = paramType.getNext();
      }
      param = param.getNext();
    }

    if (paramType != null) {
      if (paramType.getType() == Token.ELLIPSIS) {
        builder.addRestFormals(
            getTypeFromNode(paramType.getFirstChild(), registry));
      } else {
        warn("The function has fewer formal parameters than the types " +
            "declared in the JSDoc", funNode);
      }
    }
    if (!warnedForInlineJsdoc &&
        funNode.getFirstChild().getJSDocInfo() != null) {
      warn("The function cannot have both an @type jsdoc and inline " +
          "jsdocs. Ignoring the inline return jsdoc.", funNode);
    }
    if (jsdoc.getReturnType() != null) {
      warn("The function cannot have both an @type jsdoc and @return " +
          "jsdoc. Ignoring @return jsdoc.", funNode);
    }
    builder.addRetType(getTypeFromNode(childJsdoc, registry));

    return builder;
  }

  private FunctionTypeBuilder getFunTypeFromTypicalFunctionJsdoc(
      JSDocInfo jsdoc, Node funNode, DeclaredTypeRegistry registry,
      boolean ignoreJsdoc /* for when the jsdoc is malformed */) {
    Preconditions.checkArgument(!ignoreJsdoc || jsdoc == null);
    FunctionTypeBuilder builder = new FunctionTypeBuilder();
    Node params = funNode.getFirstChild().getNext();
    for (Node param = params.getFirstChild();
         param != null;
         param = param.getNext()) {
      String pname = param.getQualifiedName();
      JSType inlineParamType = ignoreJsdoc ? null :
          getNodeTypeDeclaration(param.getJSDocInfo(), registry);
      boolean isRequired = true, isRestFormals = false;
      JSTypeExpression texp = jsdoc == null ?
          null : jsdoc.getParameterType(pname);
      Node jsdocNode = texp == null ? null : texp.getRootNode();
      if (jsdocNode != null && jsdocNode.getType() == Token.EQUALS) {
        isRequired = false;
        jsdocNode = jsdocNode.getFirstChild();
      } else if (jsdocNode != null && jsdocNode.getType() == Token.ELLIPSIS) {
        isRequired = false;
        isRestFormals = true;
        jsdocNode = jsdocNode.getFirstChild();
      }
      JSType fnParamType = null;
      if (jsdocNode != null) {
        fnParamType = getTypeFromNode(jsdocNode, registry);
      }
      if (inlineParamType != null) {
        // TODO(user): The support for inline optional parameters is currently
        // broken, so this is always a required parameter. See b/11481388. Fix.
        builder.addReqFormal(inlineParamType);
        if (fnParamType != null) {
          warn("Found two JsDoc comments for formal parameter " + pname, param);
        }
      } else if (isRequired) {
        builder.addReqFormal(fnParamType);
      } else if (isRestFormals) {
        builder.addRestFormals(fnParamType);
      } else {
        builder.addOptFormal(fnParamType);
      }
    }

    JSDocInfo inlineRetJsdoc = ignoreJsdoc ? null :
        funNode.getFirstChild().getJSDocInfo();
    JSTypeExpression retTypeExp = jsdoc == null ? null : jsdoc.getReturnType();
    if (inlineRetJsdoc != null) {
      builder.addRetType(getNodeTypeDeclaration(inlineRetJsdoc, registry));
      if (retTypeExp != null) {
        warn("Found two JsDoc comments for the return type", funNode);
      }
    } else {
      builder.addRetType(getTypeFromJSTypeExpression(retTypeExp, registry));
    }

    return builder;
  }

  void warn(String msg, Node faultyNode) {
    reporter.warning(msg, faultyNode.getSourceFileName(),
        faultyNode.getLineno(), faultyNode.getCharno());
  }

}
