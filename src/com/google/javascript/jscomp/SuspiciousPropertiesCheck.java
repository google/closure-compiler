/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks for obviously wrong properties.
 *
 * <p>Properties that are read but never set are typos or undeclared externs.
 * Properties that are set and never read are typos or dead code or undeclared
 * externs.
 *
 * <p>This check should produce no false positives, but it will certainly miss
 * a lot of real problems.
 *
 */
class SuspiciousPropertiesCheck implements CompilerPass {

  private final AbstractCompiler compiler;
  private final CheckLevel checkReads;
  private final CheckLevel checkWrites;

  /**
   * For undeclared externs, there might be hundreds of uses. Stop counting
   * after this many.
   */
  static final int MAX_REPORTS_PER_PROPERTY = 5;

  static final DiagnosticType READ_WITHOUT_SET = DiagnosticType.warning(
      "JSC_READ_WITHOUT_SET",
      "property {0} is read here, but never set");

  static final DiagnosticType SET_WITHOUT_READ = DiagnosticType.warning(
      "JSC_SET_WITHOUT_READ",
      "property {0} is set here, but never read");

  private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

  /** Mapping of names to their Property objects */
  private final Map<String, Property> properties = Maps.newHashMap();

  private Set<String> externPropertyNames = Sets.newHashSet();

  SuspiciousPropertiesCheck(
      AbstractCompiler compiler,
      CheckLevel checkReads,
      CheckLevel checkWrites) {
    this.compiler = compiler;
    this.checkReads = checkReads;
    this.checkWrites = checkWrites;
  }

