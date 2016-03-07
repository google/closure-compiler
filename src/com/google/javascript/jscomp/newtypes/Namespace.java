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

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An object whose properties can be types (nominal types, enums, typedefs).
 * Constructor/interface functions, enums and object literals can be namespaces.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class Namespace {
  private Map<String, Namespace> namespaces = ImmutableMap.of();
  private Map<String, Typedef> typedefs = ImmutableMap.of();
  // "Simple type" properties (i.e. represented as JSTypes rather than something
  // more specific).
  protected PersistentMap<String, Property> otherProps = PersistentMap.create();
  protected String name;
  // Represents the namespace as an object that includes all namespace
  // properties. For NamespaceLit and EnumType, it is an object literal.
  // For RawNominalType, it is the constructor.
  protected JSType namespaceType;
  // A hack to cut-off the recursion when computing the type of circular
  // namespaces. Handling the circularity properly would require that either
  // ObjectType be mutable (so that we can create cycles), or that we retain
  // NamespaceLit during NewTypeInference. Another hack that avoids true
  // circularity but is more precise than the current one is to cut-off the
  // recursion after two unrollings instead of one.
  // TODO(dimvar): handle circular namespaces correctly.
  private boolean duringComputeJSType = false;

  protected abstract JSType computeJSType(JSTypes commonTypes);

  public final String getName() {
    return name;
  }

  private boolean isDefined(String name) {
    return namespaces.containsKey(name)
        || typedefs.containsKey(name)
        || otherProps.containsKey(name);
  }

  public final boolean isDefined(QualifiedName qname) {
    String name;
    Namespace ns;
    if (qname.isIdentifier()) {
      name = qname.getLeftmostName();
      ns = this;
    } else {
      name = qname.getRightmostName();
      ns = getSubnamespace(qname.getAllButRightmost());
      if (ns == null) {
        return false;
      }
    }
    return ns.isDefined(name);
  }

  public void addNamespace(QualifiedName qname, Namespace ns) {
    Preconditions.checkState(!isDefined(qname));
    Namespace subns = getReceiverNamespace(qname);
    if (subns.namespaces.isEmpty()) {
      subns.namespaces = new LinkedHashMap<>();
    }
    String name = qname.getRightmostName();
    Preconditions.checkState(!subns.namespaces.containsKey(name));
    subns.namespaces.put(name, ns);
  }

  public final Declaration getDeclaration(QualifiedName qname) {
    Namespace recv = getReceiverNamespace(qname);
    if (recv == null) {
      return null;
    }
    String name = qname.getRightmostName();
    if (!recv.isDefined(name)) {
      return null;
    }
    Namespace ns = recv.namespaces.get(name);
    DeclaredTypeRegistry scope = ns instanceof FunctionNamespace
        ? ((FunctionNamespace) ns).getScope() : null;
    return new Declaration(
        recv.getPropDeclaredType(name),
        recv.typedefs.get(name),
        ns, scope, false, false);
  }

  public final void addTypedef(QualifiedName qname, Typedef td) {
    Preconditions.checkState(!isDefined(qname));
    Namespace ns = getReceiverNamespace(qname);
    if (ns.typedefs.isEmpty()) {
      ns.typedefs = new LinkedHashMap<>();
    }
    String name = qname.getRightmostName();
    ns.typedefs.put(name, td);
  }

  private Namespace getReceiverNamespace(QualifiedName qname) {
    if (qname.isIdentifier()) {
      return this;
    } else {
      return getSubnamespace(qname.getAllButRightmost());
    }
  }

  public final Namespace getSubnamespace(QualifiedName qname) {
    String leftmost = qname.getLeftmostName();
    Namespace firstNamespace = this.namespaces.get(leftmost);
    if (firstNamespace == null || qname.isIdentifier()) {
      return firstNamespace;
    } else {
      return firstNamespace.getSubnamespace(qname.getAllButLeftmost());
    }
  }

  public final boolean hasSubnamespace(QualifiedName qname) {
    return getSubnamespace(qname) != null;
  }

  // Static properties

  public final boolean hasProp(String pname) {
    Property prop = otherProps.get(pname);
    if (prop == null) {
      return false;
    }
    Preconditions.checkState(!prop.isOptional());
    return true;
  }

  /** Add a new non-optional declared property to this namespace */
  public final void addProperty(String pname, Node defSite, JSType type, boolean isConstant) {
    if (type == null && isConstant) {
      type = JSType.UNKNOWN;
    }
    otherProps = otherProps.with(pname, isConstant
        ? Property.makeConstant(defSite, type, type)
        : Property.makeWithDefsite(defSite, type, type));
  }

  /** Add a new undeclared property to this namespace */
  public final void addUndeclaredProperty(
      String pname, Node defSite, JSType t, boolean isConstant) {
    if (otherProps.containsKey(pname)
        && !otherProps.get(pname).getType().isUnknown()) {
      return;
    }
    otherProps = otherProps.with(pname, isConstant
        ? Property.makeConstant(defSite, t, null)
        : Property.makeWithDefsite(defSite, t, null));
  }

  public final JSType getPropDeclaredType(String pname) {
    Property p = otherProps.get(pname);
    return p == null ? null : p.getDeclaredType();
  }

  public final JSType toJSType(JSTypes commonTypes) {
    if (this.namespaceType == null) {
      this.namespaceType = computeJSType(commonTypes);
    }
    return this.namespaceType;
  }

  // TODO(dimvar): namespaces can have many properties, and here we create a new
  // object type every time we add a property, and we discard them all except
  // the last one. Is it a perf win to have a method in ObjectType that adds all
  // properties to obj at once?
  protected final JSType withNamedTypes(JSTypes commonTypes, ObjectType obj) {
    if (this.duringComputeJSType) {
      this.namespaceType = JSType.fromObjectType(obj);
      return this.namespaceType;
    }
    this.duringComputeJSType = true;

    for (Map.Entry<String, Namespace> entry : this.namespaces.entrySet()) {
      obj = obj.withProperty(
          new QualifiedName(entry.getKey()),
          entry.getValue().toJSType(commonTypes));
    }

    this.duringComputeJSType = false;
    return JSType.fromObjectType(obj);
  }

  // Very similar to withNamedTypes, but copies the properties to Window's
  // RawNominalType instead of an ObjectType.
  public final void copyWindowProperties(JSTypes commonTypes, RawNominalType win) {
    Preconditions.checkArgument(win.getName().equals("Window"));
    Preconditions.checkNotNull(this.namespaces,
        "The built-in types are missing from window. "
        + "Perhaps you forgot to run DeclaredGlobalExternsOnWindow?");

    for (Map.Entry<String, Namespace> entry : this.namespaces.entrySet()) {
      Namespace ns = entry.getValue();
      if (ns instanceof RawNominalType) {
        RawNominalType rawType = (RawNominalType) ns;
        // Hack: circular namespace here, skip adding Window.
        if (!rawType.isFinalized()) {
          Preconditions.checkState(rawType.getName().equals("Window"),
              "Unexpected unfinalized type %s", rawType.getName());
          continue;
        }
      }
      win.addProtoProperty(entry.getKey(), null, ns.toJSType(commonTypes), true);
    }
    for (Map.Entry<String, Property> entry : this.otherProps.entrySet()) {
      win.addProtoProperty(
          entry.getKey(), null, entry.getValue().getType(), true);
    }
  }
}
