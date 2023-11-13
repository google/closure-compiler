/*
 * Copyright 2020 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.testing;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/** A Compiler that records requested runtime libraries, rather than injecting. */
public class NoninjectingCompiler extends Compiler {

  public NoninjectingCompiler(ErrorManager em) {
    super(em);
  }

  public NoninjectingCompiler() {
    super();
  }

  private final Set<String> injected = new LinkedHashSet<>();

  @Override
  public Node ensureLibraryInjected(String library, boolean force) {
    injected.add(library);
    return null;
  }

  @Override
  @GwtIncompatible
  public final void saveState(OutputStream outputStream) throws IOException {
    ObjectOutputStream out = new ObjectOutputStream(outputStream);
    out.writeObject(injected);
    // call the super method only after writing 'injected' to avoid issues deserializing a Java
    // object after TypedAST proto deserialization
    super.saveState(outputStream);
  }

  @SuppressWarnings("unchecked")
  @Override
  @GwtIncompatible
  public final void restoreState(InputStream inputStream)
      throws IOException, ClassNotFoundException {
    ObjectInputStream in = new ObjectInputStream(inputStream);
    injected.clear();
    injected.addAll((Set<String>) in.readObject());
    super.restoreState(inputStream);
  }

  public final ImmutableSet<String> getInjected() {
    return ImmutableSet.copyOf(injected);
  }
}
