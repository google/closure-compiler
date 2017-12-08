/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.JsFileParser;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.deps.SimpleDependencyInfo;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class for the internal representation of an input to the compiler.
 * Wraps a {@link SourceAst} and maintain state such as module for the input and
 * whether the input is an extern. Also calculates provided and required types.
 *
 */
public class CompilerInput implements SourceAst, DependencyInfo {

  private static final long serialVersionUID = 1L;

  // Info about where the file lives.
  private JSModule module;
  private final InputId id;

  // The AST.
  private final SourceAst ast;

  // DependencyInfo to delegate to.
  private DependencyInfo dependencyInfo;
  private final List<String> extraRequires = new ArrayList<>();
  private final List<String> extraProvides = new ArrayList<>();
  private final List<String> orderedRequires = new ArrayList<>();
  private boolean hasFullParseDependencyInfo = false;
  private ModuleType jsModuleType = ModuleType.NONE;

  // An AbstractCompiler for doing parsing.
  // We do not want to persist this across serialized state.
  private transient AbstractCompiler compiler;
  private transient ModulePath modulePath;

  public CompilerInput(SourceAst ast) {
    this(ast, ast.getSourceFile().getName(), false);
  }

  public CompilerInput(SourceAst ast, boolean isExtern) {
    this(ast, ast.getInputId(), isExtern);
  }

  public CompilerInput(SourceAst ast, String inputId, boolean isExtern) {
    this(ast, new InputId(inputId), isExtern);
  }

  public CompilerInput(SourceAst ast, InputId inputId, boolean isExtern) {
    this.ast = ast;
    this.id = inputId;

    // TODO(nicksantos): Add a precondition check here. People are passing
    // in null, but they should not be.
    if (ast != null && ast.getSourceFile() != null) {
      ast.getSourceFile().setIsExtern(isExtern);
    }
  }

  public CompilerInput(SourceFile file) {
    this(file, false);
  }

  public CompilerInput(SourceFile file, boolean isExtern) {
    this(new JsAst(file), isExtern);
  }

  /**
   * Using the RecoverableJsAst, creates a CompilerInput that can be reset() to be safe to reuse
   * in multiple compiler invocations.
   */
  public static CompilerInput makePersistentInput(SourceFile file) {
    SourceAst ast = new RecoverableJsAst(new JsAst(file), true);
    return new CompilerInput(ast, file.isExtern());
  }

  /** Returns a name for this input. Must be unique across all inputs. */
  @Override
  public InputId getInputId() {
    return id;
  }

  /** Returns a name for this input. Must be unique across all inputs. */
  @Override
  public String getName() {
    return id.getIdName();
  }

  /** Gets the path relative to closure-base, if one is available. */
  @Override
  public String getPathRelativeToClosureBase() {
    // TODO(nicksantos): Implement me.
    throw new UnsupportedOperationException();
  }

  @Override
  public Node getAstRoot(AbstractCompiler compiler) {
    Node root = ast.getAstRoot(compiler);
    // The root maybe null if the AST can not be created.
    if (root != null) {
      checkState(root.isScript());
      checkNotNull(root.getInputId());
    }
    return root;
  }

  @Override
  public void clearAst() {
    ast.clearAst();
  }

  @Override
  public SourceFile getSourceFile() {
    return ast.getSourceFile();
  }

  @Override
  public void setSourceFile(SourceFile file) {
    ast.setSourceFile(file);
  }

