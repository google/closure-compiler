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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DESCRIPTION_EQUALITY;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DIAGNOSTIC_EQUALITY;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Correspondence;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.jscomp.AstValidator.TypeInfoValidation;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;

public abstract class TypeCheckTestCase extends CompilerTypeTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // Enable missing override checks that are disabled by default.
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MISSING_OVERRIDE, CheckLevel.WARNING);
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.WARNING);
  }

  protected static ObjectType getInstanceType(Node js1Node) {
    JSType type = js1Node.getFirstChild().getJSType();
    assertThat(type).isNotNull();
    assertThat(type).isInstanceOf(FunctionType.class);
    FunctionType functionType = (FunctionType) type;
    assertThat(functionType.isConstructor()).isTrue();
    return functionType.getInstanceType();
  }

  protected void checkObjectType(ObjectType objectType, String propertyName, JSType expectedType) {
    assertWithMessage(
            "Expected %s to have property %s", objectType.getReferenceName(), propertyName)
        .that(objectType.hasProperty(propertyName))
        .isTrue();
    assertTypeEquals(
        "Expected "
            + objectType.getReferenceName()
            + "'s property "
            + propertyName
            + " to have type "
            + expectedType,
        expectedType,
        objectType.getPropertyType(propertyName));
  }

  /**
   * Creates a new {@link TypeTestBuilder} based on the instance state in this class.
   *
   * @deprecated prefer to statically import {@link TypeTestBuilder.newTest}, to make tests more
   *     hermetic.
   */
  @Deprecated
  final TypeTestBuilder newTestLegacy() {
    return new TypeTestBuilder(compiler);
  }

  @CheckReturnValue
  public static final class TypeTestBuilder {
    private final List<SourceFile> sources = new ArrayList<>();
    private final List<String> externs = new ArrayList<>();
    private boolean includeDefaultExterns = false;
    private final ArrayList<DiagnosticType> diagnosticTypes = new ArrayList<>();
    private final ArrayList<String> diagnosticDescriptions = new ArrayList<>();
    private boolean diagnosticsAreErrors = false;
    private boolean reportUnknownTypes = false;
    private final ArrayList<DiagnosticGroup> suppress = new ArrayList<>();
    private boolean hasRun = false;
    private final Compiler compiler;

    static TypeTestBuilder newTest() {
      Compiler compiler = new Compiler();
      CompilerOptions options = defaultOptions();
      // Enable missing override checks that are disabled by default.
      options.setWarningLevel(DiagnosticGroups.MISSING_OVERRIDE, CheckLevel.WARNING);
      options.setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.WARNING);
      options.setWarningLevel(DiagnosticGroups.STRICT_PRIMITIVE_OPERATORS, CheckLevel.WARNING);
      compiler.initOptions(options);

      compiler.markFeatureNotAllowed(Feature.MODULES);
      return new TypeTestBuilder(compiler);
    }

    private TypeTestBuilder(Compiler compiler) {
      this.compiler = compiler;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder addSource(String code) {
      return addSource("testcode" + sources.size(), code);
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder addSource(String filename, String code) {
      this.sources.add(SourceFile.fromCode(filename, code));
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder addExterns(String externs) {
      this.externs.add(externs);
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder includeDefaultExterns() {
      this.includeDefaultExterns = true;
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder diagnosticsAreErrors() {
      this.diagnosticsAreErrors = true;
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder enableReportUnknownTypes() {
      this.reportUnknownTypes = true;
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder suppress(DiagnosticGroup group) {
      this.suppress.add(group);
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder addDiagnostic(DiagnosticType x) {
      this.diagnosticTypes.add(x);
      return this;
    }

    @CanIgnoreReturnValue
    public TypeTestBuilder addDiagnostic(String x) {
      this.diagnosticDescriptions.add(x.trim());
      return this;
    }

    public void run() {
      checkState(!this.sources.isEmpty(), "Must provide a source");
      checkState(!this.hasRun, "Cannot run the same test twice");
      this.hasRun = true;
      if (this.includeDefaultExterns) {
        this.externs.add(0, DEFAULT_EXTERNS);
      }

      String allExterns = String.join("\n", this.externs);

      final List<Object> diagnostics;
      final Correspondence<JSError, Object> correspondence;
      if (!this.diagnosticTypes.isEmpty()) {
        diagnostics = castAny(this.diagnosticTypes);
        correspondence = castAny(DIAGNOSTIC_EQUALITY);
      } else {
        diagnostics = castAny(this.diagnosticDescriptions);
        correspondence = castAny(DESCRIPTION_EQUALITY);
      }
      for (DiagnosticGroup suppress : this.suppress) {
        compiler.getOptions().setWarningLevel(suppress, CheckLevel.OFF);
      }

      parseAndTypeCheckWithScope(compiler, allExterns, this.sources, reportUnknownTypes);

      final ImmutableList<JSError> assertedErrors;
      final ImmutableList<JSError> emptyErrors;
      if (this.diagnosticsAreErrors) {
        assertedErrors = compiler.getErrors();
        emptyErrors = compiler.getWarnings();
      } else {
        assertedErrors = compiler.getWarnings();
        emptyErrors = compiler.getErrors();
      }

      assertThat(assertedErrors)
          .comparingElementsUsing(correspondence)
          .containsExactlyElementsIn(diagnostics)
          .inOrder();
      assertThat(emptyErrors).isEmpty();
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> T castAny(Object x) {
    return (T) x;
  }

  /** Parses and type checks the JavaScript code. */
  protected Node parseAndTypeCheck(String js) {
    return parseAndTypeCheck("", js);
  }

  protected Node parseAndTypeCheck(String externs, String js) {
    return parseAndTypeCheckWithScope(externs, js, "").root;
  }

  /**
   * Parses and type checks the JavaScript code and returns the TypedScope used whilst type
   * checking.
   */
  protected TypeCheckResult parseAndTypeCheckWithScope(String js) {
    return parseAndTypeCheckWithScope("", js, "");
  }

  /**
   * Parses and type checks the JavaScript code and returns the TypedScope used whilst type
   * checking.
   */
  protected TypeCheckResult parseAndTypeCheckWithScope(String externs, String js) {
    return parseAndTypeCheckWithScope(externs, js, "");
  }

  @CanIgnoreReturnValue
  protected TypeCheckResult parseAndTypeCheckWithScope(
      String externs, String js, String sourceNameExtension) {
    return parseAndTypeCheckWithScope(
        compiler,
        externs,
        ImmutableList.of(SourceFile.fromCode("[testcode]" + sourceNameExtension, js)),
        false);
  }

  @CanIgnoreReturnValue
  protected static TypeCheckResult parseAndTypeCheckWithScope(
      Compiler compiler, String externs, List<SourceFile> sources, boolean reportUnknownTypes) {
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        sources,
        compiler.getOptions());
    compiler.parse();

    Node jsNode = compiler.getJsRoot();
    Node externsNode = compiler.getExternsRoot();
    new GatherModuleMetadata(compiler, false, ResolutionMode.BROWSER).process(externsNode, jsNode);
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()).process(externsNode, jsNode);
    new InferConsts(compiler).process(externsNode, jsNode);

    assertWithMessage("Regarding errors:").that(compiler.getErrors()).isEmpty();

    TypedScope s =
        makeTypeCheck(compiler, compiler.getTypeRegistry())
            .reportUnknownTypes(reportUnknownTypes)
            .processForTesting(externsNode, jsNode);

    new AstValidator(compiler)
        .setTypeValidationMode(TypeInfoValidation.JSTYPE)
        .process(externsNode, jsNode);

    return new TypeCheckResult(jsNode.getFirstChild(), s);
  }

  /**
   * @param n A valid statement node, SCRIPT, or ROOT node.
   */
  protected Node typeCheck(Node n) {
    Node jsRoot;
    if (n.isRoot()) {
      // This is fine as is.
      jsRoot = n;
    } else if (n.isScript()) {
      jsRoot = IR.root(n);
    } else {
      Node script = IR.script(n);
      jsRoot = IR.root(script);
      script.setInputId(new InputId("test"));
    }
    Node externsNode = IR.root();
    Node externAndJsRoot = IR.root(externsNode);
    externAndJsRoot.addChildToBack(jsRoot);

    makeTypeCheck().processForTesting(externsNode, externAndJsRoot.getSecondChild());
    return n;
  }

  protected TypeCheck makeTypeCheck() {
    return makeTypeCheck(compiler, registry);
  }

  protected static TypeCheck makeTypeCheck(Compiler compiler, JSTypeRegistry registry) {
    return new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry);
  }

  protected String suppressMissingProperty(String... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += "x." + prop + " = 3;";
    }
    return result + "}";
  }

  protected String suppressMissingPropertyFor(String type, String... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += type + ".prototype." + prop + " = 3;";
    }
    return result + "}";
  }

  protected void assertHasXMorePropertiesThanNativeObject(
      ObjectType instanceType, int numExtraProperties) {
    assertThat(instanceType.getPropertiesCount())
        .isEqualTo(getNativeObjectPropertiesCount() + numExtraProperties);
  }

  private int getNativeObjectPropertiesCount() {
    return getNativeObjectType().getPropertiesCount();
  }

  protected static class TypeCheckResult {
    final Node root;
    final TypedScope scope;

    private TypeCheckResult(Node root, TypedScope scope) {
      this.root = root;
      this.scope = scope;
    }
  }
}
