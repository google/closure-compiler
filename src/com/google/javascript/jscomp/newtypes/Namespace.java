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
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;

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
  // These properties can themselves be namespaces
  protected Map<String, RawNominalType> nominals = null;
  protected Map<String, EnumType> enums = null;
  protected Map<String, NamespaceLit> namespaces = null;
  // Non-namespace properties
  protected Map<String, Typedef> typedefs = null;
  protected PersistentMap<String, Property> otherProps = PersistentMap.create();

  public boolean isDefined(QualifiedName qname) {
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
    return ns.nominals != null && ns.nominals.containsKey(name)
        || ns.enums != null && ns.enums.containsKey(name)
        || ns.namespaces != null && ns.namespaces.containsKey(name)
        || ns.typedefs != null && ns.typedefs.containsKey(name)
        || ns.otherProps.containsKey(name);
  }

  public void addSubnamespace(QualifiedName qname) {
    Preconditions.checkState(!isDefined(qname));
    Namespace ns = getReceiverNamespace(qname);
    if (ns.namespaces == null) {
      ns.namespaces = new LinkedHashMap<>();
    }
    String name = qname.getRightmostName();
    ns.namespaces.put(name, new NamespaceLit());
  }

  public void addNominalType(QualifiedName qname, RawNominalType rawNominalType) {
    Preconditions.checkState(!isDefined(qname));
    Namespace ns = getReceiverNamespace(qname);
    if (ns.nominals == null) {
      ns.nominals = new LinkedHashMap<>();
    }
    String name = qname.getRightmostName();
    ns.nominals.put(name, rawNominalType);
  }

  public void addTypedef(QualifiedName qname, Typedef td) {
    Preconditions.checkState(!isDefined(qname));
    Namespace ns = getReceiverNamespace(qname);
    if (ns.typedefs == null) {
      ns.typedefs = new LinkedHashMap<>();
    }
    String name = qname.getRightmostName();
    ns.typedefs.put(name, td);
  }

  public void addEnum(QualifiedName qname, EnumType e) {
    Preconditions.checkState(!isDefined(qname));
    Namespace ns = getReceiverNamespace(qname);
    if (ns.enums == null) {
      ns.enums = new LinkedHashMap<>();
    }
    String name = qname.getRightmostName();
    ns.enums.put(name, e);
  }

  private Namespace getLocalSubnamespace(String name) {
    if (nominals != null && nominals.containsKey(name)) {
      return nominals.get(name);
    } else if (namespaces != null && namespaces.containsKey(name)) {
      return namespaces.get(name);
    } else if (enums != null && enums.containsKey(name)) {
      return enums.get(name);
    } else {
      return null;
    }
  }

  private Namespace getReceiverNamespace(QualifiedName qname) {
    if (qname.isIdentifier()) {
      return this;
    } else {
      return getSubnamespace(qname.getAllButRightmost());
    }
  }

  public Namespace getSubnamespace(QualifiedName qname) {
    String leftmost = qname.getLeftmostName();
    Namespace firstNamespace = getLocalSubnamespace(leftmost);
    if (firstNamespace == null || qname.isIdentifier()) {
      return firstNamespace;
    } else {
      return firstNamespace.getSubnamespace(qname.getAllButLeftmost());
    }
  }

  public RawNominalType getNominalType(QualifiedName qname) {
    Namespace ns = getReceiverNamespace(qname);
    if (ns == null || ns.nominals == null) {
      return null;
    }
    String name = qname.getRightmostName();
    return ns.nominals.get(name);
  }

  public EnumType getEnumType(QualifiedName qname) {
    Namespace ns = getReceiverNamespace(qname);
    if (ns == null || ns.enums == null) {
      return null;
    }
    String name = qname.getRightmostName();
    return ns.enums.get(name);
  }

  public Typedef getTypedef(QualifiedName qname) {
    Namespace ns = getReceiverNamespace(qname);
    if (ns == null || ns.typedefs == null) {
      return null;
    }
    String name = qname.getRightmostName();
    return ns.typedefs.get(name);
  }

  public boolean hasSubnamespace(QualifiedName qname) {
    return getSubnamespace(qname) != null;
  }

  // Static properties

  public boolean hasProp(String pname) {
    Property prop = otherProps.get(pname);
    if (prop == null) {
      return false;
    }
    Preconditions.checkState(!prop.isOptional());
    return true;
  }

  /** Add a new non-optional declared property to this namespace */
  public void addProperty(String pname, JSType type, boolean isConstant) {
    if (type == null && isConstant) {
      type = JSType.UNKNOWN;
    }
    otherProps = otherProps.with(pname, isConstant ?
        Property.makeConstant(type, type) : Property.make(type, type));
  }

  /** Add a new undeclared property to this namespace */
  public void addUndeclaredProperty(
      String pname, JSType t, boolean isConstant) {
    if (otherProps.containsKey(pname)
        && !otherProps.get(pname).getType().isUnknown()) {
      return;
    }
    otherProps = otherProps.with(pname, isConstant ?
        Property.makeConstant(t, null) : Property.make(t, null));
  }

  public JSType getPropDeclaredType(String pname) {
    Property p = otherProps.get(pname);
    return p == null ? null : p.getDeclaredType();
  }

  public abstract JSType toJSType();

  protected JSType withNamedTypes(ObjectType obj) {
    if (nominals != null) {
      for (Map.Entry<String, RawNominalType> entry : nominals.entrySet()) {
        obj = obj.withProperty(
            new QualifiedName(entry.getKey()), entry.getValue().toJSType());
      }
    }
    if (enums != null) {
      for (Map.Entry<String, EnumType> entry : enums.entrySet()) {
        obj = obj.withProperty(
            new QualifiedName(entry.getKey()), entry.getValue().toJSType());
      }
    }
    if (namespaces != null) {
      for (Map.Entry<String, NamespaceLit> entry : namespaces.entrySet()) {
        obj = obj.withProperty(
            new QualifiedName(entry.getKey()), entry.getValue().toJSType());
      }
    }
    return JSType.fromObjectType(obj);
  }
}
