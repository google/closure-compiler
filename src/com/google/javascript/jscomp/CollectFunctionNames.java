/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;

import com.google.javascript.rhino.Token;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extract a list of all function nodes defined in a JavaScript
 * program, assigns them globally unique ids and computes their fully
 * qualified names.  Function names are derived from the property they
 * are assigned to and the scope they are defined in, and are meant to be
 * human-readable rather than valid Javascript identifiers.  For instance,
 * the following code
 *
 * goog.widget = function(str) {
 *   this.member_fn = function() {}
 *   local_fn = function() {}
 *   goog.array.map(arr, function(){});
 * }
 *
 * defines the following functions
 *
 *  goog.widget
 *  goog.widget.member_fn
 *  goog.widget::local_fn
 *  goog.widget::&lt;anonymous&gt;
 *
 */
class CollectFunctionNames extends AbstractPostOrderCallback implements CompilerPass {

  private static class FunctionNamesMap implements FunctionNames {
    private int nextId = 0;

    private final Map<Node, FunctionRecord> functionMap = new LinkedHashMap<>();

    @Override
    public Iterable<Node> getFunctionNodeList() {
      return functionMap.keySet();
    }

    @Override
    public int getFunctionId(Node f) {
      FunctionRecord record = functionMap.get(f);
      if (record != null) {
        return record.id;
      } else {
        return -1;
      }
    }

    @Override
    public String getFunctionName(Node f) {
      FunctionRecord record = functionMap.get(f);
      if (record == null) {
        // Function node was added during compilation and has no name.
        return null;
      }

      String str = record.name;
      if (str.isEmpty()) {
        str = "<anonymous>";
      }

      Node parent = record.parent;
      if (parent != null) {
        str = getFunctionName(parent) + "::" + str;
      }

      // this.foo -> foo
      str = str.replace("::this.", ".");
      // foo.prototype.bar -> foo.bar
      // AnonymousFunctionNamingCallback already replaces ".prototype."
      // with "..", just remove the extra dot.
      str = str.replace("..", ".");
      // remove toplevel anonymous blocks, if they exists.
      str = str.replaceFirst("^(<anonymous>::)*", "");
      return str;
    }

    private void put(Node n, Node enclosingFunction, String functionName) {
      functionMap.put(n, new FunctionRecord(nextId++, enclosingFunction, functionName));
    }

    private void setFunctionName(String name, Node fnNode) {
      FunctionRecord record = functionMap.get(fnNode);
      assert(record != null);
      assert(record.name.isEmpty());
      record.name = name;
    }
  }

  private static class FunctionRecord implements Serializable {
    public final int id;
    public final Node parent;
    public String name;

    FunctionRecord(int id, Node parent, String name) {
      this.id = id;
      this.parent = parent;
      this.name = name;
    }
  }

  private final transient AbstractCompiler compiler;
  private final FunctionNamesMap functionNames = new FunctionNamesMap();
  private static final char DELIMITER = '.';
  private final NodeNameExtractor extractor = new NodeNameExtractor(DELIMITER);

  CollectFunctionNames(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  public FunctionNames getFunctionNames() {
    return functionNames;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FUNCTION:
        Node functionNameNode = n.getFirstChild();
        String functionName = functionNameNode.getString();
        if (functionName.isEmpty()) {
          if (parent.isAssign()) {
            Node lhs = parent.getFirstChild();
            functionName = extractor.getName(lhs);
          } else if (parent.isName()) {
            functionName = extractor.getName(parent);
          }
        }

        Node enclosingFunction = t.getEnclosingFunction();

        // Here functionName may still be empty. We handle certain cases of empty function names in
        // collectObjectLiteralnames and collectClassMethodsNames. Other functions remain unnamed.
        // (See unit tests for examples).
        functionNames.put(n, enclosingFunction, functionName);
        break;
      case ASSIGN:
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        // We handle object literal methods starting from ASSIGN nodes instead of OBJECT_LIT
        // to avoid revisiting nested object literals.
        if (rhs.isObjectLit()) {
          collectObjectLiteralMethodsNames(rhs, extractor.getName(lhs));
        }
        break;
      case CLASS:
        collectClassMethodsNames(n, extractor.getName(n));
        break;
      default:
        break;
    }
  }

  /**
   * Sets names in the functionNames map for unnamed functions inside object literals,
   * and recursively visits nested object literals.
   * @param objectLiteral The object literal node to visit.
   * @param context Represents the qualified name "so far"
   */
  private void collectObjectLiteralMethodsNames(Node objectLiteral, String context) {
    for (Node keyNode = objectLiteral.getFirstChild();
        keyNode != null;
        keyNode = keyNode.getNext()) {
      Node valueNode = keyNode.getFirstChild();

      // Object literal keys may be STRING_KEY, GETTER_DEF, SETTER_DEF,
      // MEMBER_FUNCTION_DEF (Shorthand function definition) or COMPUTED_PROP.
      // We currently skip Get, Set and CompProp keys.
      // TODO(lharker): Find a way to name Get, Set, and CompProp keys.
      if (keyNode.isStringKey() || keyNode.isMemberFunctionDef()) {
        // concatenate the context and key name to get a new qualified name.
        String name = combineNames(context, extractor.getName(keyNode));

        Token type = valueNode.getToken();
        if (type == Token.FUNCTION) {
          Node functionNameNode = valueNode.getFirstChild();
          String functionName = functionNameNode.getString();
          if (functionName.isEmpty()) {
            functionNames.setFunctionName(name, valueNode);
          }
        } else if (type == Token.OBJECTLIT) {
          // process nested object literal
          collectObjectLiteralMethodsNames(valueNode, name);
        }
      }
    }
  }

  /**
   * Collects the names of class methods, which require special handling because
   * method names are stored differently in the AST than regular function expression/declaration
   * names. For example,
   * class A {
   *   method() {}
   * }
   * will set the name of "method" to be "A.method".
   */
  private void collectClassMethodsNames(Node classNode, String className) {
    Node classMembersNode = classNode.getLastChild();

    for (Node methodNode : classMembersNode.children()) {
      if (methodNode.isMemberFunctionDef()) {
        Node functionNode = methodNode.getFirstChild();
        String name = combineNames(className, extractor.getName(methodNode));
        functionNames.setFunctionName(name, functionNode);
      }
    }
  }

  private String combineNames(String lhs, String rhs) {
    return lhs + DELIMITER + rhs;
  }
}
