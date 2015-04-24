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

package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for DependencyResolver.
 */
public final class DependencyResolverTest extends TestCase {

  DependencyFile fakeDeps1 = new DependencyFile(new VirtualFile("deps1",
      "goog.addDependency('a.js', ['a'], []);\n"
      + "goog.addDependency('b.js', ['b'], []);\n"
      + "goog.addDependency('c.js', ['c', 'c2'], ['a']);\n"
      + "goog.addDependency('d.js', ['d'], ['b', 'c']);\n"));

  DependencyFile fakeDeps2 = new DependencyFile(new VirtualFile("deps2",
      "goog.addDependency('e.js', ['e'], ['c2']);\n"
      + "goog.addDependency('f.js', ['f'], ['b', 'c']);\n"
      + "goog.addDependency('g.js', ['g'], ['a', 'b', 'c']);\n"
      + "goog.addDependency('h.js', ['h', 'i'], ['g', 'd', 'c']);\n"));

  DefaultDependencyResolver resolver;

  @Override protected void setUp() throws Exception {
    super.setUp();
    resolver = new DefaultDependencyResolver(ImmutableList.of(fakeDeps1, fakeDeps2), false);
  }

  public void testBasicCase() throws Exception {
     Collection<String> deps = resolver.getDependencies("goog.require('a');");
     assertThat(Joiner.on(",").useForNull("null").join(deps)).isEqualTo("base.js,a.js");
  }

  public void testSimpleDependencies() throws Exception {
     Collection<String> deps = resolver.getDependencies("goog.require('c');");
     assertThat(Joiner.on(",").useForNull("null").join(deps)).isEqualTo("base.js,a.js,c.js");
  }

  public void testTransitiveDependencies() throws Exception {
     Collection<String> deps = resolver.getDependencies("goog.require('e');");
     assertThat(Joiner.on(",").useForNull("null").join(deps)).isEqualTo("base.js,a.js,c.js,e.js");
  }

  public void testMultipleRequires() throws Exception {
     Collection<String> deps = resolver.getDependencies(
         "goog.require('e');goog.require('a');goog.require('b');");
     assertThat(Joiner.on(",").useForNull("null").join(deps))
         .isEqualTo("base.js,a.js,c.js,e.js,b.js");
  }

  public void testOneMoreForGoodMeasure() throws Exception {
    Collection<String> deps = resolver.getDependencies(
        "goog.require('g');goog.require('f');goog.require('c');");
    assertThat(Joiner.on(",").useForNull("null").join(deps))
        .isEqualTo("base.js,a.js,b.js,c.js,g.js,f.js");
  }

  public void testSharedSeenSetNoBaseFile() throws Exception {
    Set<String> seen = new HashSet<>();

    Collection<String> deps = resolver.getDependencies(
    "goog.require('g');goog.require('f');goog.require('c');", seen, false);

    Collection<String> depsLater = resolver.getDependencies(
    "goog.require('f');goog.require('c');", seen, false);

    assertThat(Joiner.on(",").useForNull("null").join(deps)).isEqualTo("a.js,b.js,c.js,g.js,f.js");
    assertThat(depsLater).isEmpty();
  }

  public void testSharedSeenSetNoBaseFileNewRequires() throws Exception {
    Set<String> seen = new HashSet<>();

    Collection<String> deps = resolver.getDependencies(
        "goog.require('f');goog.require('c');", seen, false);

    Collection<String> depsLater = resolver.getDependencies(
        "goog.require('g');goog.require('c');", seen, false);

    assertThat(Joiner.on(",").useForNull("null").join(deps)).isEqualTo("b.js,a.js,c.js,f.js");
    assertThat(Joiner.on(",").useForNull("null").join(depsLater)).isEqualTo("g.js");
  }

  public void testSharedSeenSetNoBaseFileMultipleProvides() throws Exception {
    Set<String> seen = new HashSet<>();

    Collection<String> deps = resolver.getDependencies(
        "goog.require('h');goog.require('i');", seen, false);

    assertThat(Joiner.on(",").useForNull("null").join(deps))
        .isEqualTo("a.js,b.js,c.js,g.js,d.js,h.js");
  }

  public void testNonExistentProvideLoose() throws Exception {
    Set<String> seen = new HashSet<>();
    resolver = new DefaultDependencyResolver(ImmutableList.of(fakeDeps1), false);
    Collection<String> deps = resolver.getDependencies(
        "goog.require('foo');goog.require('d');", seen, false);

    assertThat(Joiner.on(",").useForNull("null").join(deps)).isEqualTo("b.js,a.js,c.js,d.js");
  }

  public void testNonExistentProvideStrict() throws Exception {
    Set<String> seen = new HashSet<>();
    resolver = new DefaultDependencyResolver(ImmutableList.of(fakeDeps1), true);
    try {
      Collection<String> deps = resolver.getDependencies(
          "goog.require('foo');goog.require('a');", seen, false);
      fail("Service exception should be thrown");
    } catch (ServiceException expected) {}
  }

}
