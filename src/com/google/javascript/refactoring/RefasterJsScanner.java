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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.TypeMatchingStrategy;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Class that drives the RefasterJs refactoring by matching against a provided
 * template JS file and then applying a transformation based off the template
 * JS.
 */
public final class RefasterJsScanner extends Scanner {
  /** The JS code that contains the RefasterJs templates. */
  private String templateJs;

  /**
   * The type matching strategy to use when matching templates.
   *
   * <p>Defaults to {@link TypeMatchingStrategy#SUBTYPES}.
   */
  private TypeMatchingStrategy typeMatchingStrategy = TypeMatchingStrategy.SUBTYPES;

  /**
   * Each 'before' template has multiple RefasterJsTemplate instances that correspond to the
   * multiple alternative fixes.
   */
  private LinkedHashMap<JsSourceMatcher, ImmutableList<RefasterJsTemplate>> templates;

  /** The RefasterJsTemplates that matched the last match. */
  private ImmutableList<RefasterJsTemplate> matchedTemplates;

  public RefasterJsScanner() {
    this.templateJs = null;
  }

  /**
   * Loads the RefasterJs template. This must be called before the scanner is used.
   */
  public void loadRefasterJsTemplate(String refasterjsTemplate) throws IOException  {
    checkState(
        templateJs == null, "Can't load RefasterJs template since a template is already loaded.");
    this.templateJs =
        Thread.currentThread().getContextClassLoader().getResource(refasterjsTemplate) != null
            ? Resources.toString(Resources.getResource(refasterjsTemplate), UTF_8)
            : Files.asCharSource(new File(refasterjsTemplate), UTF_8).read();
  }

  /**
   * Sets the type matching strategy to use when matching templates.
   *
   * <p>Defaults to {@link TypeMatchingStrategy#SUBTYPES}.
   */
  public void setTypeMatchingStrategy(TypeMatchingStrategy typeMatchingStrategy) {
    this.typeMatchingStrategy = typeMatchingStrategy;
  }

  /**
   * Loads the RefasterJs template. This must be called before the scanner is used.
   */
  public void loadRefasterJsTemplateFromCode(String refasterJsTemplate) throws IOException  {
    checkState(
        templateJs == null, "Can't load RefasterJs template since a template is already loaded.");
    this.templateJs = refasterJsTemplate;
  }

  /**
   * Clears the RefasterJs templates used for comparison. This function should be called if this
   * class is going to be used with multiple Compiler objects since type comparison is dependent
   * on the compiler used to generate the types.
   */
  public void clearTemplates() {
    templates = null;
    matchedTemplates = null;
  }

