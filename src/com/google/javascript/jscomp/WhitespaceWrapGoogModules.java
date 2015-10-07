package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class WhitespaceWrapGoogModules implements HotSwapCompilerPass {

  private final AbstractCompiler compiler;

  WhitespaceWrapGoogModules(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
      Preconditions.checkState(c.isScript());
      hotSwapScript(c, null);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    if (!NodeUtil.isModuleFile(scriptRoot)) return;

    // As per ClosureBundler:
    /*
      // add the prefix on the first line so the line numbers aren't affected.
      out.append(
          "goog.loadModule(function(exports) {"
          + "'use strict';");
      append(out, Mode.NORMAL, contents);
      out.append(
          "\n" // terminate any trailing single line comment.
          + ";" // terminate any trailing expression.
          + "return exports;});\n");
      appendSourceUrl(out, Mode.NORMAL);
     */

    Node block = IR.block();
    block.addChildToBack(IR.exprResult(IR.string("use strict"))); // needs to be explicit, to match ClosureBundler
    if (scriptRoot.hasChildren()) {
      block.addChildrenToBack(scriptRoot.removeChildren());
    }
    block.addChildToBack(IR.returnNode(IR.name("exports")));

    Node loadMod = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog"), IR.string("loadModule")),
        IR.function(IR.name(""), IR.paramList(IR.name("exports")), block)))
        .srcrefTree(scriptRoot);

    scriptRoot.removeChildren();
    scriptRoot.addChildToBack(loadMod);
    compiler.reportCodeChange();
  }

}
