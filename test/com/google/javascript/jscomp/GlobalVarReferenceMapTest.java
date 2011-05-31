/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.ReferenceCollectingCallback.Reference.createRefForTest;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.ObjectType;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Unit-tests for the GlobalVarReferenceMap class.
 *
 * @author bashir@google.com (Bashir Sadjad)
 */
public class GlobalVarReferenceMapTest extends TestCase {

  private static final CompilerInput INPUT1 =
      new CompilerInput(null, "input1", false);
  private static final CompilerInput INPUT2 =
      new CompilerInput(null, "input2", false);
  private static final CompilerInput INPUT3 =
      new CompilerInput(null, "input3", false);

  private final GlobalVarReferenceMap map = new GlobalVarReferenceMap(
      Lists.newArrayList(INPUT1, INPUT2, INPUT3));
  private final Map<Var, ReferenceCollection> globalMap = Maps.newHashMap();
  private final Node root = new Node(Token.BLOCK);
  private final Scope globalScope = new Scope(root, (ObjectType) null);
  Node scriptRoot = new Node(Token.SCRIPT);

  // In the initial setUp we have 3 references to var1 (one in each input) and
  // 2 references to var2 (in first and third inputs).
  private static final String VAR1 = "var1";
  private static final String VAR2 = "var2";
  private final ReferenceCollection var1Refs = new ReferenceCollection();
  private final ReferenceCollection var2Refs = new ReferenceCollection();
  private final Reference var1In1Ref =  createRefForTest(INPUT1.getName());
  private final Reference var1In2Ref =  createRefForTest(INPUT2.getName());
  private final Reference var1In3Ref =  createRefForTest(INPUT3.getName());
  private final Reference var2In1Ref =  createRefForTest(INPUT1.getName());
  private final Reference var2In3Ref =  createRefForTest(INPUT3.getName());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    globalScope.declare(VAR1, new Node(Token.NAME), null, INPUT1);
    var1Refs.references = Lists.newArrayList(var1In1Ref,
        var1In2Ref, var1In3Ref);
    globalScope.declare(VAR2, new Node(Token.NAME), null, INPUT1);
    var2Refs.references = Lists.newArrayList(var2In1Ref, var2In3Ref);
    // We recreate these two ReferenceCollection to keep var1Refs and
    // var2Refs intact in update operations for comparison in the tests.
    ReferenceCollection var1TempRefs = new ReferenceCollection();
    var1TempRefs.references = Lists.newArrayList(var1Refs.references);
    ReferenceCollection var2TempRefs = new ReferenceCollection();
    var2TempRefs.references = Lists.newArrayList(var2Refs.references);
    globalMap.put(globalScope.getVar(VAR1), var1TempRefs);
    globalMap.put(globalScope.getVar(VAR2), var2TempRefs);
    map.updateGlobalVarReferences(globalMap, root);
    scriptRoot.putProp(Node.SOURCENAME_PROP, INPUT2.getName());
  }

  /** Tests whether the global variable references are set/reset properly. */
  public void testUpdateGlobalVarReferences_ResetReferences() {
    // First we check the original setup then reset again.
    for (int i = 0; i < 2; i++) {
      assertEquals(var1Refs.references,
          map.getReferences(globalScope.getVar(VAR1)).references);
      assertEquals(var2Refs.references,
          map.getReferences(globalScope.getVar(VAR2)).references);
      map.updateGlobalVarReferences(globalMap, root);
    }
  }

  /** Removes all variable references in second script. */
  public void testUpdateGlobalVarReferences_UpdateScriptNoRef() {
    Map<Var, ReferenceCollection> scriptMap = Maps.newHashMap();
    map.updateGlobalVarReferences(scriptMap, scriptRoot);
    ReferenceCollection refs = map.getReferences(globalScope.getVar(VAR2));
    assertEquals(var2Refs.references, refs.references);
    refs = map.getReferences(globalScope.getVar(VAR1));
    assertEquals(2, refs.references.size());
    assertEquals(var1Refs.references.get(0), refs.references.get(0));
    assertEquals(var1Refs.references.get(2), refs.references.get(1));
  }

  /** Changes variable references in second script. */
  public void testUpdateGlobalVarReferences_UpdateScriptNewRefs() {
    Map<Var, ReferenceCollection> scriptMap = Maps.newHashMap();
    ReferenceCollection newVar1Refs = new ReferenceCollection();
    Reference newVar1In2Ref = createRefForTest(INPUT2.getName());
    newVar1Refs.references = Lists.newArrayList(newVar1In2Ref);
    ReferenceCollection newVar2Refs = new ReferenceCollection();
    Reference newVar2In2Ref = createRefForTest(INPUT2.getName());
    newVar2Refs.references = Lists.newArrayList(newVar2In2Ref);
    scriptMap.put(globalScope.getVar(VAR1), newVar1Refs);
    scriptMap.put(globalScope.getVar(VAR2), newVar2Refs);
    map.updateGlobalVarReferences(scriptMap, scriptRoot);
    ReferenceCollection refs = map.getReferences(globalScope.getVar(VAR1));
    assertEquals(3, refs.references.size());
    assertEquals(var1Refs.references.get(0), refs.references.get(0));
    assertEquals(newVar1In2Ref, refs.references.get(1));
    assertEquals(var1Refs.references.get(2), refs.references.get(2));
    refs = map.getReferences(globalScope.getVar(VAR2));
    assertEquals(3, refs.references.size());
    assertEquals(var2Refs.references.get(0), refs.references.get(0));
    assertEquals(newVar2In2Ref, refs.references.get(1));
    assertEquals(var2Refs.references.get(1), refs.references.get(2));
  }

  /** Changes variable references in second script. */
  public void testUpdateGlobalVarReferences_UpdateScriptNewVar() {
    Map<Var, ReferenceCollection> scriptMap = Maps.newHashMap();
    final String var3 = "var3";
    globalScope.declare(var3, new Node(Token.NAME), null, INPUT2);
    ReferenceCollection newVar3Refs = new ReferenceCollection();
    Reference newVar3In2Ref = createRefForTest(INPUT2.getName());
    newVar3Refs.references = Lists.newArrayList(newVar3In2Ref);
    scriptMap.put(globalScope.getVar(var3), newVar3Refs);
    map.updateGlobalVarReferences(scriptMap, scriptRoot);
    ReferenceCollection refs = map.getReferences(globalScope.getVar(var3));
    assertEquals(1, refs.references.size());
    assertEquals(newVar3In2Ref, refs.references.get(0));
  }

}
