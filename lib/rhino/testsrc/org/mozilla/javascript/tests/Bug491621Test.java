package org.mozilla.javascript.tests;

import org.junit.Assert;
import org.junit.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

/**
 */
public class Bug491621Test {

    /**
     * Asserts that the value returned by {@link AstRoot#toSource()} after
     * the given input source was parsed equals the specified expected output source.
     *
     * @param source the JavaScript source to be parsed
     * @param expectedOutput the JavaScript source that is expected to be
     *                       returned by {@link AstRoot#toSource()}
     */
    private void assertSource(String source, String expectedOutput)
    {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_1_7);
        Parser parser = new Parser(env);
        AstRoot root = parser.parse(source, null, 0);
        Assert.assertEquals(expectedOutput, root.toSource());
    }

    /**
     * Tests that var declaration AST nodes is properly decompiled.
     */
    @Test
    public void testVarDeclarationToSource()
    {
        assertSource("var x=0;x++;",
                "var x = 0;\nx++;\n");
        assertSource("for(var i=0;i<10;i++)x[i]=i;a++;",
                "for (var i = 0; i < 10; i++) \n  x[i] = i;\na++;\n");
        assertSource("var a;if(true)a=1;",
                "var a;\nif (true) \na = 1;\n");
        assertSource("switch(x){case 1:var y;z++}",
                "switch (x) {\n  case 1:\n    var y;\n    z++;\n}\n");
        assertSource("for(var p in o)s+=o[p]",
                "for (var p in o) \n  s += o[p];\n");
        assertSource("if(c)var a=0;else a=1",
                "if (c) \nvar a = 0; else a = 1;\n");
        assertSource("for(var i=0;i<10;i++)var x=i;x++;",
                "for (var i = 0; i < 10; i++) \n  var x = i;\nx++;\n");
        assertSource("function f(){var i=2;for(var j=0;j<i;++j)print(j);}",
                "function f() {\n  var i = 2;\n  for (var j = 0; j < i; ++j) \n    print(j);\n}\n");
    }

    /**
     * Tests that let declaration AST nodes are properly decompiled.
     */
    @Test
    public void testLetDeclarationToSource()
    {
        assertSource("let x=0;x++;",
                "let x = 0;\nx++;\n");
        assertSource("for(let i=0;i<10;i++)x[i]=i;a++;",
                "for (let i = 0; i < 10; i++) \n  x[i] = i;\na++;\n");
        assertSource("let a;if(true)a=1;",
                "let a;\nif (true) \na = 1;\n");
        assertSource("switch(x){case 1:let y;z++}",
                "switch (x) {\n  case 1:\n    let y;\n    z++;\n}\n");
        assertSource("for(let p in o)s+=o[p]",
                "for (let p in o) \n  s += o[p];\n");
        assertSource("if(c)let a=0;else a=1",
                "if (c) \nlet a = 0; else a = 1;\n");
        assertSource("for(let i=0;i<10;i++){let x=i;}x++;",
                "for (let i = 0; i < 10; i++) \n  {\n    let x = i;\n  }\nx++;\n");
        assertSource("function f(){let i=2;for(let j=0;j<i;++j)print(j);}",
                "function f() {\n  let i = 2;\n  for (let j = 0; j < i; ++j) \n    print(j);\n}\n");
    }

    /**
     * Tests that const declaration AST nodes are properly decompiled.
     */
    @Test
    public void testConstDeclarationToSource()
    {
        assertSource("const x=0;x++;",
                "const x = 0;\nx++;\n");
        assertSource("const a;if(true)a=1;",
                "const a;\nif (true) \na = 1;\n");
        assertSource("switch(x){case 1:const y;z++}",
                "switch (x) {\n  case 1:\n    const y;\n    z++;\n}\n");
        assertSource("if(c)const a=0;else a=1",
                "if (c) \nconst a = 0; else a = 1;\n");
    }
}
