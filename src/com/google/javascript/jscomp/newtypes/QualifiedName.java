/*
 * Copyright 2013 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import com.google.javascript.rhino.Node;

/**
 * Represents a qualified name.
 * (e.g. namespace.inner.Foo)
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class QualifiedName {
  private ImmutableList<String> parts;

  private QualifiedName(ImmutableList<String> parts) {
    this.parts = parts;
  }

  public QualifiedName(String s) {
    this.parts = ImmutableList.of(s);
  }

  public static QualifiedName join(QualifiedName lhs, QualifiedName rhs) {
    return new QualifiedName(ImmutableList.<String>builder()
        .addAll(lhs.parts).addAll(rhs.parts).build());
  }

  public static QualifiedName fromNode(Node qnameNode) {
    if (qnameNode == null || !qnameNode.isQualifiedName()) {
      return null;
    }
    return qnameNode.isName()
        ? new QualifiedName(qnameNode.getString())
        : new QualifiedName(ImmutableList.copyOf(
              Splitter.on('.').split(qnameNode.getQualifiedName())));
  }

  public static QualifiedName fromQualifiedString(String qname) {
    return qname.contains(".") ?
        new QualifiedName(ImmutableList.copyOf(Splitter.on('.').split(qname))) :
        new QualifiedName(qname);
  }

  public boolean isIdentifier() {
    return parts.size() == 1;
  }

  public QualifiedName getAllButLeftmost() {
    Preconditions.checkArgument(!isIdentifier());
    return new QualifiedName(parts.subList(1, parts.size()));
  }

  public String getLeftmostName() {
    return parts.get(0);
  }

  public QualifiedName getAllButRightmost() {
    Preconditions.checkArgument(!isIdentifier());
    return new QualifiedName(parts.subList(0, parts.size() - 1));
  }

  public String getRightmostName() {
    return parts.get(parts.size() - 1);
  }

  @Override
  public String toString() {
    return Joiner.on(".").join(parts);
  }
}
