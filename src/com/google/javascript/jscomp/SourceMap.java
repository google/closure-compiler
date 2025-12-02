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

package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapFormat;
import com.google.debugging.sourcemap.SourceMapGenerator;
import com.google.debugging.sourcemap.SourceMapGeneratorFactory;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Collects information mapping the generated (compiled) source back to its original source for
 * debugging purposes.
 *
 * @see CodeConsumer
 * @see CodeGenerator
 * @see CodePrinter
 */
public final class SourceMap {
  /** An enumeration of available source map formats */
  public static enum Format {
    DEFAULT {
      @Override
      SourceMap getInstance() {
        return new SourceMap(SourceMapGeneratorFactory.getInstance(SourceMapFormat.DEFAULT));
      }
    },
    V3 {
      @Override
      SourceMap getInstance() {
        return new SourceMap(SourceMapGeneratorFactory.getInstance(SourceMapFormat.V3));
      }
    };

    abstract SourceMap getInstance();
  }

  /** Source maps can be very large different levels of detail can be specified. */
  public static enum DetailLevel implements Predicate<Node> {
    // ALL is best when the fullest details are needed for debugging or for
    // code-origin analysis.
    ALL {
      @Override
      public boolean apply(Node node) {
        return true;
      }
    },
    // SYMBOLS is intended to be used for stack trace deobfuscation when full
    // detail is not needed.
    SYMBOLS {
      @Override
      public boolean apply(Node node) {
        return node.isCall()
            || node.isNew()
            || node.isFunction()
            || node.isName()
            || NodeUtil.isNormalOrOptChainGet(node)
            || NodeUtil.mayBeObjectLitKey(node)
            || node.isTaggedTemplateLit();
      }
    }
  }

  /**
   * Function that mape a "destination" location to use within the source map. Should return null if
   * the value is not mapped.
   */
  @FunctionalInterface
  public interface LocationMapping {
    /**
     * @param location the location to transform
     * @return the transformed location or null if not transformed
     */
    @Nullable String map(String location);
  }

  /** Simple {@link LocationMapping} that strips a prefix from a location. */
  public static final class PrefixLocationMapping implements LocationMapping {
    final String prefix;
    final String replacement;

    public PrefixLocationMapping(String prefix, String replacement) {
      this.prefix = prefix;
      this.replacement = replacement;
    }

    @Override
    public @Nullable String map(String location) {
      if (location.startsWith(prefix)) {
        return replacement + location.substring(prefix.length());
      }
      return null;
    }

    public String toString() {
      return "(" + prefix + "|" + replacement + ")";
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof PrefixLocationMapping prefixLocationMapping) {
        return prefixLocationMapping.prefix.equals(prefix)
            && prefixLocationMapping.replacement.equals(replacement);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(prefix, replacement);
    }
  }

  private final SourceMapGenerator generator;
  private List<? extends LocationMapping> prefixMappings = ImmutableList.of();
  private final Map<String, String> sourceLocationFixupCache = new LinkedHashMap<>();

  /**
   * A mapping derived from input source maps. Maps back to input sources that inputs to this
   * compilation job have been generated from, and used to create a source map that maps all the way
   * back to original inputs. {@code null} if no such mapping is wanted.
   */
  private @Nullable SourceFileMapping mapping;

  private SourceMap(SourceMapGenerator generator) {
    this.generator = generator;
  }

  /**
   * Maintains a mapping from a given node to the position in the source code at which its generated
   * form was placed. The positions are typically relative to the source file the node is located
   * in, but might be adjusted if that source is being concatenated to other sources.
   */
  public static class Mapping {
    Node node;
    FilePosition start;
    FilePosition end;

    @Override
    public String toString() {
      // This toString() representation is used for debugging purposes only.
      return "Mapping: start " + start + ", end " + end + ", node " + node;
    }
  }

  public void addMapping(Mapping mapping) {
    addMapping(mapping.node, mapping.start, mapping.end);
  }

