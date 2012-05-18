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

import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.*;

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
 *  goog.widget::<anonymous>
 *
 */

class FunctionNames implements CompilerPass, Serializable {
  private static final long serialVersionUID = 1L;

  private final transient AbstractCompiler compiler;
  private final Map<Node, FunctionRecord> functionMap = Maps.newLinkedHashMap();
  private final transient FunctionListExtractor functionListExtractor;

  FunctionNames(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.functionListExtractor = new FunctionListExtractor(functionMap);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, functionListExtractor);
    FunctionExpressionNamer namer = new FunctionExpressionNamer(functionMap);
    AnonymousFunctionNamingCallback namingCallback =
        new AnonymousFunctionNamingCallback(namer);
    NodeTraversal.traverse(compiler, root, namingCallback);
  }

  public Iterable<Node> getFunctionNodeList() {
    return functionMap.keySet();
  }

  public int getFunctionId(Node f) {
    FunctionRecord record = functionMap.get(f);
    if (record != null) {
      return record.id;
    } else {
      return -1;
    }
  }

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
    str = str.replaceAll("::this\\.", ".");
    // foo.prototype.bar -> foo.bar
    // AnonymousFunctionNamingCallback already replaces ".prototype."
    // with "..", just remove the extra dot.
    str = str.replaceAll("\\.\\.", ".");
    // remove toplevel anonymous blocks, if they exists.
    str = str.replaceFirst("^(<anonymous>::)*", "");
    return str;
  }

  private static class FunctionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int id;
    public final Node parent;
    public String name;

    FunctionRecord(int id, Node parent, String name) {
      this.id = id;
      this.parent = parent;
      this.name = name;
    }
  }

  private static class FunctionListExtractor extends AbstractPostOrderCallback {
    private final Map<Node, FunctionRecord> functionMap;
    private int nextId = 0;

    FunctionListExtractor(Map<Node, FunctionRecord> functionMap) {
      this.functionMap = functionMap;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isFunction()) {
        Node functionNameNode = n.getFirstChild();
        String functionName = functionNameNode.getString();

        Node enclosingFunction = t.getEnclosingFunction();

        functionMap.put(n,
            new FunctionRecord(nextId, enclosingFunction, functionName));
        nextId++;
      }
    }
  }

  private static class FunctionExpressionNamer
      implements AnonymousFunctionNamingCallback.FunctionNamer {
    private static final char DELIMITER = '.';
    private static final NodeNameExtractor extractor =
        new NodeNameExtractor(DELIMITER);
    private final Map<Node, FunctionRecord> functionMap;

    FunctionExpressionNamer(Map<Node, FunctionRecord> functionMap) {
      this.functionMap = functionMap;
    }

    @Override
    public final String getName(Node node) {
      return extractor.getName(node);
    }

    @Override
    public final void setFunctionName(String name, Node fnNode) {
      FunctionRecord record = functionMap.get(fnNode);
      assert(record != null);
      assert(record.name.isEmpty());
      record.name = name;
    }

    @Override
    public final String getCombinedName(String lhs, String rhs) {
      return lhs + DELIMITER + rhs;
    }
  }
}
