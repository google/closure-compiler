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

import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.Es6ToEs3Util.createGenericType;
import static com.google.javascript.jscomp.Es6ToEs3Util.createType;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.javascript.jscomp.parsing.JsDocInfoParser;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

/**
 * Helper class for transpiling ES6 template literals.
 *
 * @author moz@google.com (Michael Zhou)
 */
class Es6TemplateLiterals {
  private static final String TEMPLATELIT_VAR = "$jscomp$templatelit$";

  /**
   * Converts `${a} b ${c} d ${e}` to (a + " b " + c + " d " + e)
   *
   * @param n A TEMPLATELIT node that is not prefixed with a tag
   */
  static void visitTemplateLiteral(NodeTraversal t, Node n, boolean addTypes) {
    JSTypeRegistry registry = t.getCompiler().getTypeRegistry();
    JSType stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    int length = n.getChildCount();
    if (length == 0) {
      n.replaceWith(withType(IR.string("\"\""), stringType));
    } else {
      Node first = n.removeFirstChild();
      checkState(first.isTemplateLitString() && first.getCookedString() != null);
      Node firstStr = withType(IR.string(first.getCookedString()), stringType);
      if (length == 1) {
        n.replaceWith(firstStr);
      } else {
        // Add the first string with the first substitution expression
        Node add =
            withType(IR.add(firstStr, n.removeFirstChild().removeFirstChild()), n.getJSType());
        // Process the rest of the template literal
        for (int i = 2; i < length; i++) {
          Node child = n.removeFirstChild();
          if (child.isTemplateLitString()) {
            checkState(child.getCookedString() != null);
            if (child.getCookedString().isEmpty()) {
              continue;
            } else if (i == 2 && first.getCookedString().isEmpty()) {
              // So that `${hello} world` gets translated into (hello + " world")
              // instead of ("" + hello + " world").
              add = add.getSecondChild().detach();
            }
          }
          add =
              withType(
                  IR.add(
                      add,
                      child.isTemplateLitString()
                          ? withType(IR.string(child.getCookedString()), stringType)
                          : child.removeFirstChild()),
                  n.getJSType());
        }
        n.replaceWith(add.useSourceInfoIfMissingFromForTree(n));
      }
    }
    t.reportCodeChange();
  }

  /**
   * Converts tag`a\tb${bar}` to:
   *   // A global (module) scoped variable
   *   var $jscomp$templatelit$0 = ["a\tb"];   // cooked string array
   *   $jscomp$templatelit$0.raw = ["a\\tb"];  // raw string array
   *   ...
   *   // A call to the tagging function
   *   tag($jscomp$templatelit$0, bar);
   *
   *   See template_literal_test.js for more examples.
   *
   * @param n A TAGGED_TEMPLATELIT node
   */
  static void visitTaggedTemplateLiteral(NodeTraversal t, Node n, boolean addTypes) {
    AstFactory astFactory = t.getCompiler().createAstFactory();
    JSTypeRegistry registry = t.getCompiler().getTypeRegistry();
    JSType stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    JSType arrayType = createGenericType(addTypes, registry, JSTypeNative.ARRAY_TYPE, stringType);
    JSType templateArrayType =
        createType(addTypes, registry, JSTypeNative.I_TEMPLATE_ARRAY_TYPE);
    JSType voidType = createType(addTypes, registry, JSTypeNative.VOID_TYPE);
    JSType numberType = createType(addTypes, registry, JSTypeNative.NUMBER_TYPE);

    Node templateLit = n.getLastChild();
    Node cooked =
        createCookedStringArray(templateLit, templateArrayType, stringType, voidType, numberType);

    // Specify the type of the first argument to be ITemplateArray.
    JSTypeExpression nonNullSiteObject = new JSTypeExpression(
        JsDocInfoParser.parseTypeString("!ITemplateArray"), "<Es6TemplateLiterals.java>");
    JSDocInfoBuilder info = new JSDocInfoBuilder(false);
    info.recordType(nonNullSiteObject);
    Node siteObject = withType(IR.cast(cooked, info.build()), templateArrayType);

    // Create a variable representing the template literal.
    Node callsiteId =
        withType(
            IR.name(TEMPLATELIT_VAR + t.getCompiler().getUniqueNameIdSupplier().get()),
            templateArrayType);
    Node var = IR.var(callsiteId, siteObject).useSourceInfoIfMissingFromForTree(n);
    Node script = NodeUtil.getEnclosingScript(n);
    script.addChildToFront(var);
    t.reportCodeChange(var);

    // Define the "raw" property on the introduced variable.
    Node defineRaw;
    if (cookedAndRawStringsSame(templateLit)) {
      // The cooked and raw versions of the array are the same, so just call slice() on the
      // cooked array at runtime to make the raw array a copy of the cooked array.
      defineRaw =
          IR.exprResult(
                  astFactory.createAssign(
                      astFactory.createGetProp(callsiteId.cloneNode(), "raw"),
                      astFactory.createCall(
                          astFactory.createGetProp(callsiteId.cloneNode(), "slice"))))
              .useSourceInfoIfMissingFromForTree(n);
    } else {
      // The raw string array is different, so we need to construct it.
      Node raw = createRawStringArray(templateLit, arrayType, stringType);
      defineRaw =
          IR.exprResult(
                  astFactory.createAssign(
                      astFactory.createGetProp(callsiteId.cloneNode(), "raw"), raw))
              .useSourceInfoIfMissingFromForTree(n);
    }

    script.addChildAfter(defineRaw, var);

    // Generate the call expression.
    Node call = withType(IR.call(n.removeFirstChild(), callsiteId.cloneNode()), n.getJSType());
    for (Node child = templateLit.getFirstChild(); child != null; child = child.getNext()) {
      if (!child.isTemplateLitString()) {
        call.addChildToBack(child.removeFirstChild());
      }
    }
    call.useSourceInfoIfMissingFromForTree(templateLit);
    call.putBooleanProp(Node.FREE_CALL, !call.getFirstChild().isGetProp());
    n.replaceWith(call);
    t.reportCodeChange();
  }

  private static Node createRawStringArray(Node n, JSType arrayType, JSType stringType) {
    Node array = withType(IR.arraylit(), arrayType);
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isTemplateLitString()) {
        array.addChildToBack(withType(IR.string(child.getRawString()), stringType));
      }
    }
    return array;
  }

  private static Node createCookedStringArray(
      Node n, JSType templateArrayType, JSType stringType, JSType voidType, JSType numberType) {
    Node array = withType(IR.arraylit(), templateArrayType);
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isTemplateLitString()) {
        if (child.getCookedString() != null) {
          array.addChildToBack(withType(IR.string(child.getCookedString()), stringType));
        } else {
          // undefined cooked string due to exception in template escapes
          array.addChildToBack(withType(IR.voidNode(withType(IR.number(0), numberType)), voidType));
        }
      }
    }
    return array;
  }

  private static boolean cookedAndRawStringsSame(Node n) {
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (!child.isTemplateLitString()) {
        continue;
      }
      // getCookedString() returns null when the template literal has an illegal escape sequence.
      if (child.getCookedString() == null
          || !child.getCookedString().equals(child.getRawString())) {
        return false;
      }
    }
    return true;
  }
}
