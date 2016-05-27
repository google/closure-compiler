/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.gwt.linker;

import com.google.common.base.Preconditions;
import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.CompilationResult;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.LinkerOrder.Order;
import com.google.gwt.core.linker.SymbolMapsLinker;
import com.google.gwt.dev.util.Util;

/**
 * Simple single-script linker that doesn't add any dependence on the browser.
 * This is intended to generate server-side runnable JS.  It doesn't support
 * permutations, nor does it allow late-loading code.
 */
@LinkerOrder(Order.PRIMARY)
public class MinimalLinker extends AbstractLinker {

  private static final String PREFIX = "(function(){var $wnd=this;var $doc={};";
  private static final String SUFFIX = "})();";

  @Override
  public String getDescription() {
    return "Minimal";
  }

  @Override
  public ArtifactSet link(TreeLogger logger, LinkerContext context, ArtifactSet artifacts)
      throws UnableToCompleteException {
    ArtifactSet toReturn = link(logger, context, artifacts, true);
    toReturn = link(logger, context, toReturn, false);
    return toReturn;
  }

  @Override
  public ArtifactSet link(
      TreeLogger logger, LinkerContext context, ArtifactSet artifacts, boolean onePermutation)
      throws UnableToCompleteException {
    ArtifactSet toReturn = new ArtifactSet(artifacts);
    ArtifactSet writableArtifacts = new ArtifactSet(artifacts);

    for (CompilationResult result : toReturn.find(CompilationResult.class)) {
      String[] js = result.getJavaScript();
      Preconditions.checkArgument(js.length == 1, "MinimalLinker doesn't support GWT.runAsync");

      byte[] primary = Util.getBytes(PREFIX + js[0] + SUFFIX);
      toReturn.add(emitBytes(logger, primary, context.getModuleName() + ".js"));
    }

    for (SymbolMapsLinker.ScriptFragmentEditsArtifact ea :
        writableArtifacts.find(SymbolMapsLinker.ScriptFragmentEditsArtifact.class)) {
      toReturn.add(ea);
    }
    return toReturn;
  }

  @Override
  public boolean supportsDevModeInJunit(LinkerContext context) {
    return false;
  }
}