  @Override public boolean matches(Node node, NodeMetadata metadata) {
    if (templates == null) {
      try {
        initialize(metadata.getCompiler());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    matchedTemplates = null;
    for (Map.Entry<JsSourceMatcher, ImmutableList<RefasterJsTemplate>> e : templates.entrySet()) {
      if (e.getKey().matches(node, metadata)) {
        this.matchedTemplates = e.getValue();
        return true;
      }
    }
    return false;
  }

  @Override
  public ImmutableList<SuggestedFix> processMatch(Match match) {
    SuggestedFix.Builder defaultFix = null;
    for (RefasterJsTemplate matchedTemplate : matchedTemplates) {
      SuggestedFix.Builder fix = new SuggestedFix.Builder();
      // Only replace the original source with a version serialized from the AST if the after
      // template
      // is actually different. Otherwise, we might just add churn (e.g. single quotes into double
      // quotes and whitespace).
      if (matchedTemplate
          .beforeTemplate
          .getLastChild()
          .isEquivalentTo(matchedTemplate.afterTemplate.getLastChild())) {
        return ImmutableList.of();
      }

      Node script = NodeUtil.getEnclosingScript(match.getNode());
      ScriptMetadata scriptMetadata = ScriptMetadata.create(script, match.getMetadata());

      for (String require : matchedTemplate.getGoogRequiresToAdd()) {
        if (scriptMetadata.getAlias(require) == null) {
          fix.addGoogRequire(match, require, scriptMetadata);
        }
      }

      // Re-match to compute getTemplateNodeToMatchMap.
      Preconditions.checkState(
          matchedTemplate.matcher.matches(match.getNode(), match.getMetadata()),
          "Matcher for %s did not match a second time",
          matchedTemplate.beforeTemplate);

      Node newNode =
          transformNode(
              matchedTemplate.afterTemplate.getLastChild(),
              matchedTemplate.matcher.getTemplateNodeToMatchMap(),
              scriptMetadata);
      Node nodeToReplace = match.getNode();
      fix.attachMatchedNodeInfo(nodeToReplace, match.getMetadata().getCompiler());
      fix.replace(nodeToReplace, newNode, match.getMetadata().getCompiler());
      // If the template is a multiline tcs emplate, make sure to delete the same number of sibling
      // nodes as the template has.
      Node n = match.getNode().getNext();
      int count = matchedTemplate.beforeTemplate.getLastChild().getChildCount();
      for (int i = 1; i < count; i++) {
        Preconditions.checkNotNull(
            n,
            "Found mismatched sibling count between before template and matched node.\n"
                + "Template: %s to %s\nMatch: %s",
            matchedTemplate.beforeTemplate.getLastChild(),
            matchedTemplate.afterTemplate.getLastChild(),
            match.getNode());
        fix.delete(n);
        n = n.getNext();
      }

      for (String require : matchedTemplate.getGoogRequiresToRemove()) {
        fix.removeGoogRequire(match, require);
      }

      if (defaultFix == null) {
        defaultFix = fix;
      } else {
        defaultFix.addAlternative(fix.build());
      }
    }
    return ImmutableList.of(defaultFix.build());
  }

  /**
   * Transforms the template node to a replacement node by mapping the template names to the ones
   * that were matched against in the JsSourceMatcher.
   */
  private Node transformNode(
      Node templateNode, Map<String, Node> templateNodeToMatchMap, ScriptMetadata scriptMetadata) {
    Node clone = templateNode.cloneNode();
    if (templateNode.isName()) {
      String name = templateNode.getString();
      if (templateNodeToMatchMap.containsKey(name)) {
        Node templateMatch = templateNodeToMatchMap.get(name);
        Preconditions.checkNotNull(templateMatch, "Match for %s is null", name);
        if (templateNode.getParent().isVar()) {
          // Var declarations should only copy the variable name from the saved match, but the rest
          // of the subtree should come from the template node.
          clone.setString(templateMatch.getString());
        } else {
          return templateMatch.cloneTree();
        }
      }
    } else if (templateNode.isCall()
        && templateNode.getBooleanProp(Node.FREE_CALL)
        && templateNode.getFirstChild().isName()) {
      String name = templateNode.getFirstChild().getString();
      if (templateNodeToMatchMap.containsKey(name)) {
        // If this function call matches a template parameter, don't treat it as a free call.
        // This mirrors the behavior in the TemplateAstMatcher as well as ensures the code
        // generator doesn't generate code like "(0,fn)()".
        clone.putBooleanProp(Node.FREE_CALL, false);
      }
    }
    if (templateNode.isQualifiedName()) {
      String name = templateNode.getQualifiedName();
      String alias = scriptMetadata.getAlias(name);
      if (alias != null && !name.equals(alias)) {
        return IR.name(alias);
      }
    }
    for (Node child : templateNode.children()) {
      clone.addChildToBack(transformNode(child, templateNodeToMatchMap, scriptMetadata));
    }
    return clone;
  }

  /**
   * Initializes the Scanner class by loading the template JS file, compiling it, and then
   * finding all matching RefasterJs template functions in the file.
   */
  void initialize(AbstractCompiler compiler) throws Exception {
    checkState(
        !Strings.isNullOrEmpty(templateJs),
        "The template JS must be loaded before the scanner is used. "
            + "Make sure that the template file is not empty.");
    Node scriptRoot = new JsAst(SourceFile.fromCode(
        "template", templateJs)).getAstRoot(compiler);

    // The before-templates are kept in a LinkedHashMap, to ensure that they are later iterated
    // over in the order in which they appear in the template JS file.
    LinkedHashMap<String, Node> beforeTemplates = new LinkedHashMap<>();
    Map<String, SortedMap<Integer, Node>> afterTemplates = new HashMap<>();
    Set<String> hasChoices = new HashSet<>();
    for (Node templateNode : scriptRoot.children()) {
      if (templateNode.isFunction()) {
        String fnName = templateNode.getFirstChild().getQualifiedName();
        if (fnName.startsWith("before_")) {
          String templateName = fnName.substring("before_".length());
          Preconditions.checkState(
              !beforeTemplates.containsKey(templateName),
              "Found existing template with the same name: %s", beforeTemplates.get(templateName));
          checkState(
              templateNode.getLastChild().hasChildren(),
              "Before templates are not allowed to be empty!");
          beforeTemplates.put(templateName, templateNode);
        } else if (fnName.startsWith("after_option_")) {
          Matcher m = AFTER_CHOICE_PATTERN.matcher(fnName);
          checkState(m.matches(), "Template name %s must match pattern after_option_\\d*_", fnName);
          int optionNumber = Integer.parseInt(m.group(1));
          String templateName = m.group(2);
          if (!afterTemplates.containsKey(templateName)) {
            afterTemplates.put(templateName, new TreeMap<>());
            hasChoices.add(templateName);
          }
          checkState(
              hasChoices.contains(templateName),
              "Template %s can only be mixed with other after_option_ templates");
          checkState(
              !afterTemplates.get(templateName).containsKey(optionNumber),
              "Found duplicate template for %s, assign unique indexes for options",
              fnName);
          afterTemplates.get(templateName).put(optionNumber, templateNode);
        } else if (fnName.startsWith("after_")) {
          String templateName = fnName.substring("after_".length());
          Preconditions.checkState(
              !afterTemplates.containsKey(templateName),
              "Found existing template with the same name: %s", afterTemplates.get(templateName));
          afterTemplates.put(templateName, ImmutableSortedMap.of(0, templateNode));
        } else if (fnName.startsWith("do_not_change_")) {
          String templateName = fnName.substring("do_not_change_".length());
          Preconditions.checkState(
              !beforeTemplates.containsKey(templateName),
              "Found existing template with the same name: %s",
              beforeTemplates.get(templateName));
          Preconditions.checkState(
              !afterTemplates.containsKey(templateName),
              "Found existing template with the same name: %s",
              afterTemplates.get(templateName));
          beforeTemplates.put(templateName, templateNode);
          afterTemplates.put(templateName, ImmutableSortedMap.of(0, templateNode));
        }
      }
    }

    checkState(
        !beforeTemplates.isEmpty(),
        "Did not find any RefasterJs templates! Make sure that there are 2 functions defined "
            + "with the same name, one with a \"before_\" prefix and one with a \"after_\" prefix");

    // TODO(bangert): Get ImmutableLinkedMap into Guava?
    this.templates = new LinkedHashMap<>();
    for (String templateName : beforeTemplates.keySet()) {
      Preconditions.checkState(
          afterTemplates.containsKey(templateName) && !afterTemplates.get(templateName).isEmpty(),
          "Found before template without at least one corresponding after "
              + " template. Make sure there is an after_%s or after_option_1_%s function defined.",
          templateName,
          templateName);
      ImmutableList.Builder<RefasterJsTemplate> builder = ImmutableList.builder();
      for (Node afterTemplateOption : afterTemplates.get(templateName).values()) {
        builder.add(
            new RefasterJsTemplate(
                compiler,
                typeMatchingStrategy,
                beforeTemplates.get(templateName),
                afterTemplateOption));
      }
      ImmutableList<RefasterJsTemplate> afterOptions = builder.build();
      this.templates.put(afterOptions.get(0).matcher, afterOptions);
    }
  }

  private static final Pattern AFTER_CHOICE_PATTERN = Pattern.compile("^after_option_(\\d*)_(.*)");

  /** Class that holds the before and after templates for a given RefasterJs refactoring. */
  private static class RefasterJsTemplate {
    private static final Pattern ADD_GOOG_REQUIRE_PATTERN =
        Pattern.compile("\\+require\\s+\\{([^}]+)\\}");
    private static final Pattern REMOVE_GOOG_REQUIRE_PATTERN =
        Pattern.compile("-require\\s+\\{([^}]+)\\}");

    final JsSourceMatcher matcher;
    final Node beforeTemplate;
    final Node afterTemplate;

    RefasterJsTemplate(
        AbstractCompiler compiler,
        TypeMatchingStrategy typeMatchingStrategy,
        Node beforeTemplate,
        Node afterTemplate) {
      this.matcher = new JsSourceMatcher(compiler, beforeTemplate, typeMatchingStrategy);
      this.beforeTemplate = beforeTemplate;
      this.afterTemplate = afterTemplate;
    }

    List<String> getGoogRequiresToAdd() {
      return getGoogRequiresFromPattern(ADD_GOOG_REQUIRE_PATTERN);
    }

    List<String> getGoogRequiresToRemove() {
      return getGoogRequiresFromPattern(REMOVE_GOOG_REQUIRE_PATTERN);
    }

    private ImmutableList<String> getGoogRequiresFromPattern(Pattern pattern) {
      return Stream.concat(
              getGoogRequiresFromNode(pattern, beforeTemplate).stream(),
              getGoogRequiresFromNode(pattern, afterTemplate).stream())
          .distinct()
          .collect(ImmutableList.toImmutableList());
    }

    private static ImmutableList<String> getGoogRequiresFromNode(
        Pattern pattern, Node beforeTemplate) {
      JSDocInfo jsDoc = NodeUtil.getBestJSDocInfo(beforeTemplate);
      if (jsDoc == null) {
        return ImmutableList.of();
      }
      String jsDocContent = jsDoc.getOriginalCommentString();
      if (jsDocContent == null) {
        return ImmutableList.of();
      }
      ImmutableList.Builder<String> requires = ImmutableList.builder();
      Matcher m = pattern.matcher(jsDocContent);
      while (m.find()) {
        requires.add(m.group(1));
      }
      return requires.build();
    }
  }
}
