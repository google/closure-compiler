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
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.JsFileRegexParser;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.ModulePath;
import com.google.javascript.jscomp.deps.SimpleDependencyInfo;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A class for the internal representation of an input to the compiler. Wraps a {@link JsAst} and
 * maintain state such as the {@link JSChunk} for the input and whether the input is an extern. Also
 * calculates provided and required types.
 */
public class CompilerInput implements DependencyInfo {

  private static final long serialVersionUID = 2L;

  // Info about where the file lives.
  private JSChunk chunk;
  private final InputId id;
  private final SourceFile sourceFile;

  // The lazily constructed AST.
  private final JsAst ast = new JsAst();

  // DependencyInfo to delegate to.
  private DependencyInfo dependencyInfo;
  private final List<Require> extraRequires = new ArrayList<>();
  private final List<String> extraProvides = new ArrayList<>();
  private final List<Require> orderedRequires = new ArrayList<>();
  private final List<String> dynamicRequires = new ArrayList<>();
  // Modules imported by goog.requireDynamic()
  private final List<String> requireDynamicImports = new ArrayList<>();
  private boolean hasFullParseDependencyInfo = false;
  private ModuleType jsModuleType = ModuleType.NONE;

  /**
   * If this input file has a MODULE_BODY (i.e. it is a goog.module() or ES module),
   * TypedScopeCreator will store the scope created for that Node here.
   *
   * <p>DefaultPassConfig is responsible for erasing this value when later use of it is not needed.
   */
  private @Nullable TypedScope typedScope;

  public void setTypedScope(@Nullable TypedScope typedScope) {
    this.typedScope = typedScope;
  }

  public @Nullable TypedScope getTypedScope() {
    return this.typedScope;
  }

  // An AbstractCompiler for doing parsing.
  private AbstractCompiler compiler;
  private ModulePath modulePath;

  // TODO(tjgq): Whether a CompilerInput is an externs file is determined by the `isExtern`
  // constructor argument and the `setIsExtern` method. Both are necessary because, while externs
  // files passed under the --externs flag are not required to contain an @externs annotation,
  // externs files passed under any other flag must have one, and the presence of the latter can
  // only be known once the file has been parsed. To add to the confusion, note that CompilerInput
  // doesn't actually store the extern bit itself, but instead mutates the SourceFile associated
  // with the AST node. Once (when?) we enforce that extern files always contain an @externs
  // annotation, we can store the extern bit in the AST node, and make SourceFile immutable.

  public CompilerInput(SourceFile file, InputId inputId) {
    this(file, inputId, false);
  }

  /**
   * @deprecated the inputId is read from the SourceFile. Use CompilerInput(file, isExtern)
   */
  @Deprecated
  public CompilerInput(SourceFile sourceFile, String inputId, boolean isExtern) {
    this(sourceFile, new InputId(inputId), isExtern);
  }

  /**
   * @deprecated the inputId is read from the SourceFile. Use CompilerInput(file, isExtern)
   */
  @Deprecated
  public CompilerInput(SourceFile sourceFile, InputId inputId, boolean isExtern) {
    this.sourceFile = checkNotNull(sourceFile);
    this.id = checkNotNull(inputId);
    if (isExtern) {
      setIsExtern();
    }
  }

  public CompilerInput(SourceFile file) {
    this(file, false);
  }

  public CompilerInput(SourceFile file, boolean isExtern) {
    this(file, new InputId(file.getName()), isExtern);
  }

  /** Returns a name for this input. Must be unique across all inputs. */
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

  public Node getAstRoot(AbstractCompiler compiler) {
    Node root = checkNotNull(ast.getAstRoot(compiler));
    checkState(root.isScript());
    checkNotNull(root.getInputId());
    return root;
  }

  public void clearAst() {
    ast.clearAst();
  }

  void initShadowAst(Node shadowScript) {
    checkArgument(shadowScript.isScript(), shadowScript);
    checkState(shadowScript.getIsInClosureUnawareSubtree(), shadowScript);
    this.ast.root = shadowScript;
  }

  public SourceFile getSourceFile() {
    return sourceFile;
  }