  /** Sets an abstract compiler for doing parsing. */
  public void setCompiler(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /** Gets a list of types depended on by this input. */
  @Override
  public ImmutableList<String> getRequires() {
    if (hasFullParseDependencyInfo) {
      return ImmutableList.copyOf(orderedRequires);
    }

    return getDependencyInfo().getRequires();
  }

  @Override
  public ImmutableList<String> getWeakRequires() {
    return getDependencyInfo().getWeakRequires();
  }

  /**
   * Gets a list of types depended on by this input,
   * but does not attempt to regenerate the dependency information.
   * Typically this occurs from module rewriting.
   */
  ImmutableCollection<String> getKnownRequires() {
    return concat(
        dependencyInfo != null ? dependencyInfo.getRequires() : ImmutableList.<String>of(),
        extraRequires);
  }

  /** Gets a list of types provided by this input. */
  @Override
  public ImmutableList<String> getProvides() {
    return getDependencyInfo().getProvides();
  }

  /**
   * Gets a list of types provided, but does not attempt to
   * regenerate the dependency information. Typically this occurs
   * from module rewriting.
   */
  ImmutableCollection<String> getKnownProvides() {
    return concat(
        dependencyInfo != null ? dependencyInfo.getProvides() : ImmutableList.<String>of(),
        extraProvides);
  }

  /**
   * Registers a type that this input defines. Includes both explicitly declared namespaces via
   * goog.provide and goog.module calls as well as implicit namespaces provided by module rewriting.
   */
  public void addProvide(String provide) {
    extraProvides.add(provide);
  }

  /** Registers a type that this input depends on in the order seen in the file. */
  public boolean addOrderedRequire(String require) {
    if (!orderedRequires.contains(require)) {
      orderedRequires.add(require);
      return true;
    }
    return false;
  }

  public void setHasFullParseDependencyInfo(boolean hasFullParseDependencyInfo) {
    this.hasFullParseDependencyInfo = hasFullParseDependencyInfo;
  }

  public ModuleType getJsModuleType() {
    return jsModuleType;
  }

  public void setJsModuleType(ModuleType moduleType) {
    jsModuleType = moduleType;
  }

  /** Registers a type that this input depends on. */
  public void addRequire(String require) {
    extraRequires.add(require);
  }

  /**
   * Returns the DependencyInfo object, generating it lazily if necessary.
   */
  private DependencyInfo getDependencyInfo() {
    if (dependencyInfo == null) {
      dependencyInfo = generateDependencyInfo();
    }
    if (!extraRequires.isEmpty() || !extraProvides.isEmpty()) {
      dependencyInfo =
          SimpleDependencyInfo.builder(getName(), getName())
              .setProvides(concat(dependencyInfo.getProvides(), extraProvides))
              .setRequires(concat(dependencyInfo.getRequires(), extraRequires))
              .setLoadFlags(dependencyInfo.getLoadFlags())
              .build();
      extraRequires.clear();
      extraProvides.clear();
    }
    return dependencyInfo;
  }

  /**
   * Generates the DependencyInfo by scanning and/or parsing the file.
   * This is called lazily by getDependencyInfo, and does not take into
   * account any extra requires/provides added by {@link #addRequire}
   * or {@link #addProvide}.
   */
  private DependencyInfo generateDependencyInfo() {
    Preconditions.checkNotNull(compiler, "Expected setCompiler to be called first: %s", this);
    Preconditions.checkNotNull(
        compiler.getErrorManager(), "Expected compiler to call an error manager: %s", this);

    // If the code is a JsAst, then it was originally JS code, and is compatible with the
    // regex-based parsing of JsFileParser.
    if (ast instanceof JsAst && JsFileParser.isSupported()) {
      // Look at the source code.
      // Note: it's OK to use getName() instead of
      // getPathRelativeToClosureBase() here because we're not using
      // this to generate deps files. (We're only using it for
      // symbol dependencies.)
      try {
        DependencyInfo info =
            (new JsFileParser(compiler.getErrorManager()))
            .setIncludeGoogBase(true)
            .parseFile(getName(), getName(), getCode());
        return new LazyParsedDependencyInfo(info, (JsAst) ast, compiler);
      } catch (IOException e) {
        compiler.getErrorManager().report(CheckLevel.ERROR,
            JSError.make(AbstractCompiler.READ_ERROR, getName()));
        return SimpleDependencyInfo.EMPTY;
      }
    } else {
      // Otherwise, just look at the AST.

      DepsFinder finder = new DepsFinder(getPath());
      Node root = getAstRoot(compiler);
      if (root == null) {
        return SimpleDependencyInfo.EMPTY;
      }

      finder.visitTree(root);

      // TODO(nicksantos|user): This caching behavior is a bit
      // odd, and only works if you assume the exact call flow that
      // clients are currently using.  In that flow, they call
      // getProvides(), then remove the goog.provide calls from the
      // AST, and then call getProvides() again.
      //
      // This won't work for any other call flow, or any sort of incremental
      // compilation scheme. The API needs to be fixed so callers aren't
      // doing weird things like this, and then we should get rid of the
      // multiple-scan strategy.
      return SimpleDependencyInfo.builder("", "")
          .setProvides(finder.provides)
          .setRequires(finder.requires)
          .setLoadFlags(finder.loadFlags)
          .build();
    }
  }

  private static class DepsFinder {
    private final Map<String, String> loadFlags = new TreeMap<>();
    private final List<String> provides = new ArrayList<>();
    private final List<String> requires = new ArrayList<>();
    private final ModulePath modulePath;

    DepsFinder(ModulePath modulePath) {
      this.modulePath = modulePath;
    }

    void visitTree(Node n) {
      visitSubtree(n, null);
      checkArgument(n.isScript());
      FeatureSet features = (FeatureSet) n.getProp(Node.FEATURE_SET);
      if (features != null) {
        // Only add the "lang" load flag if it's not the default (es3), so that
        // legacy deps files will remain unchanged (i.e. load flags omitted).
        String version = features.version();
        if (!version.equals("es3")) {
          loadFlags.put("lang", version);
        }
      }
    }

    void visitSubtree(Node n, Node parent) {
      switch (n.getToken()) {
        case CALL:
          if (n.hasTwoChildren()
              && n.getFirstChild().isGetProp()
              && n.getFirstFirstChild().matchesQualifiedName("goog")) {

            if (!requires.contains("goog")) {
              requires.add("goog");
            }

            Node callee = n.getFirstChild();
            Node argument = n.getLastChild();
            switch (callee.getLastChild().getString()) {

              case "module":
                loadFlags.put("module", "goog");
                // Fall-through
              case "provide":
                if (!argument.isString()) {
                  return;
                }
                provides.add(argument.getString());
                return;

              case "require":
                if (!argument.isString()) {
                  return;
                }
                requires.add(argument.getString());
                return;

              case "loadModule":
                // Process the block of the loadModule argument
                n = argument.getLastChild();
                break;

              default:
                return;
            }
          }
          break;

        case MODULE_BODY:
          if (!parent.getBooleanProp(Node.GOOG_MODULE)) {
            provides.add(modulePath.toModuleName());
            loadFlags.put("module", "es6");
          }
          break;

        case IMPORT:
          visitEs6ModuleName(n.getLastChild(), n);
          return;

        case EXPORT:
          if (NodeUtil.isExportFrom(n)) {
            visitEs6ModuleName(n.getLastChild(), n);
          }
          return;

        case VAR:
          if (n.getFirstChild().matchesQualifiedName("goog")
              && NodeUtil.isNamespaceDecl(n.getFirstChild())) {
            provides.add("goog");
          }
          break;

        case EXPR_RESULT:
        case CONST:
        case BLOCK:
        case SCRIPT:
        case NAME:
        case DESTRUCTURING_LHS:
        case LET:
          break;

        default:
          return;
      }

      for (Node child = n.getFirstChild();
           child != null; child = child.getNext()) {
        visitSubtree(child, n);
      }
    }

    void visitEs6ModuleName(Node n, Node parent) {
      checkArgument(n.isString());
      checkArgument(parent.isExport() || parent.isImport());

      // TODO(blickly): Move this (and the duplicated logic in JsFileParser/Es6RewriteModules)
      // into ModuleLoader.
      String moduleName = n.getString();
      if (moduleName.startsWith("goog:")) {
        requires.add(moduleName.substring(5)); // cut off the "goog:" prefix
        return;
      }
      ModulePath importedModule =
          modulePath.resolveJsModule(
              moduleName, modulePath.toString(), n.getLineno(), n.getCharno());

      if (importedModule == null) {
        importedModule = modulePath.resolveModuleAsPath(moduleName);
      }

      requires.add(importedModule.toModuleName());
    }
  }

  public String getCode() throws IOException {
    return getSourceFile().getCode();
  }

  /** Returns the module to which the input belongs. */
  public JSModule getModule() {
    return module;
  }

  /** Sets the module to which the input belongs. */
  public void setModule(JSModule module) {
    // An input may only belong to one module.
    checkArgument(module == null || this.module == null || this.module == module);
    this.module = module;
  }

  /** Overrides the module to which the input belongs. */
  void overrideModule(JSModule module) {
    this.module = module;
  }

  public boolean isExtern() {
    if (ast == null || ast.getSourceFile() == null) {
      return false;
    }
    return ast.getSourceFile().isExtern();
  }

  void setIsExtern(boolean isExtern) {
    if (ast == null || ast.getSourceFile() == null) {
      return;
    }
    ast.getSourceFile().setIsExtern(isExtern);
  }

  public int getLineOffset(int lineno) {
    return ast.getSourceFile().getLineOffset(lineno);
  }

  /** @return The number of lines in this input. */
  public int getNumLines() {
    return ast.getSourceFile().getNumLines();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public ImmutableMap<String, String> getLoadFlags() {
    return getDependencyInfo().getLoadFlags();
  }

  @Override
  public boolean isModule() {
    return "goog".equals(getLoadFlags().get("module"));
  }

  private static <T> ImmutableSet<T> concat(Iterable<T> first, Iterable<T> second) {
    return ImmutableSet.<T>builder().addAll(first).addAll(second).build();
  }

  ModulePath getPath() {
    if (modulePath == null) {
      ModuleLoader moduleLoader = compiler.getModuleLoader();
      this.modulePath = moduleLoader.resolve(getName());
    }
    return modulePath;
  }

  /**
   * Resets the compiler input for reuse in another compile.
   */
  public void reset() {
    this.module = null;
    this.ast.clearAst();
  }

  /** JavaScript module type. */
  public enum ModuleType {
    NONE,
    GOOG,
    ES6,
    COMMONJS,
    JSON,
    IMPORTED_SCRIPT
  }
}
