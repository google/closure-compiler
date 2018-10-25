/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.base.StandardSystemProperty.USER_DIR;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the {@link PathUtil} class.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
@RunWith(JUnit4.class)
public final class PathUtilTest {

  @Test
  public void testCollapseDots() {
    assertThat(PathUtil.collapseDots("/foo")).isEqualTo("/foo");
    assertThat(PathUtil.collapseDots("/foo/bar//")).isEqualTo("/foo/bar");
    assertThat(PathUtil.collapseDots("..//foo/bar")).isEqualTo("foo/bar");
    assertThat(PathUtil.collapseDots("foo/.././bar")).isEqualTo("bar");
    assertThat(PathUtil.collapseDots("foo/../../bar/.")).isEqualTo("bar");
    assertThat(PathUtil.collapseDots("./foo/bar/../../baz")).isEqualTo("baz");
    assertThat(PathUtil.collapseDots("//foo/../bar/../baz/")).isEqualTo("/baz");
    assertThat(PathUtil.collapseDots("/foo/..")).isEqualTo("/");
    assertThat(PathUtil.collapseDots("foo/..")).isEmpty();
    assertThat(PathUtil.collapseDots("foo/./././bar//..")).isEqualTo("foo");
  }

  @Test
  public void testMakeAbsolute() {
    String cwd = USER_DIR.value();
    assertThat(PathUtil.makeAbsolute("/foo/")).isEqualTo("/foo");
    assertThat(PathUtil.makeAbsolute("/foo//bar")).isEqualTo("/foo/bar");
    assertThat(PathUtil.makeAbsolute("foo/")).isEqualTo(cwd + "/foo");
    assertThat(PathUtil.makeAbsolute("foo/b/..")).isEqualTo(cwd + "/foo");

    cwd = "/some/root";
    assertThat(PathUtil.makeAbsolute("/foo/", cwd)).isEqualTo("/foo");
    assertThat(PathUtil.makeAbsolute("/foo//bar", cwd)).isEqualTo("/foo/bar");
    assertThat(PathUtil.makeAbsolute("foo/", cwd)).isEqualTo("/some/root/foo");
    assertThat(PathUtil.makeAbsolute("../foo//./", cwd)).isEqualTo("/some/foo");
  }

  @Test
  public void testMakeRelative() {
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso/simba")).isEqualTo("simba");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso/simba/")).isEqualTo("simba");
    assertThat(PathUtil.makeRelative("gfs/bp/pso/", "gfs/bp/pso/simba")).isEqualTo("simba");
    assertThat(PathUtil.makeRelative("gfs/bp/pso/", "gfs/bp/pso/simba/")).isEqualTo("simba");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso/simba/ore"))
        .isEqualTo("simba/ore");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso/", "/gfs/bp/pso/simba/ore/"))
        .isEqualTo("simba/ore");
    assertThat(PathUtil.makeRelative("gfs/bp/pso/", "gfs/bp/pso")).isEqualTo(".");
    assertThat(PathUtil.makeRelative("gfs/bp/pso", "gfs/bp/pso/")).isEqualTo(".");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp/pso///")).isEqualTo(".");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "//gfs//bp//pso///")).isEqualTo(".");

    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/gfs/bp")).isEqualTo("..");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/gfs")).isEqualTo("../..");
    assertThat(PathUtil.makeRelative("gfs/bp/pso", "gfs/bp")).isEqualTo("..");
    assertThat(PathUtil.makeRelative("gfs/bp/pso", "gfs")).isEqualTo("../..");

    assertThat(PathUtil.makeRelative("gfs/bp/pso", "file")).isEqualTo("../../../file");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/abs/path")).isEqualTo("../../../abs/path");

    assertThat(PathUtil.makeRelative("gfs/bp/pso", "gfs/c/file")).isEqualTo("../../c/file");
    assertThat(PathUtil.makeRelative("/gfs/bp/pso", "/gfs/c/file")).isEqualTo("../../c/file");
  }
}