  /** Sets an abstract compiler for doing parsing. */
  public void setCompiler(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  /** Gets a list of types depended on by this input. */
  @Override
  public ImmutableList<Require> getRequires() {
    if (hasFullParseDependencyInfo) {
      return ImmutableList.copyOf(orderedRequires);
    }

    return getDependencyInfo().getRequires();
  }

  @Override
  public ImmutableList<String> getTypeRequires() {
    return getDependencyInfo().getTypeRequires();
  }

  /**
   * Gets a list of namespaces and paths depended on by this input, but does not attempt to
   * regenerate the dependency information. Typically this occurs from module rewriting.
   */
  ImmutableCollection<Require> getKnownRequires() {
    return concat(
        dependencyInfo != null ? dependencyInfo.getRequires() : ImmutableList.of(), extraRequires);
  }

  ImmutableList<String> getKnownRequiredSymbols() {
    return Require.asSymbolList(getKnownRequires());
  }

  /** Gets a list of types provided by this input. */
  @Override
  public ImmutableList<String> getProvides() {
    return getDependencyInfo().getProvides();
  }

  @Override
  public boolean getHasExternsAnnotation() {
    return getDependencyInfo().getHasExternsAnnotation();
  }

  @Override
  public boolean getHasNoCompileAnnotation() {
    return getDependencyInfo().getHasNoCompileAnnotation();
  }

  /**
   * Gets a list of types provided, but does not attempt to regenerate the dependency information.
   * Typically this occurs from module rewriting.
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
  public boolean addOrderedRequire(Require require) {
    if (!orderedRequires.contains(require)) {
      orderedRequires.add(require);
      return true;
    }
    return false;
  }

  /**
   * Returns the types that this input dynamically depends on in the order seen in the file. The
   * returned types were loaded dynamically so while they are part of the dependency graph, they do
   * not need sorted before this input.
   */
  public ImmutableList<String> getDynamicRequires() {
    return ImmutableList.copyOf(dynamicRequires);
  }

  /**
   * Registers a type that this input depends on in the order seen in the file. The type was loaded
   * dynamically so while it is part of the dependency graph, it does not need sorted before this
   * input.
   */
  public boolean addDynamicRequire(String require) {
    if (!dynamicRequires.contains(require)) {
      dynamicRequires.add(require);
      return true;
    }
    return false;
  }

  /**
   * Returns the types that this input dynamically imports by goog.requireDynamic() in the order
   * seen in the file. The returned types were loaded dynamically so while they are part of the
   * dependency graph, they do not need sorted before this input.
   */
  public ImmutableList<String> getRequireDynamicImports() {
    return ImmutableList.copyOf(requireDynamicImports);
  }

  /**
   * Registers a type that this input depends on in the order seen in the file. The type was loaded
   * by goog.requireDynamic() so while it is part of the dependency graph, it does not need sorted
   * before this input.
   */
  public void addRequireDynamicImports(String require) {
    if (!requireDynamicImports.contains(require)) {
      requireDynamicImports.add(require);
    }
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
  public void addRequire(Require require) {
    extraRequires.add(require);
  }

  /** Returns the DependencyInfo object, generating it lazily if necessary. */
  DependencyInfo getDependencyInfo() {
    if (dependencyInfo == null) {
      dependencyInfo = generateDependencyInfo();
    }
    if (!extraRequires.isEmpty() || !extraProvides.isEmpty()) {
      dependencyInfo =
          SimpleDependencyInfo.builder(getName(), getName())
              .setProvides(concat(dependencyInfo.getProvides(), extraProvides))
              .setRequires(concat(dependencyInfo.getRequires(), extraRequires))
              .setTypeRequires(dependencyInfo.getTypeRequires())
              .setLoadFlags(dependencyInfo.getLoadFlags())
              .setHasExternsAnnotation(dependencyInfo.getHasExternsAnnotation())
              .setHasNoCompileAnnotation(dependencyInfo.getHasNoCompileAnnotation())
              .build();
      extraRequires.clear();
      extraProvides.clear();
    }
    return dependencyInfo;
  }

  /**
   * Generates the DependencyInfo by scanning and/or parsing the file. This is called lazily by
   * getDependencyInfo, and does not take into account any extra requires/provides added by {@link
   * #addRequire} or {@link #addProvide}.
   */
  private DependencyInfo generateDependencyInfo() {
    Preconditions.checkNotNull(compiler, "Expected setCompiler to be called first: %s", this);
    Preconditions.checkNotNull(
        compiler.getErrorManager(), "Expected compiler to call an error manager: %s", this);

    // If the code is a JsAst, then it was originally JS code, and is compatible with the
    // regex-based parsing of JsFileRegexParser.
    if (JsFileRegexParser.isSupported() && compiler.preferRegexParser()) {
      // Look at the source code.
      // Note: it's OK to use getName() instead of
      // getPathRelativeToClosureBase() here because we're not using
      // this to generate deps files. (We're only using it for
      // symbol dependencies.)
      try {
        DependencyInfo info =
            new JsFileRegexParser(compiler.getErrorManager())
                .setModuleLoader(compiler.getModuleLoader())
                .setIncludeGoogBase(true)
                .parseFile(getName(), getName(), getCode());
        return new LazyParsedDependencyInfo(info, this, compiler);
      } catch (IOException e) {
        compiler
            .getErrorManager()
            .report(
                CheckLevel.ERROR,
                JSError.make(AbstractCompiler.READ_ERROR, getName(), e.getMessage()));
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
      JSDocInfo info = root.getJSDocInfo();

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
          .setTypeRequires(finder.typeRequires)
          .setLoadFlags(finder.loadFlags)
          .setHasExternsAnnotation(info != null && info.isExterns())
          .setHasNoCompileAnnotation(info != null && info.isNoCompile())
          .build();
    }
  }

  private static class DepsFinder {
    private final Map<String, String> loadFlags = new TreeMap<>();
    private final List<String> provides = new ArrayList<>();
    private final List<Require> requires = new ArrayList<>();
    private final List<String> typeRequires = new ArrayList<>();
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

    void visitSubtree(Node n, @Nullable Node parent) {
      switch (n.getToken()) {
        case CALL -> {
          if (n.hasTwoChildren()
              && n.getFirstChild().isGetProp()
              && n.getFirstFirstChild().matchesName("goog")) {

            if (!requires.contains(Require.BASE)) {
              requires.add(Require.BASE);
            }

            Node callee = n.getFirstChild();
            Node argument = n.getLastChild();
            switch (callee.getString()) {
              case "module":
                // only mark as a goog.module if this is not bundled, e.g.
                // goog.loadModule(function(exports) {"use strict";goog.module('Foo');
                if (parent.isExprResult() && parent.getParent().isModuleBody()) {
                  loadFlags.put("module", "goog");
                }
              // Fall-through
              case "provide":
                if (!argument.isStringLit()) {
                  return;
                }
                provides.add(argument.getString());
                return;

              case "require":
                if (!argument.isStringLit()) {
                  return;
                }
                requires.add(Require.googRequireSymbol(argument.getString()));
                return;

              case "requireType":
                if (!argument.isStringLit()) {
                  return;
                }
                typeRequires.add(argument.getString());
                return;

              case "loadModule":
                // Process the block of the loadModule argument if it's a function, as opposed to
                // a string.
                if (argument.isStringLit()) {
                  throw new IllegalArgumentException(
                      "Unsupported parse of goog.loadModule with string literal");
                }
                n = argument.getLastChild();
                break;

              case "declareModuleId":
                if (!argument.isStringLit()) {
                  return;
                }
                provides.add(argument.getString());
                break;

              default:
                return;
            }
          }
        }
        case MODULE_BODY -> {
          if (!parent.getBooleanProp(Node.GOOG_MODULE)) {
            provides.add(modulePath.toModuleName());
            loadFlags.put("module", "es6");
          }
        }
        case IMPORT -> {
          visitEs6ModuleName(n.getLastChild(), n);
          return;
        }
        case EXPORT -> {
          if (NodeUtil.isExportFrom(n)) {
            visitEs6ModuleName(n.getLastChild(), n);
          }
          return;
        }
        case EXPR_RESULT, CONST, LET, VAR, BLOCK, NAME, DESTRUCTURING_LHS -> {}
        case SCRIPT -> {
          JSDocInfo jsdoc = n.getJSDocInfo();
          if (jsdoc != null && jsdoc.isProvideGoog()) {
            provides.add("goog");
          }
        }
        default -> {
          return;
        }
      }

      for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        visitSubtree(child, n);
      }
    }

    void visitEs6ModuleName(Node n, Node parent) {
      checkArgument(n.isStringLit());
      checkArgument(parent.isExport() || parent.isImport());

      // TODO(blickly): Move this (and the duplicated logic in JsFileRegexParser/Es6RewriteModules)
      // into ModuleLoader.
      String moduleName = n.getString();
      if (moduleName.startsWith("goog:")) {
        // cut off the "goog:" prefix
        requires.add(Require.googRequireSymbol(moduleName.substring(5)));
        return;
      }
      ModulePath importedModule =
          modulePath.resolveJsModule(
              moduleName, modulePath.toString(), n.getLineno(), n.getCharno());

      if (importedModule == null) {
        importedModule = modulePath.resolveModuleAsPath(moduleName);
      }

      requires.add(Require.es6Import(importedModule.toModuleName(), n.getString()));
    }
  }

  public String getCode() throws IOException {
    return getSourceFile().getCode();
  }

  /** Returns the chunk to which the input belongs. */
  public JSChunk getChunk() {
    return chunk;
  }

  /** Sets the chunk to which the input belongs. */
  public void setChunk(JSChunk chunk) {
    // An input may only belong to one chunk.
    checkArgument(chunk == null || this.chunk == null || this.chunk == chunk);
    this.chunk = chunk;
  }

  /** Overrides the chunk to which the input belongs. */
  void overrideChunk(JSChunk chunk) {
    this.chunk = chunk;
  }

  public boolean isExtern() {
    return sourceFile.isExtern();
  }

  void setIsExtern() {
    sourceFile.setKind(SourceKind.EXTERN);
  }

  public int getLineOffset(int lineno) {
    return sourceFile.getLineOffset(lineno);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean isEs6Module() {
    // Instead of doing a full parse to read all load flags, just ask the delegate, which at least
    // has this much info
    return getDependencyInfo().isEs6Module();
  }

  @Override
  public boolean isGoogModule() {
    // Instead of doing a full parse to read all load flags, just ask the delegate, which at least
    // has this much info
    return getDependencyInfo().isGoogModule();
  }

  @Override
  public ImmutableMap<String, String> getLoadFlags() {
    return getDependencyInfo().getLoadFlags();
  }

  private static <T> ImmutableSet<T> concat(Iterable<T> first, Iterable<T> second) {
    return ImmutableSet.<T>builder().addAll(first).addAll(second).build();
  }

  public ModulePath getPath() {
    if (modulePath == null) {
      ModuleLoader moduleLoader = compiler.getModuleLoader();
      this.modulePath = moduleLoader.resolve(getName());
    }
    return modulePath;
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

  public FeatureSet getFeatures(AbstractCompiler compiler) {
    var unused = this.ast.getAstRoot(compiler); // parse if required
    return this.ast.features;
  }

  /**
   * Wraps the Rhino AST for this CompilerInput.
   *
   * <p>Use this wrapper class instead of directly storing a Node because the AST is lazily
   * constructed. CompilerInputs are created before the parsing step, and in some cases the AST for
   * a given CompilerInput may never be parsed if dependency pruning is enabled.
   *
   * <p>Additionally if using precompiled libraries, this class handles deserializing the AST from
   * the TypedAST format, instead of parsing it from the source.
   */
  private final class JsAst {
    private @Nullable Node root;
    private FeatureSet features;

    public Node getAstRoot(AbstractCompiler compiler) {
      if (this.isParsed()) {
        return this.root;
      }

      Supplier<Node> astRootSource = compiler.getTypedAstDeserializer(sourceFile);
      if (astRootSource != null) {
        this.root = astRootSource.get();
        this.features = (FeatureSet) this.root.getProp(Node.FEATURE_SET);
      } else {
        this.parse(compiler);
      }
      checkState(identical(this.root.getStaticSourceFile(), sourceFile));
      this.root.setInputId(id);
      // Clear the cached source after parsing.  It will be re-read for snippet generation if
      // needed.
      sourceFile.clearCachedSource();
      return this.root;
    }

    public void clearAst() {
      root = null;
      // While we're at it, clear out any saved text in the source file on
      // the assumption that if we're dumping the parse tree, then we probably
      // assume regenerating everything else is a smart idea also.
      sourceFile.clearCachedSource();
    }

    private boolean isParsed() {
      return root != null;
    }

    private void parse(AbstractCompiler compiler) {
      try {
        ParserRunner.ParseResult result =
            ParserRunner.parse(
                sourceFile,
                sourceFile.getCode(),
                compiler.getParserConfig(
                    sourceFile.isExtern()
                        ? AbstractCompiler.ConfigContext.EXTERNS
                        : AbstractCompiler.ConfigContext.DEFAULT),
                compiler.getDefaultErrorReporter());
        root = result.ast;
        features = result.features;

        if (compiler.getOptions().preservesDetailedSourceInfo()) {
          compiler.addComments(sourceFile.getName(), result.comments);
        }
        if (result.sourceMapURL != null && compiler.getOptions().getResolveSourceMapAnnotations()) {
          boolean parseInline = compiler.getOptions().getParseInlineSourceMaps();
          SourceFile sourceMapSourceFile =
              SourceMapResolver.extractSourceMap(sourceFile, result.sourceMapURL, parseInline);
          if (sourceMapSourceFile != null) {
            compiler.addInputSourceMap(
                sourceFile.getName(), new SourceMapInput(sourceMapSourceFile));
          }
        }
      } catch (IOException e) {
        compiler.report(
            JSError.make(AbstractCompiler.READ_ERROR, sourceFile.getName(), e.getMessage()));
      }

      if (root == null) {
        root = IR.script();
      }

      // Set the source name so that the compiler passes can track
      // the source file and module.
      root.setStaticSourceFile(sourceFile);
    }
  }
}
