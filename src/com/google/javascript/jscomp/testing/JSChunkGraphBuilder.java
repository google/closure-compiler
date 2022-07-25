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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.JSChunk;
import com.google.javascript.jscomp.SourceFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** Utility to create various input {@link com.google.javascript.jscomp.JSChunk} graphs */
public final class JSChunkGraphBuilder {

  private enum GraphType {
    // each chunk depends on the chunk immediately before
    CHAIN() {
      @Override
      void addDependencyEdges(JSChunk[] chunks) {
        for (int i = 1; i < chunks.length; i++) {
          chunks[i].addDependency(chunks[i - 1]);
        }
      }
    },
    // each chunk depends on the fist chunk
    STAR() {
      @Override
      void addDependencyEdges(JSChunk[] chunks) {
        for (int i = 1; i < chunks.length; i++) {
          chunks[i].addDependency(chunks[0]);
        }
      }
    },
    // no dependencies between chunks
    DISJOINT,
    // chunk 2 depends on chunk 1, and all others depend on chunk 2
    BUSH() {
      @Override
      void addDependencyEdges(JSChunk[] chunks) {
        checkState(chunks.length > 2, "BUSHes need at least three graph nodes");
        for (int i = 1; i < chunks.length; i++) {
          chunks[i].addDependency(chunks[i == 1 ? 0 : 1]);
        }
      }
    },
    // binary tree
    TREE() {
      @Override
      void addDependencyEdges(JSChunk[] chunks) {
        for (int i = 1; i < chunks.length; i++) {
          chunks[i].addDependency(chunks[(i - 1) / 2]);
        }
      }
    };

    void addDependencyEdges(JSChunk[] chunks) {}
  }

  private final GraphType graphType;
  private final ArrayList<String> chunks = new ArrayList<>();
  // correspondances between chunk indices in `chunks` and specified names. if no name is specified
  // defaults to "m{i}"
  private final LinkedHashMap<Integer, String> chunkNames = new LinkedHashMap<>();
  private String filenameFormat = "i%s.js";

  private JSChunkGraphBuilder(GraphType graphType) {
    this.graphType = graphType;
  }

  /**
   * Generates a list of chunks from a list of inputs, such that each chunk depends on the chunk
   * before it.
   */
  public static JSChunkGraphBuilder forChain() {
    return new JSChunkGraphBuilder(GraphType.CHAIN);
  }

  /**
   * Generates a list of chunks from a list of inputs, such that each chunk depends on the first
   * chunk.
   */
  public static JSChunkGraphBuilder forStar() {
    return new JSChunkGraphBuilder(GraphType.STAR);
  }

  /**
   * Generates a list of chunks from a list of inputs, such that chunks form a bush formation. In a
   * bush formation, chunk 2 depends on chunk 1, and all other chunks depend on chunk 2.
   */
  public static JSChunkGraphBuilder forBush() {
    return new JSChunkGraphBuilder(GraphType.BUSH);
  }

  /**
   * Generates a list of chunks from a list of inputs, such that chunks form a tree formation. In a
   * tree formation, chunk N depends on chunk `floor(N/2)`, So the chunks form a balanced binary
   * tree.
   */
  public static JSChunkGraphBuilder forTree() {
    return new JSChunkGraphBuilder(GraphType.TREE);
  }

  /**
   * Generates a list of chunks from a list of inputs with no dependency edges between the chunks
   */
  public static JSChunkGraphBuilder forUnordered() {
    return new JSChunkGraphBuilder(GraphType.DISJOINT);
  }

  @CanIgnoreReturnValue
  public JSChunkGraphBuilder addChunk(String input) {
    this.chunks.add(input);
    return this;
  }

  @CanIgnoreReturnValue
  public JSChunkGraphBuilder addChunkWithName(String input, String chunkName) {
    this.chunkNames.put(this.chunks.size(), chunkName);
    this.chunks.add(input);
    return this;
  }

  @CanIgnoreReturnValue
  public JSChunkGraphBuilder addChunks(List<String> inputs) {
    this.chunks.addAll(inputs);
    return this;
  }

  public JSChunkGraphBuilder addChunks(String[] chunks) {
    return this.addChunks(Arrays.asList(chunks));
  }

  /**
   * Set a format string for the generated file names for each chunk.
   *
   * <p>String must contain one substitution, the index of the file. e.g. "i%s.js" will produce
   * i0.js, i1.js, etc.
   */
  @CanIgnoreReturnValue
  public JSChunkGraphBuilder setFilenameFormat(String filenameFormat) {
    this.filenameFormat = filenameFormat;
    return this;
  }

  public JSChunk[] build() {
    JSChunk[] chunks = new JSChunk[this.chunks.size()];
    for (int i = 0; i < this.chunks.size(); i++) {
      String chunkName = this.chunkNames.getOrDefault(i, "m" + i);
      JSChunk chunk = chunks[i] = new JSChunk(chunkName);

      chunk.add(
          SourceFile.fromCode(Strings.lenientFormat(this.filenameFormat, i), this.chunks.get(i)));
    }
    this.graphType.addDependencyEdges(chunks);
    return chunks;
  }
}