  /**
   * Examines all the code, looking for suspicious reads and writes.
   * @param externs  all the extern declaration code
   * @param root  all the application code
   */
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs,
                           new ProcessExternedProperties());
    NodeTraversal.traverse(compiler, root, new ProcessProperties());

    for (Property prop : properties.values()) {
      if (prop.reads != null) {
        // Report all the reads without writes.
        for (Node n : prop.reads) {
          compiler.report(
              JSError.make((String) n.getProp(Node.SOURCENAME_PROP), n,
                           checkReads,
                           READ_WITHOUT_SET, n.getString()));
        }
      }
      if (prop.writes != null) {
        // Report all the writes without reads.
        for (Node n : prop.writes) {
          compiler.report(
              JSError.make((String) n.getProp(Node.SOURCENAME_PROP), n,
                           checkWrites,
                           SET_WITHOUT_READ, n.getString()));
        }
      }
    }
  }

  /**
   * Looks up the property object for a name, creating it if necessary.
   */
  private Property getProperty(String name) {
    Property prop = properties.get(name);
    if (prop == null) {
      prop = new Property();
      properties.put(name, prop);
    }
    return prop;
  }


  /**
   * Returns whether a property name is known to be externally defined.
   */
  private boolean isExternallyDefined(String name) {
    return externPropertyNames.contains(name);
  }


  /**
   * Returns whether a property name is externally referenceable.
   */
  private boolean isExported(String name) {
    return compiler.getCodingConvention().isExported(name);
  }

  /**
   * Builds up a list of all externally defined properties and global names.
   * Globals are included because they can appear as properties of a window
   * object.
   */
  private class ProcessExternedProperties extends AbstractPostOrderCallback {
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
        case Token.GETELEM:
          Node dest = n.getFirstChild().getNext();
          if (dest.getType() == Token.STRING) {
            externPropertyNames.add(dest.getString());
          }
          break;
        case Token.OBJECTLIT:
          for (Node child = n.getFirstChild();
               child != null;
               child = child.getNext()) {
            if (child.getType() != Token.NUMBER) {
              externPropertyNames.add(child.getString());
            }
          }
          break;
        case Token.NAME:
          // Treat global extern vars and funcs as extern properties,
          // because they are sometimes used as properties of window objects.
          String name = n.getString();

          // Avoid anonymous functions
          if (!name.isEmpty()) {
            // Only count globals
            Scope.Var var = t.getScope().getVar(name);
            if (var != null && !var.isLocal()) {
              externPropertyNames.add(name);
            }
          }
          break;
      }
    }
  }


  /**
   * Looks for property reads and writes, and accumulates info about them,
   * to be reported once all the code has been examined.
   */
  private class ProcessProperties extends AbstractPostOrderCallback {

    /**
     * Looks for property reads and writes.
     */
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.GETPROP:
          Node dest = n.getFirstChild().getNext();
          if (dest.getType() == Token.STRING) {
            if (parent.getType() == Token.ASSIGN &&
                parent.getFirstChild() == n ||
                NodeUtil.isExpressionNode(parent)) {
              // The property is the *target* of the assign, like:
              // x.foo = ...;
              // or else it's a stub property for duck-typing, like:
              // x.foo;
              addWrite(dest, t, false);
            } else {
              addRead(dest, t);
            }
          }
          break;
        case Token.OBJECTLIT:
          for (Node child = n.getFirstChild();
               child != null;
               child = child.getNext()) {
            if (child.getType() != Token.NUMBER) {
              addWrite(child, t, true);
            }
          }
          break;
        case Token.CALL:
          // Some generated code accesses properties using a special function
          // call syntax that has meaning only to the compiler.
          Node callee = n.getFirstChild();
          if (callee.getType() == Token.NAME &&
              callee.getString().equals(
                  RenameProperties.RENAME_PROPERTY_FUNCTION_NAME)) {
            Node argument = callee.getNext();
            if (argument.getType() == Token.STRING) {
              // Not sure how the property names will be used, so count as both
              // reads and writes to keep this pass silent about them.
              for (String name : DOT_PATTERN.split(argument.getString())) {
                Property prop = getProperty(name);
                prop.readCount++;
                prop.writeCount++;
                prop.reads = null;
                prop.writes = null;
              }
            }
          }
          break;
      }
    }

    /**
     * Determines whether this is a potentially bad read, and remembers the
     * location of the code.  A read clears out all this property's bad writes.
     * @param nameNode  the name of the property (a STRING node)
     * @param t  where we are in the code, so we can generate a useful report
     */
    private void addRead(Node nameNode, NodeTraversal t) {
      String name = nameNode.getString();
      Property prop = getProperty(name);
      prop.readCount++;
      if (prop.writeCount == 0 && !isExternallyDefined(name)) {
        // We don't know about any writes yet, so this might be a bad read.
        if (checkReads.isOn()) {
          if (prop.reads == null) {
            prop.reads = new ArrayList<Node>(MAX_REPORTS_PER_PROPERTY);
          }
          if (prop.reads.size() < MAX_REPORTS_PER_PROPERTY) {
            nameNode.putProp(Node.SOURCENAME_PROP, t.getSourceName());
            prop.reads.add(nameNode);
          }
        }
      } else {
        // There are writes, or this is an extern, so null out reads.
        prop.reads = null;
      }

      // There's at least this one read, so there are no invalid writes.
      prop.writes = null;
    }

    /**
     * Determines whether this is a potentially bad write, and remembers the
     * location of the code.  A write clears out all this property's bad reads.
     * Setting an object literal key will not result in a bad-write warning,
     * because it was too noisy.  (Object literal keys are usually externs of
     * some kind, and they're only renamed by the compiler if they're used to
     * set a prototype's value.)
     * @param nameNode  the name of the property (a STRING node)
     * @param t  where we are in the code, so we can generate a useful report
     * @param objLit  true iff this property is a key in an object literal
     */
    private void addWrite(Node nameNode, NodeTraversal t, boolean objLit) {
      String name = nameNode.getString();
      Property prop = getProperty(name);
      prop.writeCount++;
      if (prop.readCount == 0 && !isExported(name)) {
        // Don't count object-literal writes as possible bad writes.  We might
        // be writing a message for the user or the server.
        if (checkWrites.isOn() && !objLit) {
          // We haven't seen any reads, so this could be a bad write.
          if (prop.writes == null) {
            prop.writes = new ArrayList<Node>(MAX_REPORTS_PER_PROPERTY);
          }
          if (prop.writes.size() < MAX_REPORTS_PER_PROPERTY) {
            nameNode.putProp(Node.SOURCENAME_PROP, t.getSourceName());
            prop.writes.add(nameNode);
          }
        }
      } else {
        // There are reads, or this is an extern, so null out writes.
        prop.writes = null;
      }

      // There's at least this one write, so there are no invalid reads.
      prop.reads = null;
    }
  }


  /**
   * Tracks reads and writes for a property.
   */
  private static class Property {
    int readCount = 0;
    int writeCount = 0;

    /**
     * Accumulates reads until we see a write, at which point the list is
     * nulled out again.
     */
    List<Node> reads = null;

    /**
     * Accumulates writes until we see a read, at which point the list is
     * nulled out again.
     */
    List<Node> writes = null;

    Property() {
    }
  }
}
