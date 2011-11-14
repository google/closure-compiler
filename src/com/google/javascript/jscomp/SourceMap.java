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
import com.google.common.collect.Maps;
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapFormat;
import com.google.debugging.sourcemap.SourceMapGenerator;
import com.google.debugging.sourcemap.SourceMapGeneratorFactory;
import com.google.debugging.sourcemap.SourceMapGeneratorV1;
import com.google.debugging.sourcemap.SourceMapGeneratorV2;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Collects information mapping the generated (compiled) source back to
 * its original source for debugging purposes.
 *
 * @see CodeConsumer
 * @see CodeGenerator
 * @see CodePrinter
 *
 */
public class SourceMap {

  public static enum Format {
     V1 {
       @Override SourceMap getInstance() {
         return new SourceMap(
           SourceMapGeneratorFactory.getInstance(SourceMapFormat.V1));
       }
     },
     DEFAULT {
       @Override SourceMap getInstance() {
         return new SourceMap(
           SourceMapGeneratorFactory.getInstance(SourceMapFormat.DEFAULT));
       }
     },
     V2 {
       @Override SourceMap getInstance() {
         return new SourceMap(
           SourceMapGeneratorFactory.getInstance(SourceMapFormat.V2));
        }
     },
     V3 {
       @Override SourceMap getInstance() {
         return new SourceMap(
           SourceMapGeneratorFactory.getInstance(SourceMapFormat.V3));
        }
     };
     abstract SourceMap getInstance();
  }

  /**
   * Source maps can be very large different levels of detail can be specified.
   */
  public static enum DetailLevel implements Predicate<Node> {
    // ALL is best when the fullest details are needed for debugging or for
    // code-origin analysis.
    ALL {
      @Override public boolean apply(Node node) {
        return true;
      }
    },
    // SYMBOLS is intended to be used for stack trace deobfuscation when full
    // detail is not needed.
    SYMBOLS {
      @Override public boolean apply(Node node) {
        return node.isCall()
            || node.isNew()
            || node.isFunction()
            || node.isName()
            || NodeUtil.isGet(node)
            || NodeUtil.isObjectLitKey(node, node.getParent())
            || (node.isString() && NodeUtil.isGet(node.getParent()));
      }
    };
  }

  public static class LocationMapping {
    final String prefix;
    final String replacement;
    public LocationMapping(String prefix, String replacement) {
      this.prefix = prefix;
      this.replacement = replacement;
    }
  }

  private final SourceMapGenerator generator;
  private List<LocationMapping> prefixMappings = Collections.emptyList();
  private final Map<String, String> sourceLocationFixupCache =
      Maps.newHashMap();

  private SourceMap(SourceMapGenerator generator) {
    this.generator = generator;
  }

  public void addMapping(
      Node node,
      FilePosition outputStartPosition,
      FilePosition outputEndPosition) {
    String sourceFile = node.getSourceFileName();

    // If the node does not have an associated source file or
    // its line number is -1, then the node does not have sufficient
    // information for a mapping to be useful.
    if (sourceFile == null || node.getLineno() < 0) {
      return;
    }

    sourceFile = fixupSourceLocation(sourceFile);

    String originalName = (String) node.getProp(Node.ORIGINALNAME_PROP);

    // Strangely, Rhino source lines are one based but columns are
    // zero based.
    // We don't change this for the v1 or v2 source maps but for
    // v3 we make them both 0 based.
    int lineBaseOffset = 1;
    if (generator instanceof SourceMapGeneratorV1
        || generator instanceof SourceMapGeneratorV2) {
      lineBaseOffset = 0;
    }

    generator.addMapping(
        sourceFile, originalName,
        new FilePosition(node.getLineno() - lineBaseOffset, node.getCharno()),
        outputStartPosition, outputEndPosition);
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
      if (sourceFile.startsWith(mapping.prefix)) {
        fixed = mapping.replacement + sourceFile.substring(
          mapping.prefix.length());
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
    generator.appendTo(out, name);
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

  /**
   * @param sourceMapLocationMappings
   */
  public void setPrefixMappings(List<LocationMapping> sourceMapLocationMappings) {
     this.prefixMappings = sourceMapLocationMappings;
  }
}
