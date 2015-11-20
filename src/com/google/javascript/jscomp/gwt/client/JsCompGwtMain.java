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

package com.google.javascript.jscomp.gwt.client;

import static com.google.javascript.jscomp.CompilationLevel.ADVANCED_OPTIMIZATIONS;
import static com.google.javascript.jscomp.WarningLevel.VERBOSE;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;

import jsinterop.annotations.JsMethod;

/**
 * A simple demo for the GWT-compiled JSCompiler
 *
 * @author moz@google.com (Michael Zhou)
 */
public class JsCompGwtMain implements EntryPoint {
  @JsMethod(name = "JsCompGwtMain", namespace = "jscompiler")
  public static String compile(String js) {
    CompilerOptions options = new CompilerOptions();
    ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    VERBOSE.setOptionsForWarningLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
    options.setCheckSymbols(true);
    options.setCheckTypes(true);
    options.setPrettyPrint(true);
    options.getDependencyOptions().setDependencySorting(false);
    options.getDependencyOptions().setEs6ModuleOrder(false);
    SourceFile externs = SourceFile.fromCode("externs.js", "var window;");
    SourceFile src = SourceFile.fromCode("src.js", js);
    Compiler compiler = new Compiler();
    compiler.compile(externs, src, options);
    return compiler.toSource();
  }

  @Override
  public void onModuleLoad() {
    final TextArea tb = new TextArea();
    tb.setCharacterWidth(80);
    tb.setVisibleLines(25);
    tb.setReadOnly(true);

    final TextArea ta = new TextArea();
    ta.setCharacterWidth(80);
    ta.setVisibleLines(25);
    ta.addKeyUpHandler(new KeyUpHandler() {
      public void onKeyUp(KeyUpEvent event) {
        clearConsole();
        tb.setValue(compile(ta.getValue()));
      }
    });

    VerticalPanel panel = new VerticalPanel();
    panel.add(ta);
    panel.add(tb);
    RootPanel.get().add(panel);
  }

  private static native void clearConsole() /*-{
    console.clear();
  }-*/;
}
