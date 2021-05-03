/*
 * Copyright 2021 The Closure Compiler Authors.
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
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.FunctionType.Parameter;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSType.Nullability;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Checks if the @override methods are missing type annotations. If they are, then this generates
 * the right type annotations for them.
 */
public final class CheckMissingOverrideTypes extends AbstractPostOrderCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;
  private static final String PLACEHOLDER_OBJ_PARAM_NAME = "objectParam";
  private static final String JSDOC_FILE_NAME = "<testFile>";
  private static final String NON_NULLABLE_OBJECT_TYPE = "!Object";

  public static final DiagnosticType OVERRIDE_WITHOUT_ALL_TYPES =
      DiagnosticType.error(
          "JSC_OVERRIDE_WITHOUT_ALL_TYPES",
          "The @override functions/properties must have param and return types specified. Here is"
              + " the replacement JSDoc for this function/property \n"
              + "{0}");

  public CheckMissingOverrideTypes(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETPROP:
        visitPropDeclaration(n);
        break;
      case FUNCTION:
        visitFunction(n);
        break;
      case MEMBER_FUNCTION_DEF:
      case GETTER_DEF:
      case SETTER_DEF:
        // Don't need to call visitFunction because this JSDoc will be visited when the function is
        // visited.
        break;
      default:
        break;
    }
  }

  @Nullable
  private JSDocInfo getOverrideJSDoc(Node n) {
    JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(n);
    return (jsDoc != null && jsDoc.isOverride()) ? jsDoc : null;
  }

  private void visitPropDeclaration(Node n) {
    JSDocInfo jsDoc = this.getOverrideJSDoc(n);
    if (jsDoc == null || jsDoc.containsTypeDeclaration()) {
      return; // type related annotation exists in overridden property
    }

    JSType type = n.getJSType();
    if (type == null) {
      return;
    }

    Node target = n.getFirstChild();
    if (!target.isThis()) {
      return; // e.g. `/** @override */ this.x;`
    }

    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(jsDoc);
    builder.recordType(new JSTypeExpression(typeToTypeAst(type), JSDOC_FILE_NAME));
    reportMissingOverrideTypes(n, builder.build());
  }

  private void visitFunction(Node function) {
    JSDocInfo jsDoc = this.getOverrideJSDoc(function);
    if (jsDoc == null) {
      return;
    }

    FunctionType fnType =
        function.getJSType() != null ? function.getJSType().toMaybeFunctionType() : null;
    if (fnType == null) {
      return; // need fnType to find and report missing types.
    }
    boolean missingParam = hasMissingParams(function, jsDoc);
    boolean missingReturn = hasMissingReturn(function, jsDoc);
    if (missingParam || missingReturn) {
      JSDocInfo completeJSDocInfo =
          createCompleteJSDocInfoForFunction(function, missingParam, missingReturn, jsDoc);
      reportMissingOverrideTypes(function, completeJSDocInfo);
    }
  }

  private boolean hasMissingParams(Node function, JSDocInfo jsDoc) {
    if (jsDoc.getType() != null) {
      // Sometimes functions are declared with @type {function(Foo, Bar)} instead of
      //   @param {Foo} foo
      //   @param {Bar} bar
      // which is fine.
      return false;
    }

    int jsDocParamCount = jsDoc.getParameterCount();

    if (jsDocParamCount == 0) {
      return hasMissingInlineParams(function);
    } else {
      Node paramList = NodeUtil.getFunctionParameters(function);
      if (!paramList.hasXChildren(jsDocParamCount)) {
        return true;
      }
    }
    return false;
  }

  /** Checks that the inline type annotations are present. */
  private boolean hasMissingInlineParams(Node function) {
    Node paramList = NodeUtil.getFunctionParameters(function);

    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      JSDocInfo jsDoc =
          param.isDefaultValue() ? param.getFirstChild().getJSDocInfo() : param.getJSDocInfo();
      if (jsDoc == null) {
        return true;
      }
    }
    return false;
  }

  private boolean hasMissingReturn(Node function, JSDocInfo jsDoc) {
    if (jsDoc.hasType() || jsDoc.isConstructor() || jsDoc.isInterface() || jsDoc.hasReturnType()) {
      return false;
    }

    if (NodeUtil.isEs6Constructor(function)) {
      // ES6 class constructors should never have "@return".
      return false;
    }

    if (function.getFirstChild().getJSDocInfo() != null) {
      // inline return
      return false;
    }

    FunctionType fnType = function.getJSType().toMaybeFunctionType();
    JSType returnType = fnType.getReturnType();
    return !returnType.isVoidType();
  }

  /**
   * Emits error for a function or property declaration node with the replacement (complete) JSDoc.
   */
  public void reportMissingOverrideTypes(Node node, JSDocInfo completeJSDocInfo) {
    compiler.report(
        JSError.make(
            node,
            OVERRIDE_WITHOUT_ALL_TYPES,
            new JSDocInfoPrinter(/* useOriginalName */ false, /* printDesc */ true)
                .print(completeJSDocInfo)));
  }

  /** Creates complete JSDocInfo for the given function node using its inferred FunctionType. */
  private JSDocInfo createCompleteJSDocInfoForFunction(
      Node fnNode, boolean missingParam, boolean missingReturn, JSDocInfo jsDocInfo) {
    checkArgument(jsDocInfo == null || jsDocInfo.isOverride(), jsDocInfo);
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(jsDocInfo);
    FunctionType fnType = fnNode.getJSType().toMaybeFunctionType();
    if (missingParam) {
      recordMissingParamAnnotations(fnNode, jsDocInfo, fnType, builder);
    }
    if (missingReturn) {
      recordMissingReturnAnnotation(fnNode, fnType, builder);
    }
    return builder.build();
  }

  private void recordMissingParamAnnotations(
      Node fnNode, JSDocInfo jsDoc, FunctionType fnType, JSDocInfo.Builder builder) {
    checkState(fnNode.isFunction(), fnNode);

    Set<String> jsDocParamNames = jsDoc.getParameterNames();
    List<String> astParamNames = getFunctionParamNamesOrPlaceholder(fnNode);
    List<Parameter> fnTypeParams = fnType.getParameters();

    for (int paramIndex = 0; paramIndex < astParamNames.size(); paramIndex++) {
      String astName = astParamNames.get(paramIndex);
      if (jsDocParamNames.contains(astName)) {
        continue;
      }

      // missing annotation for `paramName`
      Parameter fnTypeParam = fnTypeParams.get(paramIndex);

      JSType paramType = fnTypeParam.getJSType();
      if (fnTypeParam.isOptional()) {
        paramType = paramType.restrictByNotUndefined();
      }

      Node paramTypeAst = typeToTypeAst(paramType);
      if (fnTypeParam.isOptional()) {
        paramTypeAst = new Node(Token.EQUALS, paramTypeAst);
      }

      builder.recordParameter(astName, new JSTypeExpression(paramTypeAst, JSDOC_FILE_NAME));
    }
  }

  private void recordMissingReturnAnnotation(
      Node fnNode, FunctionType fnType, JSDocInfo.Builder builder) {
    checkState(fnNode.isFunction(), fnNode);
    builder.recordReturnType(
        new JSTypeExpression(typeToTypeAst(fnType.getReturnType()), JSDOC_FILE_NAME));
  }

  private static boolean omitExplicitNullability(JSType type) {
    return type.isBooleanValueType()
        || type.isNumberValueType()
        || type.isStringValueType()
        || type.isAllType()
        || type.isUnknownType()
        || type.isOnlyBigInt()
        || type.isNullType()
        || type.isSymbolValueType()
        || type.isVoidType()
        || type.isTemplateType();
  }

  private static Node typeToTypeAst(JSType type) {
    if (omitExplicitNullability(type)) {
      // Display name e.g. `<Any Type>` or `<unknown>` does not parse as a node; simply use `*` or
      // `?`.
      return JsDocInfoParser.parseTypeString(type.toString());
    }

    if (type.isLiteralObject() && type.toMaybeObjectType().getOwnPropertyNames().isEmpty()) {
      // The overridden property is inferred as an `{}` object literal type.
      // `{}` crashes JSDocInfoPrinter when printed in an annotation.
      return JsDocInfoParser.parseTypeString(NON_NULLABLE_OBJECT_TYPE);
    }

    final String typeName;
    if (type.hasDisplayName()) {
      // use display name for e.g. `!ns.enumNum` instead of `number`
      String explicitNullability = type.isNullable() ? "?" : "!";
      typeName = explicitNullability + type.getDisplayName();
    } else {
      // e.g. `{{X:!ns.Local}}`
      typeName = type.toAnnotationString(Nullability.EXPLICIT);
    }
    return JsDocInfoParser.parseTypeString(typeName);
  }

  /**
   * @param fnNode The function.
   * @return List of param names taken by this function.
   */
  private static List<String> getFunctionParamNamesOrPlaceholder(Node fnNode) {
    checkArgument(fnNode.isFunction(), fnNode);
    Node paramList = fnNode.getSecondChild();
    List<String> paramNames = new ArrayList<>();

    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      Node paramName = param.isDefaultValue() || param.isRest() ? param.getFirstChild() : param;
      if (paramName.isObjectPattern() || paramName.isArrayPattern()) {
        // e.g. `function foo({x,y} = {})` needs a single `@param {Object|undefined} <any-name>`
        // annotation.
        paramNames.add(PLACEHOLDER_OBJ_PARAM_NAME);
        continue;
      }
      checkState(paramName.isName(), param);
      paramNames.add(paramName.getString());
    }
    return paramNames;
  }

}
