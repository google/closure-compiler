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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.CompilerInput.ModuleType;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.trees.Comment;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Forwards calls to another compiler. Subclass this to intercept methods.
 */
class ForwardingCompiler extends AbstractCompiler {
  private final AbstractCompiler abstractCompiler;

  ForwardingCompiler(AbstractCompiler abstractCompiler) {
    this.abstractCompiler = abstractCompiler;
  }

  // Below here everything is an auto-generated delegate.
  // TODO(bangert): Do we have an @Auto equivalent of that?

  @Override
  public void report(JSError error) {
    abstractCompiler.report(error);
  }

  @Override
  public void beforePass(String passName) {
    abstractCompiler.beforePass(passName);
  }

  @Override
  public void afterPass(String passName) {
    abstractCompiler.afterPass(passName);
  }

  @Override
  public CompilerInput getInput(InputId inputId) {
    return abstractCompiler.getInput(inputId);
  }

  @Nullable
  @Override
  public SourceFile getSourceFileByName(String sourceName) {
    return abstractCompiler.getSourceFileByName(sourceName);
  }

  @Nullable
  @Override
  public Node getScriptNode(String filename) {
    return abstractCompiler.getScriptNode(filename);
  }

  @Override
  public JSModuleGraph getModuleGraph() {
    return abstractCompiler.getModuleGraph();
  }

  @Override
  public List<CompilerInput> getInputsInOrder() {
    return abstractCompiler.getInputsInOrder();
  }

  @Override
  public int getNumberOfInputs() {
    return abstractCompiler.getNumberOfInputs();
  }

  @Override
  public void addExportedNames(Set<String> exportedVariableNames) {
    abstractCompiler.addExportedNames(exportedVariableNames);
  }

  @Override
  public Set<String> getExportedNames() {
    return abstractCompiler.getExportedNames();
  }

  @Override
  public void setVariableMap(VariableMap variableMap) {
    abstractCompiler.setVariableMap(variableMap);
  }

  @Override
  public void setPropertyMap(VariableMap propertyMap) {
    abstractCompiler.setPropertyMap(propertyMap);
  }

  @Override
  public void setStringMap(VariableMap stringMap) {
    abstractCompiler.setStringMap(stringMap);
  }

  @Override
  public FunctionNames getFunctionNames() {
    return abstractCompiler.getFunctionNames();
  }

  @Override
  public void setFunctionNames(FunctionNames functionNames) {
    abstractCompiler.setFunctionNames(functionNames);
  }

  @Override
  public void setCssNames(Map<String, Integer> newCssNames) {
    abstractCompiler.setCssNames(newCssNames);
  }

  @Override
  public void setIdGeneratorMap(String serializedIdMappings) {
    abstractCompiler.setIdGeneratorMap(serializedIdMappings);
  }

  @Override
  public IdGenerator getCrossModuleIdGenerator() {
    return abstractCompiler.getCrossModuleIdGenerator();
  }

  @Override
  public void setAnonymousFunctionNameMap(VariableMap functionMap) {
    abstractCompiler.setAnonymousFunctionNameMap(functionMap);
  }

  @Override
  public MostRecentTypechecker getMostRecentTypechecker() {
    return abstractCompiler.getMostRecentTypechecker();
  }

  @Override
  public void setMostRecentTypechecker(MostRecentTypechecker mostRecent) {
    abstractCompiler.setMostRecentTypechecker(mostRecent);
  }

  @Override
  public JSTypeRegistry getTypeRegistry() {
    return abstractCompiler.getTypeRegistry();
  }

  @Override
  public TypeIRegistry getTypeIRegistry() {
    return abstractCompiler.getTypeIRegistry();
  }

  @Override
  public void clearTypeIRegistry() {
    abstractCompiler.clearTypeIRegistry();
  }

  @Override
  public void forwardDeclareType(String typeName) {
    abstractCompiler.forwardDeclareType(typeName);
  }

  @Override
  public ScopeCreator getTypedScopeCreator() {
    return abstractCompiler.getTypedScopeCreator();
  }

  @Override
  public TypedScope getTopScope() {
    return abstractCompiler.getTopScope();
  }

  @Override
  public IncrementalScopeCreator getScopeCreator() {
    return abstractCompiler.getScopeCreator();
  }

  @Override
  public void putScopeCreator(IncrementalScopeCreator creator) {
    abstractCompiler.putScopeCreator(creator);
  }

  @Override
  public void throwInternalError(String msg, Throwable cause) {
    abstractCompiler.throwInternalError(msg, cause);
  }

  @Override
  public CodingConvention getCodingConvention() {
    return abstractCompiler.getCodingConvention();
  }

  @Override
  public void reportChangeToEnclosingScope(Node n) {
    abstractCompiler.reportChangeToEnclosingScope(n);
  }

