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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.TypeMatchingStrategy;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that drives the RefasterJs refactoring by matching against a provided
 * template JS file and then applying a transformation based off the template
 * JS.
 *
 * @author mknichel@google.com (Mark Knichel)
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

  /** All templates that were found in the template file. */
  private ImmutableList<RefasterJsTemplate> templates;

  /** The RefasterJsTemplate that matched the last Match. */
  private RefasterJsTemplate matchedTemplate;

  public RefasterJsScanner() {
    this.templateJs = null;
  }

  /**
   * Loads the RefasterJs template. This must be called before the scanner is used.
   */
  public void loadRefasterJsTemplate(String refasterjsTemplate) throws IOException  {
    Preconditions.checkState(
        templateJs == null, "Can't load RefasterJs template since a template is already loaded.");
    this.templateJs =
        Thread.currentThread().getContextClassLoader().getResource(refasterjsTemplate) != null
        ? Resources.toString(Resources.getResource(refasterjsTemplate), UTF_8)
        : Files.toString(new File(refasterjsTemplate), UTF_8);
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
    Preconditions.checkState(
        templateJs == null, "Can't load RefasterJs template since a template is already loaded.");
    this.templateJs = refasterJsTemplate;
  }

  @Override public boolean matches(Node node, NodeMetadata metadata) {
    if (templates == null) {
      try {
        initialize(metadata.getCompiler());
      } catch (Exception e) {
        Throwables.propagate(e);
      }
    }
    matchedTemplate = null;
    for (RefasterJsTemplate template : templates) {
      if (template.matcher.matches(node, metadata)) {
        matchedTemplate = template;
        return true;
      }
    }
    return false;
  }

  @Override public List<SuggestedFix> processMatch(Match match) {
    SuggestedFix.Builder fix = new SuggestedFix.Builder();
    Node newNode = transformNode(
        matchedTemplate.afterTemplate.getLastChild(),
        matchedTemplate.matcher.getTemplateNodeToMatchMap());
    Node nodeToReplace = match.getNode();
    fix.setOriginalMatchedNode(nodeToReplace);
    fix.replace(nodeToReplace, newNode, match.getMetadata().getCompiler());
    // If the template is a multiline template, make sure to delete the same number of sibling nodes
    // as the template has.
    Node n = match.getNode().getNext();
    for (int i = 1; i < matchedTemplate.beforeTemplate.getLastChild().getChildCount(); i++) {
      Preconditions.checkNotNull(
          n, "Found mismatched sibling count between before template and matched node.\n"
          + "Template: %s\nMatch: %s",
          matchedTemplate.beforeTemplate.getLastChild(), match.getNode());
      fix.delete(n);
      n = n.getNext();
    }

    // Add/remove any goog.requires
    for (String require : matchedTemplate.getGoogRequiresToAdd()) {
      fix.addGoogRequire(match, require);
    }
    for (String require : matchedTemplate.getGoogRequiresToRemove()) {
      fix.removeGoogRequire(match, require);
    }
    return ImmutableList.of(fix.build());
  }

  /**
   * Transforms the template node to a replacement node by mapping the template names to
   * the ones that were matched against in the JsSourceMatcher.
   */
  private Node transformNode(Node templateNode, Map<String, Node> templateNodeToMatchMap) {
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
    for (Node child : templateNode.children()) {
      clone.addChildToBack(transformNode(child, templateNodeToMatchMap));
    }
    return clone;
  }

  /**
   * Initializes the Scanner class by loading the template JS file, compiling it, and then
   * finding all matching RefasterJs template functions in the file.
   */
  void initialize(AbstractCompiler compiler) throws Exception {
    Preconditions.checkState(
        !Strings.isNullOrEmpty(templateJs),
        "The template JS must be loaded before the scanner is used. "
        + "Make sure that the template file is not empty.");
    Node scriptRoot = new JsAst(SourceFile.fromCode(
        "template", templateJs)).getAstRoot(compiler);

    // The before-templates are kept in a LinkedHashMap, to ensure that they are later iterated
    // over in the order in which they appear in the template JS file.
    Map<String, Node> beforeTemplates = new LinkedHashMap<>();
    Map<String, Node> afterTemplates = new HashMap<>();
    for (Node templateNode : scriptRoot.children()) {
      if (templateNode.isFunction()) {
        String fnName = templateNode.getFirstChild().getQualifiedName();
        if (fnName.startsWith("before_")) {
          String templateName = fnName.substring("before_".length());
          Preconditions.checkState(
              !beforeTemplates.containsKey(templateName),
              "Found existing template with the same name: %s", beforeTemplates.get(templateName));
          Preconditions.checkState(
              templateNode.getLastChild().hasChildren(),
              "Before templates are not allowed to be empty!");
          beforeTemplates.put(templateName, templateNode);
        } else if (fnName.startsWith("after_")) {
          String templateName = fnName.substring("after_".length());
          Preconditions.checkState(
              !afterTemplates.containsKey(templateName),
              "Found existing template with the same name: %s", afterTemplates.get(templateName));
          afterTemplates.put(templateName, templateNode);
        }
      }
    }

    Preconditions.checkState(
        !beforeTemplates.isEmpty(),
        "Did not find any RefasterJs templates! Make sure that there are 2 functions defined "
        + "with the same name, one with a \"before_\" prefix and one with a \"after_\" prefix");

    ImmutableList.Builder<RefasterJsTemplate> builder = ImmutableList.builder();
    for (String templateName : beforeTemplates.keySet()) {
      Preconditions.checkState(
          afterTemplates.containsKey(templateName),
          "Found before template without a corresponding after template. Make sure there is an "
          + "after_%s function defined.", templateName);
      builder.add(
          new RefasterJsTemplate(
              compiler,
              typeMatchingStrategy,
              beforeTemplates.get(templateName),
              afterTemplates.get(templateName)));
    }
    this.templates = builder.build();
  }

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

    private List<String> getGoogRequiresFromPattern(Pattern pattern) {
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
