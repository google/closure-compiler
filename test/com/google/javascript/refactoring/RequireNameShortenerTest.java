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

package com.google.javascript.refactoring;

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class RequireNameShortenerTest {

  @Parameterized.Parameters(name = "{0}")
  public static Object[][] cases() {
    return new Object[][] {
      {"Array", null, "Array0"},
      {"array", null, "array0"},
      {"ns.Name", IR.name("Name"), "NsName"},
      {"ns.Type", IR.nullNode().setJSDocInfo(createTypeAnnotation("Type.q.name")), "NsType"},
      {"ns.Name", IR.arraylit(IR.name("Name"), IR.name("NsName"), IR.name("NsName0")), "NsName1"},
      {"goog.Thenable", null, "GoogThenable"},
      {"goog.array", null, "googArray"},
      {"goog.events.Event", null, "EventsEvent"},
      {"goog.object", null, "googObject"},
      {"goog.string", null, "googString"},
      {"goog.structs.Map", null, "StructsMap"},
      {"wiz.Object", null, "WizObject"},
      {"xid.String", null, "XidString"},
    };
  }

  private static final JSDocInfo createTypeAnnotation(String name) {
    JSDocInfoBuilder jsdoc = new JSDocInfoBuilder(false);
    jsdoc.recordType(new JSTypeExpression(IR.string(name), "test.js"));
    return jsdoc.build();
  }

  @Parameterized.Parameter(0)
  public String namespace;

  @Parameterized.Parameter(1)
  public Node expr;

  @Parameterized.Parameter(2)
  public String shortName;

  @Test
  public void testGetShortNameForRequire() {
    Node script = IR.script();
    if (this.expr != null) {
      script.addChildToFront(IR.exprResult(this.expr));
    }
    ScriptMetadata metadata = ScriptMetadata.create(script, new Compiler());

    assertThat(RequireNameShortener.shorten(this.namespace, metadata)).isEqualTo(this.shortName);
  }
}