  @Override
  public void reportChangeToChangeScope(Node changeScopeRoot) {
    abstractCompiler.reportChangeToChangeScope(changeScopeRoot);
  }

  @Override
  public void reportFunctionDeleted(Node node) {
    abstractCompiler.reportFunctionDeleted(node);
  }

  @Override
  public CssRenamingMap getCssRenamingMap() {
    return abstractCompiler.getCssRenamingMap();
  }

  @Override
  public void setCssRenamingMap(CssRenamingMap map) {
    abstractCompiler.setCssRenamingMap(map);
  }

  @Override
  public Node getNodeForCodeInsertion(@Nullable JSModule module) {
    return abstractCompiler.getNodeForCodeInsertion(module);
  }

  @Override
  public TypeValidator getTypeValidator() {
    return abstractCompiler.getTypeValidator();
  }

  @Override
  public Iterable<TypeMismatch> getTypeMismatches() {
    return abstractCompiler.getTypeMismatches();
  }

  @Override
  public Iterable<TypeMismatch> getImplicitInterfaceUses() {
    return abstractCompiler.getImplicitInterfaceUses();
  }

  @Override
  public <T extends TypeIRegistry> T getGlobalTypeInfo() {
    return abstractCompiler.getGlobalTypeInfo();
  }

  @Override
  public void setExternExports(String externExports) {
    abstractCompiler.setExternExports(externExports);
  }

  @Override
  public Node parseSyntheticCode(String code) {
    return abstractCompiler.parseSyntheticCode(code);
  }

  @Override
  public Node parseSyntheticCode(String filename, String code) {
    return abstractCompiler.parseSyntheticCode(filename, code);
  }

  @VisibleForTesting
  @Override
  public Node parseTestCode(String code) {
    return abstractCompiler.parseTestCode(code);
  }

  @Override
  public String toSource() {
    return abstractCompiler.toSource();
  }

  @Override
  public String toSource(Node root) {
    return abstractCompiler.toSource(root);
  }

  @Override
  public ErrorReporter getDefaultErrorReporter() {
    return abstractCompiler.getDefaultErrorReporter();
  }

  @Override
  public ReverseAbstractInterpreter getReverseAbstractInterpreter() {
    return abstractCompiler.getReverseAbstractInterpreter();
  }

  @Override
  public LifeCycleStage getLifeCycleStage() {
    return abstractCompiler.getLifeCycleStage();
  }

  @Override
  public void setLifeCycleStage(LifeCycleStage stage) {
    abstractCompiler.setLifeCycleStage(stage);
  }

  @Override
  public Supplier<String> getUniqueNameIdSupplier() {
    return abstractCompiler.getUniqueNameIdSupplier();
  }

  @Override
  public boolean hasHaltingErrors() {
    return abstractCompiler.hasHaltingErrors();
  }

  @Override
  public void addChangeHandler(CodeChangeHandler handler) {
    abstractCompiler.addChangeHandler(handler);
  }

  @Override
  public void removeChangeHandler(CodeChangeHandler handler) {
    abstractCompiler.removeChangeHandler(handler);
  }

  @Override
  public void addIndexProvider(IndexProvider<?> indexProvider) {
    abstractCompiler.addIndexProvider(indexProvider);
  }

  @Override
  public <T> T getIndex(Class<T> type) {
    return abstractCompiler.getIndex(type);
  }

  @Override
  public int getChangeStamp() {
    return abstractCompiler.getChangeStamp();
  }

  @Override
  public List<Node> getChangedScopeNodesForPass(String passName) {
    return abstractCompiler.getChangedScopeNodesForPass(passName);
  }

  @Override
  public List<Node> getDeletedScopeNodesForPass(String passName) {
    return abstractCompiler.getDeletedScopeNodesForPass(passName);
  }

  @Override
  public void incrementChangeStamp() {
    abstractCompiler.incrementChangeStamp();
  }

  @Override
  public Node getJsRoot() {
    return abstractCompiler.getJsRoot();
  }

  @Override
  public boolean hasScopeChanged(Node n) {
    return abstractCompiler.hasScopeChanged(n);
  }

  @Override
  public Config getParserConfig(ConfigContext context) {
    return abstractCompiler.getParserConfig(context);
  }

  @Override
  public void prepareAst(Node root) {
    abstractCompiler.prepareAst(root);
  }

  @Override
  public ErrorManager getErrorManager() {
    return abstractCompiler.getErrorManager();
  }

  @Override
  public boolean areNodesEqualForInlining(Node n1, Node n2) {
    return abstractCompiler.areNodesEqualForInlining(n1, n2);
  }

  @Override
  public void setHasRegExpGlobalReferences(boolean references) {
    abstractCompiler.setHasRegExpGlobalReferences(references);
  }

