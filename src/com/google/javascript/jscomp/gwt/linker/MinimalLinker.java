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
import com.google.gwt.core.ext.linker.SelectionProperty;
import com.google.gwt.core.linker.SymbolMapsLinker;

/**
 * Simple single-script linker that doesn't add any dependencies on the browser.
 *
 * This is intended to generate JS for servers, Node, or in a self-contained way inside browsers. It
 * doesn't support permutations, nor does it allow late-loading code.
 */
@LinkerOrder(Order.PRIMARY)
public class MinimalLinker extends AbstractLinker {

  /**
   * A configuration property indicating whether {@link MinimalLinker} should export via JSInterop.
   */
  private static final String EXPORT_PROPERTY = "linker.minimal.export";

  /**
   * @param context LinkerContext containing properties
   * @return Whether to export, default false
   */
  private boolean getExportProperty(LinkerContext context) {
    for (SelectionProperty prop : context.getProperties()) {
      if (EXPORT_PROPERTY.equals(prop.getName())) {
        String value = prop.tryGetValue();
        return value == null ? false : Boolean.parseBoolean(value);
      }
    }
    return false;
  }

  /**
   * Formats the application's JS code for output.
   *
   * @param js Code to format.
   * @param export Whether to export via JSInterop.
   * @return Formatted, linked code.
   */
  private String formatOutput(String js, boolean export) {
    StringBuilder output = new StringBuilder();

    // If $wnd is set to this, then JSInterop's normal export will run. If export is false, fake
    // out $wnd ith an empty object (plus Error to work around StackTraceCreator using it in a
    // static block).
    output.append("(function(){var $wnd=").append(export ? "this" : "{'Error':{}}").append(";");

    // Shadow $doc, $moduleName and $moduleBase.
    output.append("var $doc={},$moduleName,$moduleBase;");

    // Append output JS.
    output.append(js);

    // Reset $wnd, and call gwtOnLoad if defined. This invokes the onModuleLoad methods of all
    // loaded modules.
    output.append(
        "this['$gwtExport']=$wnd;$wnd=this;typeof gwtOnLoad==='function'&&gwtOnLoad()})();");

    return output.toString();
  }

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
    boolean export = getExportProperty(context);

    for (CompilationResult result : toReturn.find(CompilationResult.class)) {
      String[] js = result.getJavaScript();
      Preconditions.checkArgument(js.length == 1, "MinimalLinker doesn't support GWT.runAsync");

      String output = formatOutput(js[0], export);
      toReturn.add(emitString(logger, output, context.getModuleName() + ".js"));
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
