/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerOptions.LanguageMode.ECMASCRIPT_NEXT;

import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceMap;

public final class ReferenceCollectingCallbackTest extends CompilerTestCase {
  private Behavior behavior;
  private boolean es6ScopeCreator;

  @Override
  public void setUp() {
    setLanguage(ECMASCRIPT_NEXT, ECMASCRIPT_NEXT);
    behavior = null;
    es6ScopeCreator = true;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    ScopeCreator scopeCreator =
        es6ScopeCreator
            ? new Es6SyntacticScopeCreator(compiler)
            : SyntacticScopeCreator.makeUntyped(compiler);
    return new ReferenceCollectingCallback(
        compiler,
        this.behavior,
        scopeCreator);
  }

  public void testVarInBlock_oldScopeCreator() {
    es6ScopeCreator = false;
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
        if (t.getScope().isFunctionScope()) {
          ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
          assertThat(y.isAssignedOnceInLifetime()).isTrue();
          assertThat(y.isWellDefined()).isTrue();
        }
      }
    };
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  if (true) {",
            "    var y = x;",
            "    y;",
            "    y;",
            "  }",
            "}"));
  }

  public void testVarInBlock() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm) {
        if (t.getScope().isBlockScope() && t.getScope().getParent().isFunctionBlockScope()) {
          ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
          assertThat(y.isAssignedOnceInLifetime()).isTrue();
          assertThat(y.isWellDefined()).isTrue();
        }
      }
    };
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  if (true) {",
            "    var y = x;",
            "    y;",
            "    y;",
            "  }",
            "}"));
  }

  public void testVarInLoopNotAssignedOnlyOnceInLifetime() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isBlockScope()) {
          ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
          assertThat(x.isAssignedOnceInLifetime()).isFalse();
        }
      }
    };
    testSame("while (true) { var x = 0; }");
    testSame("while (true) { let x = 0; }");
  }

  /**
   * Although there is only one assignment to x in the code, it's in a function which could be
   * called multiple times, so {@code isAssignedOnceInLifetime()} returns false.
   */
  public void testVarInFunctionNotAssignedOnlyOnceInLifetime() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isGlobal()) {
          ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
          assertThat(x.isAssignedOnceInLifetime()).isFalse();
        }
      }
    };
    testSame("var x; function f() { x = 0; }");
    testSame("let x; function f() { x = 0; }");
  }

  public void testParameterAssignedOnlyOnceInLifetime() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isFunctionScope()) {
          ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
          assertThat(x.isAssignedOnceInLifetime()).isTrue();
        }
      }
    };
    testSame("function f(x) { x; }");
  }

  public void testModifiedParameterNotAssignedOnlyOnceInLifetime() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isFunctionScope()) {
          ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
          assertThat(x.isAssignedOnceInLifetime()).isFalse();
        }
      }
    };
    testSame("function f(x) { x = 3; }");
  }

  public void testVarAssignedOnceInLifetime1() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isFunctionBlockScope()) {
          ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
          assertThat(x.isAssignedOnceInLifetime()).isTrue();
        }
      }
    };
    testSame("function f() { var x = 0; }");
    testSame("function f() { let x = 0; }");
  }

  public void testVarAssignedOnceInLifetime2() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isBlockScope() && !t.getScope().isFunctionBlockScope()) {
          ReferenceCollection x = rm.getReferences(t.getScope().getVar("x"));
          assertThat(x.isAssignedOnceInLifetime()).isTrue();
        }
      }
    };
    testSame("function f() { { let x = 0; } }");
  }

  public void testVarAssignedOnceInLifetime3() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isCatchScope()) {
          ReferenceCollection e = rm.getReferences(t.getScope().getVar("e"));
          assertThat(e.isAssignedOnceInLifetime()).isTrue();
          ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
          assertThat(y.isAssignedOnceInLifetime()).isTrue();
          assertThat(y.isWellDefined()).isTrue();
        }
      }
    };
    testSame(
        LINE_JOINER.join(
            "try {",
            "} catch (e) {",
            "  var y = e;",
            "  g();" ,
            "  y;y;" ,
            "}"));
    testSame(
        LINE_JOINER.join(
            "try {",
            "} catch (e) {",
            "  var y; y = e;",
            "  g();" ,
            "  y;y;" ,
            "}"));
  }

  public void testLetAssignedOnceInLifetime1() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isCatchScope()) {
          ReferenceCollection e = rm.getReferences(t.getScope().getVar("e"));
          assertThat(e.isAssignedOnceInLifetime()).isTrue();
          ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
          assertThat(y.isAssignedOnceInLifetime()).isTrue();
          assertThat(y.isWellDefined()).isTrue();
        }
      }
    };
    testSame(
        LINE_JOINER.join(
            "try {",
            "} catch (e) {",
            "  let y = e;",
            "  g();" ,
            "  y;y;" ,
            "}"));
  }

  public void testLetAssignedOnceInLifetime2() {
    behavior = new Behavior() {
      @Override
      public void afterExitScope(NodeTraversal t, ReferenceMap rm)  {
        if (t.getScope().isCatchScope()) {
          ReferenceCollection e = rm.getReferences(t.getScope().getVar("e"));
          assertThat(e.isAssignedOnceInLifetime()).isTrue();
          ReferenceCollection y = rm.getReferences(t.getScope().getVar("y"));
          assertThat(y.isAssignedOnceInLifetime()).isTrue();
          assertThat(y.isWellDefined()).isTrue();
        }
      }
    };
    testSame(
        LINE_JOINER.join(
            "try {",
            "} catch (e) {",
            "  let y; y = e;",
            "  g();" ,
            "  y;y;" ,
            "}"));
  }
}
