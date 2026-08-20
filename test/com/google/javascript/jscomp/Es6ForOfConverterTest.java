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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link Es6ForOfConverter} */
@RunWith(JUnit4.class)
public final class Es6ForOfConverterTest extends CompilerTestCase {

  private static final String EXTERNS_BASE =
      new TestExternsBuilder().addArguments().addConsole().addJSCompLibraries().build();

  public Es6ForOfConverterTest() {
    super(EXTERNS_BASE);
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
    setGenericNameReplacements(
        ImmutableMap.of(
            "KEY", "$jscomp$key$",
            "ITER", "$jscomp$iter$",
            "RET_FN", "$jscomp$retFn$"));
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(
        makePassFactory(
            "injectTranspilationRuntimeLibraries", InjectTranspilationRuntimeLibraries::new));
    optimizer.addOneTimePass(makePassFactory("es6ForOfConverter", Es6ForOfConverter::new));
    return optimizer;
  }

  @Test
  public void testForOfLoop() {
    // With array literal and declaring new bound variable.
    test(
        "for (var i of [1,2,3]) { console.log(i); }",
        """
        var i;
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3]);
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
             i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);

    // With simple assign instead of var declaration in bound variable.
    test(
        "for (i of [1,2,3]) { console.log(i); }",
        """
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3])
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
            i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);

    // With name instead of array literal.
    test(
        "for (var i of arr) { console.log(i); }",
        """
        var i;
        var ITER$0 = (0, $jscomp.makeIterator)(arr)
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
             i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);

    // for of with const initializer
    test(
        "for (const i of [1,2,3]) { console.log(i); }",
        """
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3]);
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
            const i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);

    // multiple for-of loops with the const initializer name
    test(
        "for (const i of [1,2,3]) { console.log(i); } for (const i of [4,5,6]) { console.log(i); }",
        """
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3]);
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
            const i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        var ITER$3 = (0, $jscomp.makeIterator)([4, 5, 6]);
        var KEY$4$i$jscomp$1 = ITER$3.next();
        var RET_FN$5;
        try {
          for (;
              !KEY$4$i$jscomp$1.done; KEY$4$i$jscomp$1 = ITER$3.next()) {
            const i$jscomp$1 = KEY$4$i$jscomp$1.value;
            {
              console.log(i$jscomp$1);
            }
          }
        } finally {
          if (KEY$4$i$jscomp$1 && !KEY$4$i$jscomp$1.done && (RET_FN$5 = ITER$3.return)) {
            RET_FN$5.call(ITER$3);
          }
        }
        """);

    // With empty loop body.
    test(
        "for (var i of [1,2,3]);",
        """
        var i;
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3])
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
             i = KEY$1$i.value;
            {}
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);

    // With no block in for loop body.
    test(
        "for (var i of [1,2,3]) console.log(i);",
        """
        var i;
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3]);
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
             i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);

    // Iteration var shadows an outer var ()
    test(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        """
        var i = 'outer';
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3])
        var KEY$1$i$jscomp$1 = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$i$jscomp$1.done; KEY$1$i$jscomp$1 = ITER$0.next()) {
            let i$jscomp$1 = KEY$1$i$jscomp$1.value;
            {
              alert(i$jscomp$1);
            }
          }
        } finally {
          if (KEY$1$i$jscomp$1 && !KEY$1$i$jscomp$1.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        alert(i);
        """);
  }

  @Test
  public void testConstnessPreservedInNewDeclarations() {
    test(
        "for (let CID of [1, 2, 3]) { alert(CID); }",
"""
var ITER$0 = (0, $jscomp.makeIterator)([1,2,3])
var KEY$1$CID = ITER$0.next();
var RET_FN$2;
try {
  for (;
      !KEY$1$CID.done; KEY$1$CID = ITER$0.next()) {
    let CID = KEY$1$CID.value;
    {
      alert(CID);
    }
  }
} finally {
  if (KEY$1$CID && !KEY$1$CID.done && (RET_FN$2 = ITER$0.return)) {
    RET_FN$2.call(ITER$0);
  }
}
""");
    Node script = getLastCompiler().getJsRoot().getOnlyChild();
    checkState(script.isScript(), script.getToken());
    Node tryFinally = script.getLastChild();
    checkState(tryFinally.isTry(), tryFinally.getToken());
    Node tryBlock = tryFinally.getFirstChild();
    checkState(tryBlock.isBlock(), tryBlock.getToken());
    Node forLoop = tryBlock.getFirstChild();
    checkState(forLoop.isVanillaFor(), forLoop.getToken());
    Node forBody = forLoop.getLastChild();
    checkState(forBody.isBlock());
    Node declaration = forBody.getFirstChild();
    checkState(declaration.isLet());
    Node name = declaration.getFirstChild();
    checkState(name.getString().equals("CID"));
    checkState(name.getBooleanProp(Node.IS_CONSTANT_NAME));
  }

  @Test
  public void testForOfRedeclaredVar() {
    test(
        """
        for (let x of []) {
          let x = 0;
        }
        """,
        """
        var ITER$0=(0, $jscomp.makeIterator)([]);
        var KEY$1$x=ITER$0.next();
        var RET_FN$2;
        try {
          for(;
              !KEY$1$x.done; KEY$1$x=ITER$0.next()) {
            let x = KEY$1$x.value;
            {
              let x$jscomp$1 = 0;
            }
          }
        } finally {
          if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }

  @Test
  public void testForOfJSDoc() {
    test(
        "for (/** @type {string} */ let x of []) {}",
        """
        var ITER$0=(0, $jscomp.makeIterator)([]);
        var KEY$1$x=ITER$0.next();
        var RET_FN$2;
        try {
          for(;
              !KEY$1$x.done;KEY$1$x=ITER$0.next()) {
            let x = KEY$1$x.value;
            {}
          }
        } finally {
          if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
    test(
        "for (/** @type {string} */ x of []) {}",
        """
        var ITER$0=(0, $jscomp.makeIterator)([]);
        var KEY$1$x=ITER$0.next();
        var RET_FN$2;
        try {
          for(;
              !KEY$1$x.done;KEY$1$x=ITER$0.next()) {
            x = KEY$1$x.value;
            {}
          }
        } finally {
          if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }

  @Test
  public void testForOfOnNonIterable() {
    testWarning(
        """
        var arrayLike = {
          0: 'x',
          1: 'y',
          length: 2,
        };
        for (var x of arrayLike) {}
        """,
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testLabelForOf() {
    // Tests if iterator variables come before a single label
    test(
        "a: for(var i of [1,2]){console.log(i)}",
        """
        var i;
        var ITER$0 = (0, $jscomp.makeIterator)([1,2]);
        var KEY$1$i = ITER$0.next();
        var RET_FN$2;
        try {
          a: for (;
              !KEY$1$i.done; KEY$1$i = ITER$0.next()) {
             i = KEY$1$i.value;
            {
              console.log(i);
            }
          }
        } finally {
          if (KEY$1$i && !KEY$1$i.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
    // Test if the iterator variables come before two labels
    test(
        "a: b: for(var x of [1,2]){console.log(x)}",
        """
        var x;
        var ITER$0 = (0, $jscomp.makeIterator)([1,2]);
        var KEY$1$x = ITER$0.next();
        var RET_FN$2;
        try {
          a: b: for(;
              !KEY$1$x.done; KEY$1$x = ITER$0.next()) {
             x = KEY$1$x.value;
            {
              console.log(x);
            }
          }
        } finally {
          if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }

  @Test
  public void testForOfWithQualifiedNameInitializer() {
    test(
        "var obj = {a: 0}; for (obj.a of [1,2,3]) { console.log(obj.a); }",
        """
        var obj = {a: 0};
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3])
        var KEY$1$a = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$a.done; KEY$1$a = ITER$0.next()) {
            obj.a = KEY$1$a.value;
            {
              console.log(obj.a);
            }
          }
        } finally {
          if (KEY$1$a && !KEY$1$a.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }

  @Test
  public void testForOfWithComplexInitializer() {
    test(
        "function f() { return {}; } for (f()['x' + 1] of [1,2,3]) {}",
        """
        function f() { return {}; }
        var ITER$0 = (0, $jscomp.makeIterator)([1,2,3]);
        var KEY$1$a = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$a.done; KEY$1$a = ITER$0.next()) {
            f()['x' + 1] = KEY$1$a.value;
            {}
          }
        } finally {
          if (KEY$1$a && !KEY$1$a.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }

  @Test
  public void testForLetOfWithoutExterns() {
    test(
        // add only minimal runtime library stubs to prevent AstFactory crash
        externs(
            """
            /** @const */
            var $jscomp = {};
            /**
              * @param {?} iterable
              * @return {!Iterator<T>}
              * @template T
              */
            $jscomp.makeIterator = function(iterable) {};
            """),
        srcs("for (let x of [1, 2, 3]) {}"),
        expected(
            """
            var ITER$0 = (0, $jscomp.makeIterator)([1,2,3]);
            var KEY$1$x = ITER$0.next();
            var RET_FN$2;
            try {
              for (;
                  !KEY$1$x.done; KEY$1$x = ITER$0.next()) {
                let x = KEY$1$x.value;
                {}
              }
            } finally {
              if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
                RET_FN$2.call(ITER$0);
              }
            }
            """));
  }

  @Test
  public void testForOfBreak() {
    test(
        "for (var x of [1, 2, 3]) { if (x === 2) break; }",
        """
        var x;
        var ITER$0 = (0, $jscomp.makeIterator)([1, 2, 3]);
        var KEY$1$x = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$x.done; KEY$1$x = ITER$0.next()) {
            x = KEY$1$x.value;
            {
              if (x === 2) {
                break;
              }
            }
          }
        } finally {
          if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }

  @Test
  public void testForOfReturn() {
    test(
        "function f() { for (var x of [1, 2, 3]) { if (x === 2) return x; } }",
        """
        function f() {
          var x;
          var ITER$0 = (0, $jscomp.makeIterator)([1, 2, 3]);
          var KEY$1$x = ITER$0.next();
          var RET_FN$2;
          try {
            for (;
                !KEY$1$x.done; KEY$1$x = ITER$0.next()) {
              x = KEY$1$x.value;
              {
                if (x === 2) {
                  return x;
                }
              }
            }
          } finally {
            if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
              RET_FN$2.call(ITER$0);
            }
          }
        }
        """);
  }

  @Test
  public void testForOfThrow() {
    test(
        "for (var x of [1, 2, 3]) { if (x === 2) throw new Error(); }",
        """
        var x;
        var ITER$0 = (0, $jscomp.makeIterator)([1, 2, 3]);
        var KEY$1$x = ITER$0.next();
        var RET_FN$2;
        try {
          for (;
              !KEY$1$x.done; KEY$1$x = ITER$0.next()) {
            x = KEY$1$x.value;
            {
              if (x === 2) {
                throw new Error();
              }
            }
          }
        } finally {
          if (KEY$1$x && !KEY$1$x.done && (RET_FN$2 = ITER$0.return)) {
            RET_FN$2.call(ITER$0);
          }
        }
        """);
  }
}