  public void addMapping(
      Node node, FilePosition outputStartPosition, FilePosition outputEndPosition) {
    // If the node does not have an associated source file or
    // its line number is -1, then the node does not have sufficient
    // information for a mapping to be useful.
    StaticSourceFile sourceFile = node.getStaticSourceFile();
    if (sourceFile == null || node.getLineno() < 0) {
      return;
    }

    String sourceFileName = sourceFile.getName();
    int lineNo = node.getLineno();
    int charNo = node.getCharno();
    String originalName = SourceMap.getOriginalName(node);

    if (mapping != null) {
      OriginalMapping sourceMapping = mapping.getSourceMapping(sourceFileName, lineNo, charNo);
      if (sourceMapping == null) {
        // The source file does not have a input map. We consider this to be an
        // original source range and include it in the output map.
      } else if (sourceMapping.equals(OriginalMapping.getDefaultInstance())) {
        // The source file does have an input map, but it does not map the
        // location. We consider this to be a synthetic code range and do not
        // include it in the output map.
        // TODO b/452676030 - Report the sourceless mapping.
        return;
      } else {
        // The source file mapped our code range to its original source location.
        sourceFileName = sourceMapping.getOriginalFile();
        lineNo = sourceMapping.getLineNumber();
        charNo = sourceMapping.getColumnPosition();
        String identifier = sourceMapping.getIdentifier();
        if (sourceMapping.hasIdentifier() && !identifier.isEmpty()) {
          originalName = identifier;
        }
      }
    }

    sourceFileName = fixupSourceLocation(sourceFileName);

    // Rhino source lines are one based but for v3 source maps, we make
    // them zero based.
    int lineBaseOffset = 1;

    generator.addMapping(
        sourceFileName,
        originalName,
        new FilePosition(lineNo - lineBaseOffset, charNo),
        outputStartPosition,
        outputEndPosition);
  }

  public void addSourceFile(String name, String code) {
    generator.addSourcesContent(fixupSourceLocation(name), code);
  }

  private static @Nullable String getOriginalName(Node node) {
    if (node.getOriginalName() != null) {
      return node.getOriginalName();
    }
    if (node.isMemberFunctionDef()) {
      return node.getFirstChild().getOriginalName();
    }
    return null;
  }

  /**
   * @param sourceFile The source file location to fixup.
   * @return a remapped source file.
   */
  private String fixupSourceLocation(String sourceFile) {
    if (prefixMappings.isEmpty()) {
      return sourceFile;
    }

    String fixed = sourceLocationFixupCache.get(sourceFile);
    if (fixed != null) {
      return fixed;
    }

    // Replace the first prefix found with its replacement
    for (LocationMapping mapping : prefixMappings) {
      fixed = mapping.map(sourceFile);
      if (fixed != null) {
        break;
      }
    }

    // If none of the mappings match then use the original file path.
    if (fixed == null) {
      fixed = sourceFile;
    }

    sourceLocationFixupCache.put(sourceFile, fixed);
    return fixed;
  }

  public void appendTo(Appendable out, String name) throws IOException {
    generator.appendTo(out, fixupSourceLocation(name));
  }

  public void reset() {
    generator.reset();
    sourceLocationFixupCache.clear();
  }

  public void setStartingPosition(int offsetLine, int offsetIndex) {
    generator.setStartingPosition(offsetLine, offsetIndex);
  }

  public void setWrapperPrefix(String prefix) {
    generator.setWrapperPrefix(prefix);
  }

  public void validate(boolean validate) {
    generator.validate(validate);
  }

  /** */
  public void setPrefixMappings(List<? extends LocationMapping> sourceMapLocationMappings) {
    this.prefixMappings = sourceMapLocationMappings;
  }

  public void setSourceFileMapping(SourceFileMapping mapping) {
    this.mapping = mapping;
  }
}
