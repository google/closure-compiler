/*
 * Copyright 2010 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.jsonml;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.AstValidator;
import com.google.javascript.jscomp.SourceAst;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Generates an AST from a JsonML source file.
 *
 * JsonML format for representation of JavaScript is specified
 * <a href="http://code.google.com/p/es-lab/wiki/JsonMLASTFormat">here.</a>
 *
 * @author dhans@google.com (Daniel Hans)
 *
 */
public class JsonMLAst implements SourceAst {
  private static final long serialVersionUID = 1L;
  private static final String DEFAULT_SOURCE_NAME = "[[jsonmlsource]]";

  /*
   * Root element of JavaScript source which is represented by a JsonML tree.
   * See JsonML class for more details.
   */
  private JsonML jsonml;

  /*
   * Root node of internal JS Compiler AST which represents the same source.
   * In order to get the tree, getAstRoot() has to be called.
   */
  private Node root;

  private final SourceFile sourceFile;
  private final InputId inputId;

  public JsonMLAst(JsonML jsonml) {
    this.jsonml = jsonml;
    this.inputId = new InputId(getSourceName());
    this.sourceFile = new SourceFile(getSourceName());
  }

  @Override
  public void clearAst() {
    root = null;
  }

  /**
   * Generates AST based on AST representation
   * @see com.google.javascript.jscomp.SourceAst#getAstRoot(AbstractCompiler)
   */
  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    if (root == null) {
      createAst(compiler);
    }
    return root;
  }

  @Override
  public SourceFile getSourceFile() {
    return null;
  }

  @Override
  public void setSourceFile(SourceFile file) {
    throw new UnsupportedOperationException(
        "JsonMLAst cannot be associated with a SourceFile instance.");
  }

  public String getSourceName() {
    Object obj = jsonml.getAttribute(TagAttr.SOURCE);
    if (obj instanceof String) {
      return (String) obj;
    } else {
      return DEFAULT_SOURCE_NAME;
    }
  }

  private void createAst(AbstractCompiler compiler) {
    Reader translator = new Reader();
    translator.setRootElement(jsonml);
    try {
      root = translator.parse(compiler);
      root.setInputId(inputId);
      root.setStaticSourceFile(sourceFile);
      new AstValidator().validateScript(root);
    } catch (JsonMLException e) {
      // compiler should already have JSErrors
    }
  }

  public JsonML convertToJsonML () {
    if (root != null) {
      Writer converter = new Writer();
      return converter.processAst(root);
    }
    return null;
  }

  /**
   * Returns a JsonML element with the specified number from the tree in
   * pre-order walk.
   *
   * @return nth node or null if the node does not exists
   */
  public JsonML getElementPreOrder(int n) {
    Preconditions.checkState(jsonml != null);

    if (n == 0) {
      return jsonml;
    }

    Deque<WalkHelper> stack =
        new ArrayDeque<WalkHelper>();
    stack.push(new WalkHelper(jsonml, 0));
    int i = 0;
    while (i <= n && !stack.isEmpty()) {
      WalkHelper current = stack.pop();
      JsonML element = current.element;
      Integer childno = current.childno;

      // not all the children of this node have been visited
      if (childno < element.childrenSize()) {
        stack.push(new WalkHelper(element, childno + 1));
        // we visit the next child
        i++;
        element = element.getChild(childno);

        if (i == n) {
          return element;
        }

        // put the next child on the stack to preserve pre-order
        stack.push(new WalkHelper(element, 0));
      }
    }
    return null;
  }

  /*
   * Represents a walk step while the JsonML tree is traversed.
   */
  private static class WalkHelper {
    // JsonML element that corresponds to this step
    final JsonML element;

    // number of children of the element which has already been visited
    final int childno;

    WalkHelper(JsonML element, int childno) {
      this.element = element;
      this.childno = childno;
    }
  }

  @Override
  public InputId getInputId() {
    return inputId;
  }
}
