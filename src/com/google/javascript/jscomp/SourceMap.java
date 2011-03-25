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
import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGenerator;
import com.google.debugging.sourcemap.SourceMapGeneratorV1;
import com.google.debugging.sourcemap.SourceMapGeneratorV2;
import com.google.javascript.rhino.Node;

import java.io.IOException;

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
     LEGACY {
       @Override SourceMap getInstance() {
         return new SourceMap(new SourceMapGeneratorV1());
       }
     },
     DEFAULT {
       @Override SourceMap getInstance() {
         return new SourceMap(new SourceMapGeneratorV2());
       }
     },
     EXPERIMENTIAL {
       @Override SourceMap getInstance() {
         return new SourceMap(new SourceMapGeneratorV2());
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
        return NodeUtil.isCall(node)
            || NodeUtil.isNew(node)
            || NodeUtil.isFunction(node)
            || NodeUtil.isName(node)
            || NodeUtil.isGet(node)
            || NodeUtil.isObjectLitKey(node, node.getParent())
            || (NodeUtil.isString(node) && NodeUtil.isGet(node.getParent()));
      }
    };
  }

  final SourceMapGenerator generator;

  private SourceMap(SourceMapGenerator generator) {
    this.generator = generator;
  }

  public void addMapping(
      Node node,
      FilePosition outputStartPosition,
      FilePosition outputEndPosition) {
    String sourceFile = (String) node.getProp(Node.SOURCENAME_PROP);
    // If the node does not have an associated source file or
    // its line number is -1, then the node does not have sufficient
    // information for a mapping to be useful.
    if (sourceFile == null || node.getLineno() < 0) {
      return;
    }

    String originalName = (String) node.getProp(Node.ORIGINALNAME_PROP);

    generator.addMapping(
        sourceFile, originalName,
        new FilePosition(node.getLineno(), node.getCharno()),
        outputStartPosition, outputEndPosition);
  }

  public void appendTo(Appendable out, String name) throws IOException {
    generator.appendTo(out, name);
  }

  public void reset() {
    generator.reset();
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
}
