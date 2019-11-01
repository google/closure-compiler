/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.base.Supplier;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * RenameLabels renames all the labels so that they have short names, to reduce
 * code size and also to obfuscate the code.
 *
 * Label names have a unique namespace, so variable or function names clashes
 * are not a concern, but keywords clashes are.
 *
 * Additionally, labels names are only within the statements include in the
 * label and do not cross function boundaries. This means that it is possible to
 * create one label name that is used for labels at any given depth of label
 * nesting. Typically, the name "a" will be used for all top-level labels, "b"
 * for the next nested label, and so on. For example:
 *
 * <code>
 * function bar() {
 *   a: {
 *     b: {
 *       foo();
 *     }
 *   }
 *
 *   a: {
 *     b: break a;
 *   }
 * }
 * </code>
 *
 * The general processes is as follows: process() is the entry point for the
 * CompilerPass, and from there a standard "ScopedCallback" traversal is done,
 * where "shouldTraverse" is called when descending the tree, and the "visit" is
 * called in a depth first manner. The name for the label is selected during the
 * decent in "shouldTraverse", and the references to the label name are renamed
 * as they are encountered during the "visit". This means that if the label is
 * unreferenced, it is known when the label node is visited, and, if so, can be
 * safely removed.
 */
final class RenameLabels implements CompilerPass {
  private final AbstractCompiler compiler;
  private final Supplier<String> nameSupplier;
  private final boolean removeUnused;
  private final boolean markChanges;

  RenameLabels(final AbstractCompiler compiler) {
    this(compiler, new DefaultNameSupplier(), true, true);
  }

  RenameLabels(
      AbstractCompiler compiler,
      Supplier<String> supplier,
      boolean removeUnused,
      boolean markChanges) {
    this.compiler = compiler;
    this.nameSupplier = supplier;
    this.removeUnused = removeUnused;
    this.markChanges = markChanges;
  }

  static class DefaultNameSupplier implements Supplier<String> {
    // DefaultNameGenerator is used to create safe label names.
    private final NameGenerator nameGenerator;

    private DefaultNameSupplier(final NameGenerator nameGen) {
      this.nameGenerator = nameGen;
    }

    private DefaultNameSupplier() {
      this.nameGenerator = new DefaultNameGenerator(
          new HashSet<String>(), "", null);
    }

    @Override
    public String get() {
      return nameGenerator.generateNextName();
    }
  }

  /**
   * Iterate through the nodes, renaming all the labels.
   */
  class ProcessLabels implements ScopedCallback {

    private final boolean markChanges;

    ProcessLabels(boolean markChanges) {
      this.markChanges = markChanges;
      // Create a entry for global scope.
      namespaceStack.push(new LabelNamespace());
    }

    // A stack of labels namespaces. Labels in an outer scope aren't part of an
    // inner scope, so a new namespace is created each time a scope is entered.
    final Deque<LabelNamespace> namespaceStack = new ArrayDeque<>();

    // The list of generated names. Typically, the first name will be "a",
    // the second "b", etc.
    final ArrayList<String> names = new ArrayList<>();


    @Override
    public void enterScope(NodeTraversal nodeTraversal) {
      // Start a new namespace for label names.
      if (nodeTraversal.getScopeRoot().isFunction()) {
        namespaceStack.push(new LabelNamespace());
      }
    }

    @Override
    public void exitScope(NodeTraversal nodeTraversal) {
      if (nodeTraversal.getScopeRoot().isFunction()) {
        namespaceStack.pop();
      }
    }

    /**
     * shouldTraverse is call when descending into the Node tree, so it is used
     * here to build the context for label renames.
     *
     * {@inheritDoc}
     */
    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node node,
        Node parent) {
      if (node.isLabel()) {
        // Determine the new name for this label.
        LabelNamespace current = namespaceStack.peek();
        int currentDepth = current.renameMap.size() + 1;
        String name = node.getFirstChild().getString();

        // Store the context for this label name.
        LabelInfo li = new LabelInfo(currentDepth);
        checkState(!current.renameMap.containsKey(name));
        current.renameMap.put(name, li);

        // Create a new name, if needed, for this depth.
        if (names.size() < currentDepth) {
          names.add(nameSupplier.get());
        }

        String newName = getNameForId(currentDepth);
      }

      return true;
    }

    /**
     * Delegate the actual processing of the node to visitLabel and
     * visitBreakOrContinue.
     *
     * {@inheritDoc}
     */
    @Override
    public void visit(NodeTraversal t, Node node, Node parent) {
      switch (node.getToken()) {
        case LABEL:
          visitLabel(t, node, parent);
          break;

        case BREAK:
        case CONTINUE:
          visitBreakOrContinue(t, node);
          break;
        default:
          break;
      }
    }

    /**
     * Rename label references in breaks and continues.
     * @param node The break or continue node.
     */
    private void visitBreakOrContinue(NodeTraversal t, Node node) {
      Node nameNode = node.getFirstChild();
      if (nameNode != null) {
        // This is a named break or continue;
        String name = nameNode.getString();
        checkState(!name.isEmpty());
        LabelInfo li = getLabelInfo(name);
        if (li != null) {
          String newName = getNameForId(li.id);
          // Mark the label as referenced so it isn't removed.
          li.referenced = true;
          if (!name.equals(newName)) {
            // Give it the short name.
            nameNode.setString(newName);
            if (markChanges) {
              t.reportCodeChange();
            }
          }
        }
      }
    }

    /**
     * Rename or remove labels.
     * @param node  The label node.
     * @param parent The parent of the label node.
     */
    private void visitLabel(NodeTraversal t, Node node, Node parent) {
      Node nameNode = node.getFirstChild();
      checkState(nameNode != null);
      String name = nameNode.getString();
      LabelInfo li = getLabelInfo(name);
      // This is a label...
      if (li.referenced || !removeUnused) {
        String newName = getNameForId(li.id);
        if (!name.equals(newName)) {
          // ... and it is used, give it the short name.
          nameNode.setString(newName);
          if (markChanges) {
            t.reportCodeChange();
          }
        }
      } else {
        // ... and it is not referenced, just remove it.
        Node newChild = node.getLastChild();
        node.removeChild(newChild);
        parent.replaceChild(node, newChild);
        if (newChild.isBlock()) {
          NodeUtil.tryMergeBlock(newChild, false);
        }
        if (markChanges) {
          t.reportCodeChange();
        }
      }

      // Remove the label from the current stack of labels.
      namespaceStack.peek().renameMap.remove(name);
    }

    /**
     * @param id The id, which is the depth of the label in the current context,
     *        for which to get a short name.
     * @return The short name of the identified label.
     */
    String getNameForId(int id) {
      return names.get(id - 1);
    }

    /**
     * @param name The name to retrieve information about.
     * @return The structure representing the name in the current context.
     */
    LabelInfo getLabelInfo(String name) {
      return namespaceStack.peek().renameMap.get(name);
    }
  }

  @Override
  public void process(Node externs, Node root) {
    // Do variable reference counting.
    NodeTraversal.traverse(compiler, root, new ProcessLabels(markChanges));
  }

  private static class LabelInfo {
    boolean referenced = false;
    final int id;

    LabelInfo(int id) {
      this.id = id;
    }
  }


  private static class LabelNamespace {
    final Map<String, LabelInfo> renameMap = new HashMap<>();
  }

}