  @Override
  public boolean hasRegExpGlobalReferences() {
    return abstractCompiler.hasRegExpGlobalReferences();
  }

  @Override
  public CheckLevel getErrorLevel(JSError error) {
    return abstractCompiler.getErrorLevel(error);
  }

  @Override
  public void process(CompilerPass pass) {
    abstractCompiler.process(pass);
  }

  @Override
  public Node getRoot() {
    return abstractCompiler.getRoot();
  }

  @Override
  public CompilerOptions getOptions() {
    return abstractCompiler.getOptions();
  }

  @Override
  public FeatureSet getFeatureSet() {
    return abstractCompiler.getFeatureSet();
  }

  @Override
  public void setFeatureSet(FeatureSet fs) {
    abstractCompiler.setFeatureSet(fs);
  }

  @Override
  public void updateGlobalVarReferences(
      Map<Var, ReferenceCollection> refMapPatch, Node collectionRoot) {
    abstractCompiler.updateGlobalVarReferences(refMapPatch, collectionRoot);
  }

  @Override
  public GlobalVarReferenceMap getGlobalVarReferences() {
    return abstractCompiler.getGlobalVarReferences();
  }

  @Override
  public CompilerInput getSynthesizedExternsInput() {
    return abstractCompiler.getSynthesizedExternsInput();
  }

  @Override
  public CompilerInput getSynthesizedExternsInputAtEnd() {
    return abstractCompiler.getSynthesizedExternsInputAtEnd();
  }

  @Override
  public double getProgress() {
    return abstractCompiler.getProgress();
  }

  @Override
  public String getLastPassName() {
    return abstractCompiler.getLastPassName();
  }

  @Override
  public void setProgress(double progress, @Nullable String lastPassName) {
    abstractCompiler.setProgress(progress, lastPassName);
  }

  @Override
  public Node ensureLibraryInjected(String resourceName, boolean force) {
    return abstractCompiler.ensureLibraryInjected(resourceName, force);
  }

  @Override
  public Set<String> getExternProperties() {
    return abstractCompiler.getExternProperties();
  }

  @Override
  public void setExternProperties(Set<String> externProperties) {
    abstractCompiler.setExternProperties(externProperties);
  }

  @Override
  public void addInputSourceMap(String name, SourceMapInput sourceMap) {
    abstractCompiler.addInputSourceMap(name, sourceMap);
  }

  @Override
  public void addComments(String filename, List<Comment> comments) {
    abstractCompiler.addComments(filename, comments);
  }

  @Override
  ImmutableMap<String, PropertyAccessKind> getExternGetterAndSetterProperties() {
    return null;
  }

  @Override
  void setExternGetterAndSetterProperties(
      ImmutableMap<String, PropertyAccessKind> externGetterAndSetterProperties) {

  }

  @Override
  ImmutableMap<String, PropertyAccessKind> getSourceGetterAndSetterProperties() {
    return null;
  }

  @Override
  void setSourceGetterAndSetterProperties(
      ImmutableMap<String, PropertyAccessKind> externGetterAndSetterProperties) {

  }

  @Override
  public List<Comment> getComments(String filename) {
    return abstractCompiler.getComments(filename);
  }

  @Override
  public ImmutableMap<String, Node> getDefaultDefineValues() {
    return abstractCompiler.getDefaultDefineValues();
  }

  @Override
  public void setDefaultDefineValues(ImmutableMap<String, Node> values) {
    abstractCompiler.setDefaultDefineValues(values);
  }

  @Override
  public ModuleLoader getModuleLoader() {
    return abstractCompiler.getModuleLoader();
  }

  @Override
  public ModuleType getModuleTypeByName(String moduleName) {
    return abstractCompiler.getModuleTypeByName(moduleName);
  }

  @Override
  public void setAnnotation(String key, Object object) {
    abstractCompiler.setAnnotation(key, object);
  }

  @Nullable
  @Override
  public Object getAnnotation(String key) {
    return abstractCompiler.getAnnotation(key);
  }

  @Nullable
  @Override
  public PersistentInputStore getPersistentInputStore() {
    return abstractCompiler.getPersistentInputStore();
  }

  @Override
  public void setPersistentInputStore(PersistentInputStore persistentInputStore) {
    abstractCompiler.setPersistentInputStore(persistentInputStore);
  }

  @Override
  public String getSourceLine(String sourceName, int lineNumber) {
    return abstractCompiler.getSourceLine(sourceName, lineNumber);
  }

  @Override
  public Region getSourceRegion(String sourceName, int lineNumber) {
    return abstractCompiler.getSourceRegion(sourceName, lineNumber);
  }

  @Override
  public OriginalMapping getSourceMapping(String sourceName, int lineNumber, int columnNumber) {
    return abstractCompiler.getSourceMapping(sourceName, lineNumber, columnNumber);
  }
}
