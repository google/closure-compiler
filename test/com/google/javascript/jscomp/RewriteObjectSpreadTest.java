/*
 * Copyright 2018 The Closure Compiler Authors.
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

import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.CodeSubTree;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link RewriteObjectSpread} */
@RunWith(JUnit4.class)
public final class RewriteObjectSpreadTest extends CompilerTestCase {

  public RewriteObjectSpreadTest() {
    super(new TestExternsBuilder().addObject().build());
  }

  @Before
  public void customSetUp() throws Exception {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    setLanguageOut(LanguageMode.ECMASCRIPT_2017);
  }

  @Before
  public void enableTypeCheckBeforePass() {
    enableNormalize();
    enableTypeCheck();
    enableTypeInfoValidation();
    disableCompareSyntheticCode();
    allowExternsChanges();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteObjectSpread(compiler);
  }

  @Test
  public void testObjectLiteralWithSpread() {
    test("({first, ...spread});", "Object.assign({}, {first}, spread)");
    test("({first, second, ...spread});", "Object.assign({}, {first, second}, spread)");
    test("({...spread, last});", "Object.assign({}, spread, {last})");
    test("({...spread, penultimate, last});", "Object.assign({}, spread, {penultimate, last})");
    test(
        "({before, ...spread1, mid1, mid2, ...spread2, after});",
        "Object.assign({}, {before}, spread1, {mid1, mid2}, spread2, {after})");
    test("({first, ...{...nested}});", "Object.assign({}, {first}, Object.assign({}, nested))");
    test(
        "({first, [foo()]: baz(), ...spread});",
        "Object.assign({}, {first, [foo()]: baz()}, spread)");
    test(
        "({type : 'object', ...spread && spread});",
        "Object.assign({}, {type:'object'}, spread && spread);");
  }

  @Test
  public void testTyping_ofSpreadResult_isObject() {
    test(
        """
        const first = 0;
        const spread = {bar: 'str', qux: false};
        const obj = ({first, ...spread});
        """,
        """
        const first = 0;
        const spread = {bar: 'str', qux: false};
        const obj = Object.assign({}, {first}, spread)
        """);

    Compiler lastCompiler = getLastCompiler();

    Node obj = getNodeMatchingQName(lastCompiler.getJsRoot(), "obj");
    assertNode(obj).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
    assertNode(obj.getFirstFirstChild())
        .hasColorThat()
        .isEqualTo(
            CodeSubTree.findFirstNode(
                    getLastCompiler().getExternsRoot(),
                    (node) -> node.matchesQualifiedName("Object.assign"))
                .getNext()
                .getColor());
  }

  /** Returns the first node (preorder) in the given AST that matches the given qualified name */
  private Node getNodeMatchingQName(Node root, String qname) {
    if (root.matchesQualifiedName(qname)) {
      return root;
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node result = getNodeMatchingQName(child, qname);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
