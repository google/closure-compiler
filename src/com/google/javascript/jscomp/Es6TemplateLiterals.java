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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Helper class for transpiling ES6 template literals.
 *
 * @author moz@google.com (Michael Zhou)
 */
class Es6TemplateLiterals {
  private static final String TEMPLATELIT_VAR = "$jscomp$templatelit$";

  static void visitTemplateLiteral(NodeTraversal t, Node n) {
    if (n.getFirstChild().isString()) {
      createUntaggedTemplateLiteral(n);
    } else {
      createTaggedTemplateLiteral(t, n);
    }
    t.getCompiler().reportCodeChange();
  }

  /**
   * Converts `${a} b ${c} d ${e}` to (a + " b " + c + " d " + e)
   *
   * @param n A TEMPLATELIT node that is not prefixed with a tag
   */
  private static void createUntaggedTemplateLiteral(Node n) {
    int length = n.getChildCount();
    if (length == 0) {
      n.getParent().replaceChild(n, IR.string("\"\""));
    } else {
      Node first = n.removeFirstChild(); // first is always a STRING node
      if (length == 1) {
        n.getParent().replaceChild(n, first);
      } else {
        // Add the first string with the first substitution expression
        Node add = IR.add(first, n.removeFirstChild().removeFirstChild());
        // Process the rest of the template literal
        for (int i = 2; i < length; i++) {
          Node child = n.removeFirstChild();
          if (child.isString()) {
            if (child.getString().isEmpty()) {
              continue;
            } else if (i == 2 && first.getString().isEmpty()) {
              // So that `${hello} world` gets translated into (hello + " world")
              // instead of ("" + hello + " world").
              add = add.getChildAtIndex(1).detachFromParent();
            }
          }
          add = IR.add(add, child.isString() ? child : child.removeFirstChild());
        }
        n.getParent().replaceChild(n, add.useSourceInfoIfMissingFromForTree(n));
      }
    }
  }

  /**
   * Converts tag`a\tb${bar}` to:
   *   // A global (module) scoped variable
   *   var $jscomp$templatelit$0 = ["a\tb"];    // cooked string array
   *   $jscomp$templatelit$0["raw"] = ["a\\tb"]; // raw string array
   *   ...
   *   // A call to the tagging function
   *   tag($jscomp$templatelit$0, bar);
   *
   *   See template_literal_test.js for more examples.
   *
   * @param n A TEMPLATELIT node that is prefixed with a tag
   */
  private static void createTaggedTemplateLiteral(NodeTraversal t, Node n) {
    // Prepare the raw and cooked string arrays.
    Node raw = createRawStringArray(n);
    Node cooked = createCookedStringArray(n);

    // Create a variable representing the template literal.
    Node callsiteId = IR.name(
        TEMPLATELIT_VAR + t.getCompiler().getUniqueNameIdSupplier().get());
    Node var = IR.var(callsiteId, cooked).useSourceInfoIfMissingFromForTree(n);
    Node script = NodeUtil.getEnclosingType(n, Token.SCRIPT);
    script.addChildrenToFront(var);

    // Define the "raw" property on the introduced variable.
    Node defineRaw = IR.exprResult(IR.assign(IR.getelem(
        callsiteId.cloneNode(), IR.string("raw")), raw))
            .useSourceInfoIfMissingFromForTree(n);
    script.addChildAfter(defineRaw, var);

    // Generate the call expression.
    Node[] args = new Node[n.getChildCount() / 2];
    args[0] = callsiteId.cloneNode();
    for (int i = 1, j = 2; i < args.length; i++, j += 2) {
      args[i] = n.getChildAtIndex(j).removeFirstChild();
    }
    Node call = IR.call(n.removeFirstChild(), args)
        .useSourceInfoIfMissingFromForTree(n);
    call.putBooleanProp(Node.FREE_CALL, !call.getFirstChild().isGetProp());
    n.getParent().replaceChild(n, call);
  }

  private static Node createRawStringArray(Node n) {
    Node[] items = new Node[n.getChildCount() / 2];
    for (int i = 0, j = 1; i < items.length; i++, j += 2) {
      items[i] = IR.string(
          (String) n.getChildAtIndex(j).getProp(Node.RAW_STRING_VALUE));
    }
    return IR.arraylit(items);
  }

  private static Node createCookedStringArray(Node n) {
    Node[] items = new Node[n.getChildCount() / 2];
    for (int i = 0, j = 1; i < items.length; i++, j += 2) {
      items[i] = IR.string(cookString(
          (String) n.getChildAtIndex(j).getProp(Node.RAW_STRING_VALUE)));
      items[i].putBooleanProp(Node.COOKED_STRING, true);
    }
    return IR.arraylit(items);
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
      switch (c) {
        case '\\':
          char c2 = s.charAt(i++);
          switch (c2) {
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
          break;

        // Whitespace
        case '\n':
          sb.append("\\n");
          break;
        // <CR><LF> and <CR> LineTerminatorSequences are normalized to <LF>
        // for both TV and TRV.
        case '\r':
          if (s.charAt(i) == '\n') {
            i++;
          }
          sb.append("\\n");
          break;
        case '\u2028':
          sb.append("\\u2028");
          break;
        case '\u2029':
          sb.append("\\u2029");
          break;

        default:
          sb.append(c);
      }
    }
    return sb.toString();
  }
}
