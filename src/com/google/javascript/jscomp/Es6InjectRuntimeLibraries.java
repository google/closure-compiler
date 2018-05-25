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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Injects JS library code that may be needed by the transpiled form of the input source code.
 *
 * <p>The intention here is to add anything that could be needed and rely on `RemoveUnusedCode` to
 * remove the parts that don't end up getting used. This pass should run before type checking so the
 * type checking code can add type information to the injected JavaScript for checking and
 * optimization purposes.
 *
 * This class also reports an error if it finds getters or setters are used and the language output
 * level is too low to support them.
 * TODO(bradfordcsmith): The getter/setter check should probably be done separately in an earlier
 * pass that only runs when the output language level is ES3 and the input language level is
 * ES5 or greater.
 */
public final class Es6InjectRuntimeLibraries implements Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  // Since there's currently no Feature for Symbol, run this pass if the code has any ES6 features.
  private static final FeatureSet requiredForFeatures = FeatureSet.ES6.without(FeatureSet.ES5);

  public Es6InjectRuntimeLibraries(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, requiredForFeatures, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, requiredForFeatures, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case FOR_OF:
        // We will need this when we transpile for/of in LateEs6ToEs3Converter,
        // but we want the runtime functions to be have JSType applied to it by the type checker.
        Es6ToEs3Util.preloadEs6RuntimeFunction(compiler, "makeIterator");
        break;
      case GETTER_DEF:
      case SETTER_DEF:
        if (FeatureSet.ES3.contains(compiler.getOptions().getOutputFeatureSet())) {
          Es6ToEs3Util.cannotConvert(
              compiler, n, "ES5 getters/setters (consider using --language_out=ES5)");
        }
        break;
      case FUNCTION:
        if (n.isAsyncFunction()) {
          throw new IllegalStateException("async functions should have already been converted");
        }
        if (n.isGeneratorFunction()) {
          compiler.ensureLibraryInjected("es6/generator_engine", /* force= */ false);
        }
        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case NAME:
        if (!n.isFromExterns() && isGlobalSymbol(t, n)) {
          initSymbolBefore(n);
        }
        break;
      case GETPROP:
        if (!n.isFromExterns()) {
          visitGetprop(t, n);
        }
        break;
      default:
        break;
    }
  }

  /** @return Whether {@code n} is a reference to the global "Symbol" function. */
  private boolean isGlobalSymbol(NodeTraversal t, Node n) {
    if (!n.matchesQualifiedName("Symbol")) {
      return false;
    }
    Var var = t.getScope().getVar("Symbol");
    return var == null || var.isGlobal();
  }

  /** Inserts a call to $jscomp.initSymbol() before {@code n}. */
  private void initSymbolBefore(Node n) {
    compiler.ensureLibraryInjected("es6/symbol", false);
    Node statement = NodeUtil.getEnclosingStatement(n);
    Node initSymbol = IR.exprResult(IR.call(NodeUtil.newQName(compiler, "$jscomp.initSymbol")));
    statement.getParent().addChildBefore(initSymbol.useSourceInfoFromForTree(statement), statement);
    compiler.reportChangeToEnclosingScope(initSymbol);
  }

  // TODO(tbreisacher): Do this for all well-known symbols.
  private void visitGetprop(NodeTraversal t, Node n) {
    if (!n.matchesQualifiedName("Symbol.iterator")) {
      return;
    }
    if (isGlobalSymbol(t, n.getFirstChild())) {
      compiler.ensureLibraryInjected("es6/symbol", false);
      Node statement = NodeUtil.getEnclosingStatement(n);
      Node init = IR.exprResult(IR.call(NodeUtil.newQName(compiler, "$jscomp.initSymbolIterator")));
      statement.getParent().addChildBefore(init.useSourceInfoFromForTree(statement), statement);
      compiler.reportChangeToEnclosingScope(init);
    }
  }
}
