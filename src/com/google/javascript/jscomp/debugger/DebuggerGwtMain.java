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

package com.google.javascript.jscomp.debugger;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.CellPanel;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;

import java.util.Arrays;
import java.util.List;

/**
 * A GWT-based version of the Closure Compiler debugger
 *
 * @author moz@google.com (Michael Zhou)
 * @author blickly@google.com (Ben Lickly)
 */
public class DebuggerGwtMain implements EntryPoint {
  final CompilerOptions options = new CompilerOptions();
  final TextArea externs = new TextArea();
  final TextArea input0 = new TextArea();
  final VerticalPanel rightPane = new VerticalPanel();

  private void doCompile() {
    SourceFile externFile = SourceFile.fromCode("externs", externs.getValue());
    SourceFile srcFile = SourceFile.fromCode("input0", input0.getValue());
    Compiler compiler = new Compiler();
    try {
      Result result = compiler.compile(externFile, srcFile, options);
      updateUi(compiler, result);
    } catch (Exception e) {
      updateUiException(e);
    }
  }

  private void updateUi(Compiler compiler, Result result) {
    rightPane.clear();
    rightPane.add(new HTML("<h4>Output</h4>"));
    String outputCode = compiler.toSource();
    rightPane.add(new Label(outputCode));
    rightPane.add(new HTML("<h4>Warnings</h4>"));
    List<JSError> errors = Arrays.asList(result.errors);
    List<JSError> warnings = Arrays.asList(result.warnings);
    rightPane.add(new Label(Joiner.on("\n\n").join(Iterables.concat(errors, warnings))));
    rightPane.add(new HTML("<h4>AST</h4>"));
    String outputAst = compiler.getRoot().toStringTree();
    rightPane.add(new Label(outputAst));
  }

  private void updateUiException(Exception e) {
    rightPane.clear();
    rightPane.add(new HTML("<h1>Exception</h1>"));
    rightPane.add(new Label(e.toString()));
  }

  @Override
  public void onModuleLoad() {
    externs.setCharacterWidth(80);
    externs.setVisibleLines(5);
    externs.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        doCompile();
      }
    });

    input0.setCharacterWidth(80);
    input0.setVisibleLines(25);
    input0.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        doCompile();
      }
    });

    HorizontalPanel panel = new HorizontalPanel();
    VerticalPanel leftPane = new VerticalPanel();
    leftPane.add(new HTML("<h4>Externs</h4>"));
    leftPane.add(externs);
    leftPane.add(new HTML("<h4>Input</h4>"));
    leftPane.add(input0);
    leftPane.add(new HTML("<h4>Options</h4>"));
    createCheckboxes(leftPane);
    panel.add(leftPane);
    panel.add(rightPane);
    RootPanel.get().add(panel);
  }

  private void createCheckboxes(CellPanel checkboxPanel) {
    for (final CompilationParam param : CompilationParam.getSortedValues()) {
      CheckBox cb = new CheckBox(param.toString());
      cb.setValue(param.getDefaultValue());
      param.apply(options, param.getDefaultValue());
      cb.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          boolean checked = ((CheckBox) event.getSource()).getValue();
          param.apply(options, checked);
          doCompile();
        }
      });
      checkboxPanel.add(cb);
    }
  }
}
