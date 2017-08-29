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
import com.google.javascript.rhino.TypeI;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;

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
    TypeIRegistry registry = t.getCompiler().getTypeIRegistry();
    TypeI stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    int length = n.getChildCount();
    if (length == 0) {
      n.replaceWith(withType(IR.string("\"\""), stringType));
    } else {
      Node first = n.removeFirstChild();
      checkState(first.isString());
      if (length == 1) {
        n.replaceWith(first);
      } else {
        // Add the first string with the first substitution expression
        Node add = withType(IR.add(first, n.removeFirstChild().removeFirstChild()), n.getTypeI());
        // Process the rest of the template literal
        for (int i = 2; i < length; i++) {
          Node child = n.removeFirstChild();
          if (child.isString()) {
            if (child.getString().isEmpty()) {
              continue;
            } else if (i == 2 && first.getString().isEmpty()) {
              // So that `${hello} world` gets translated into (hello + " world")
              // instead of ("" + hello + " world").
              add = add.getSecondChild().detach();
            }
          }
          add =
              withType(
                  IR.add(add, child.isString() ? child : child.removeFirstChild()), n.getTypeI());
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
    TypeIRegistry registry = t.getCompiler().getTypeIRegistry();
    TypeI stringType = createType(addTypes, registry, JSTypeNative.STRING_TYPE);
    TypeI arrayType = createGenericType(addTypes, registry, JSTypeNative.ARRAY_TYPE, stringType);
    TypeI templateArrayType =
        createType(addTypes, registry, JSTypeNative.I_TEMPLATE_ARRAY_TYPE);

    Node templateLit = n.getLastChild();
    // Prepare the raw and cooked string arrays.
    Node raw = createRawStringArray(templateLit, arrayType, stringType);
    Node cooked = createCookedStringArray(templateLit, templateArrayType, stringType);

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
    Node defineRaw =
        IR.exprResult(
                withType(
                    IR.assign(
                        withType(
                            IR.getprop(
                                callsiteId.cloneNode(), withType(IR.string("raw"), stringType)),
                            arrayType),
                        raw),
                    arrayType))
            .useSourceInfoIfMissingFromForTree(n);
    script.addChildAfter(defineRaw, var);

    // Generate the call expression.
    Node call = withType(IR.call(n.removeFirstChild(), callsiteId.cloneNode()), n.getTypeI());
    for (Node child = templateLit.getFirstChild(); child != null; child = child.getNext()) {
      if (!child.isString()) {
        call.addChildToBack(child.removeFirstChild());
      }
    }
    call.useSourceInfoIfMissingFromForTree(templateLit);
    call.putBooleanProp(Node.FREE_CALL, !call.getFirstChild().isGetProp());
    n.replaceWith(call);
    t.reportCodeChange();
  }

  private static Node createRawStringArray(Node n, TypeI arrayType, TypeI stringType) {
    Node array = withType(IR.arraylit(), arrayType);
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isString()) {
        array.addChildToBack(
            withType(IR.string((String) child.getProp(Node.RAW_STRING_VALUE)), stringType));
      }
    }
    return array;
  }

  private static Node createCookedStringArray(Node n, TypeI templateArrayType, TypeI stringType) {
    Node array = withType(IR.arraylit(), templateArrayType);
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isString()) {
        Node string =
            withType(
                IR.string(cookString((String) child.getProp(Node.RAW_STRING_VALUE))), stringType);
        array.addChildToBack(string);
      }
    }
    return array;
  }

  /**
   * Takes a raw string and returns a string that is suitable for the cooked
   * value (the Template Value or TV as described in the specs). This involves
   * removing line continuations.
   */
  private static String cookString(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length();) {
      char c = s.charAt(i++);
      if (c == '\\') {
        char c2 = s.charAt(i++);
        switch (c2) {
          case 't':
            sb.append('\t');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'u':
            int unicodeValue = Integer.parseInt(s.substring(i, i + 4), 16);
            sb.append((char) unicodeValue);
            i += 4;
            break;
          // Strip line continuation.
          case '\n':
          case '\u2028':
          case '\u2029':
            break;
          case '\r':
            // \ \r \n should be stripped as one
            if (s.charAt(i + 1) == '\n') {
              i++;
            }
            break;

          default:
            sb.append(c);
            sb.append(c2);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
