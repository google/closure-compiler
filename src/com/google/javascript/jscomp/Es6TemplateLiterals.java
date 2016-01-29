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

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

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
  static void visitTemplateLiteral(NodeTraversal t, Node n) {
    int length = n.getChildCount();
    if (length == 0) {
      n.getParent().replaceChild(n, IR.string("\"\""));
    } else {
      Node first = n.removeFirstChild();
      Preconditions.checkState(first.isString());
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
              add = add.getSecondChild().detachFromParent();
            }
          }
          add = IR.add(add, child.isString() ? child : child.removeFirstChild());
        }
        n.getParent().replaceChild(n, add.useSourceInfoIfMissingFromForTree(n));
      }
    }
    t.getCompiler().reportCodeChange();
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
  static void visitTaggedTemplateLiteral(NodeTraversal t, Node n) {
    Node templateLit = n.getLastChild();
    // Prepare the raw and cooked string arrays.
    Node raw = createRawStringArray(templateLit);
    Node cooked = createCookedStringArray(templateLit);

    // Create a variable representing the template literal.
    Node callsiteId = IR.name(
        TEMPLATELIT_VAR + t.getCompiler().getUniqueNameIdSupplier().get());
    Node var = IR.var(callsiteId, cooked).useSourceInfoIfMissingFromForTree(n);
    Node script = NodeUtil.getEnclosingScript(n);
    script.addChildrenToFront(var);

    // Define the "raw" property on the introduced variable.
    Node defineRaw = IR.exprResult(IR.assign(IR.getprop(
        callsiteId.cloneNode(), IR.string("raw")), raw))
            .useSourceInfoIfMissingFromForTree(n);
    script.addChildAfter(defineRaw, var);

    // Generate the call expression.
    Node call = IR.call(n.removeFirstChild(), callsiteId.cloneNode());
    for (Node child = templateLit.getFirstChild(); child != null; child = child.getNext()) {
      if (!child.isString()) {
        call.addChildToBack(child.removeFirstChild());
      }
    }
    call.useSourceInfoIfMissingFromForTree(templateLit);
    call.putBooleanProp(Node.FREE_CALL, !call.getFirstChild().isGetProp());
    n.getParent().replaceChild(n, call);
    t.getCompiler().reportCodeChange();
  }

  private static Node createRawStringArray(Node n) {
    Node array = IR.arraylit();
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isString()) {
        array.addChildToBack(IR.string((String) child.getProp(Node.RAW_STRING_VALUE)));
      }
    }
    return array;
  }

  private static Node createCookedStringArray(Node n) {
    Node array = IR.arraylit();
    for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
      if (child.isString()) {
        Node string = IR.string(cookString((String) child.getProp(Node.RAW_STRING_VALUE)));
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
