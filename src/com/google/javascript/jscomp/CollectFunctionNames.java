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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Extract a list of all function nodes defined in a JavaScript
 * program, assigns them globally unique ids and computes their fully
 * qualified names.  Function names are derived from the property they
 * are assigned to and the scope they are defined in.  For instance,
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

class CollectFunctionNames implements CompilerPass {

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
  private final transient FunctionListExtractor functionListExtractor;

  CollectFunctionNames(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.functionListExtractor = new FunctionListExtractor(functionNames);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, functionListExtractor);
    FunctionExpressionNamer namer = new FunctionExpressionNamer(functionNames);
    AnonymousFunctionNamingCallback namingCallback =
        new AnonymousFunctionNamingCallback(namer);
    NodeTraversal.traverseEs6(compiler, root, namingCallback);
  }

  public FunctionNames getFunctionNames() {
    return functionNames;
  }

  private static class FunctionListExtractor extends AbstractPostOrderCallback {
    private final FunctionNamesMap functionNames;

    FunctionListExtractor(FunctionNamesMap functionNames) {
      this.functionNames = functionNames;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction()) {
        Node functionNameNode = n.getFirstChild();
        String functionName = functionNameNode.getString();

        Node enclosingFunction = t.getEnclosingFunction();

        functionNames.put(n, enclosingFunction, functionName);
      }
    }
  }

  private static class FunctionExpressionNamer
      implements AnonymousFunctionNamingCallback.FunctionNamer {
    private static final char DELIMITER = '.';
    private static final NodeNameExtractor extractor =
        new NodeNameExtractor(DELIMITER);
    private final FunctionNamesMap functionNames;

    FunctionExpressionNamer(FunctionNamesMap functionNames) {
      this.functionNames = functionNames;
    }

    @Override
    public final String getName(Node node) {
      return extractor.getName(node);
    }

    @Override
    public final void setFunctionName(String name, Node fnNode) {
      functionNames.setFunctionName(name, fnNode);
    }

    @Override
    public final String getCombinedName(String lhs, String rhs) {
      return lhs + DELIMITER + rhs;
    }

  }
}
