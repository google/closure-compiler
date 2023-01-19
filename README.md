# [Google Closure Compiler](https://developers.google.com/closure/compiler/)

[![Build Status](https://github.com/google/closure-compiler/workflows/Compiler%20CI/badge.svg)](https://github.com/google/closure-compiler/actions)
[![Open Source Helpers](https://www.codetriage.com/google/closure-compiler/badges/users.svg)](https://www.codetriage.com/google/closure-compiler)
[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-v2.0%20adopted-ff69b4.svg)](https://github.com/google/closure-compiler/blob/master/code_of_conduct.md)

The [Closure Compiler](https://developers.google.com/closure/compiler/) is a
tool for making JavaScript download and run faster. It is a true compiler for
JavaScript. Instead of compiling from a source language to machine code, it
compiles from JavaScript to better JavaScript. It parses your JavaScript,
analyzes it, removes dead code and rewrites and minimizes what's left. It also
checks syntax, variable references, and types, and warns about common JavaScript
pitfalls.

## Getting Started

The easiest way to install the compiler is with [NPM](https://npmjs.com) or
[Yarn](https://yarnpkg.com):

```bash
yarn global add google-closure-compiler
# OR
npm i -g google-closure-compiler
```

The package manager will link the binary for you, and you can access the
compiler with:

```bash
google-closure-compiler
```

This starts the compiler in interactive mode. Type:

```javascript
var x = 17 + 25;
```

Hit `Enter`, then `Ctrl+Z` (on Windows) or `Ctrl+D` (on Mac/Linux), then `Enter`
again. The Compiler will respond with the compiled output (using `SIMPLE` mode
by default):

```javascript
var x=42;
```

#### Downloading from Maven Repository

A pre-compiled release of the compiler is also available via
[Maven](https://mvnrepository.com/artifact/com.google.javascript/closure-compiler).

### Basic usage

The Closure Compiler has many options for reading input from a file, writing
output to a file, checking your code, and running optimizations. Here is a
simple example of compressing a JS program:

```bash
google-closure-compiler --js file.js --js_output_file file.out.js
```

We get the **most benefit** from the compiler if we give it **all of our source
code** (see [Compiling Multiple Scripts](#compiling-multiple-scripts)), which
allows us to use `ADVANCED` optimizations:

```bash
google-closure-compiler -O ADVANCED rollup.js --js_output_file rollup.min.js
```

NOTE: The output below is just an example and not kept up-to-date. The
  [Flags and Options wiki page](https://github.com/google/closure-compiler/wiki/Flags-and-Options)
  is updated during each release.

To see all of the compiler's options, type:

```bash
google-closure-compiler --help
```

<table>
<thead>
  <tr>
    <th><code>--flag</code></th>
    <th>Description</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td><code>--compilation_level (-O)</code></td>
    <td>
      Specifies the compilation level to use.
      Options: <code>BUNDLE</code>, <code>WHITESPACE_ONLY</code>,
      <code>SIMPLE</code> (default), <code>ADVANCED</code>
    </td>
  </tr>
  <tr>
    <td><code>--env</code></td>
    <td>
      Determines the set of builtin externs to load.
      Options: <code>BROWSER</code>, <code>CUSTOM</code>.
      Defaults to <code>BROWSER</code>.
    </td>
  </tr>
  <tr>
    <td><code>--externs</code></td>
    <td>The file containing JavaScript externs. You may specify multiple</td>
  </tr>
  <tr>
    <td><code>--js</code></td>
    <td>
      The JavaScript filename. You may specify multiple. The flag name is
      optional, because args are interpreted as files by default. You may also
      use minimatch-style glob patterns. For example, use
      <code>--js='**.js' --js='!**_test.js'</code> to recursively include all
      js files that do not end in <code>_test.js</code>
    </td>
  </tr>
  <tr>
    <td><code>--js_output_file</code></td>
    <td>
      Primary output filename. If not specified, output is written to stdout.
    </td>
  </tr>
  <tr>
    <td><code>--language_in</code></td>
    <td>
      Sets the language spec to which input sources should conform.
      Options: <code>ECMASCRIPT3</code>, <code>ECMASCRIPT5</code>,
      <code>ECMASCRIPT5_STRICT</code>, <code>ECMASCRIPT_2015</code>,
      <code>ECMASCRIPT_2016</code>, <code>ECMASCRIPT_2017</code>,
      <code>ECMASCRIPT_2018</code>, <code>ECMASCRIPT_2019</code>,
      <code>STABLE</code>, <code>ECMASCRIPT_NEXT</code>
    </td>
  </tr>
  <tr>
    <td><code>--language_out</code></td>
    <td>
      Sets the language spec to which output should conform.
      Options: <code>ECMASCRIPT3</code>, <code>ECMASCRIPT5</code>,
      <code>ECMASCRIPT5_STRICT</code>, <code>ECMASCRIPT_2015</code>,
      <code>ECMASCRIPT_2016</code>, <code>ECMASCRIPT_2017</code>,
      <code>ECMASCRIPT_2018</code>, <code>ECMASCRIPT_2019</code>,
      <code>STABLE</code>
    </td>
  </tr>
  <tr>
    <td><code>--warning_level (-W)</code></td>
    <td>Specifies the warning level to use.
      Options: <code>QUIET</code>, <code>DEFAULT</code>, <code>VERBOSE</code>
    </td>
  </tr>
</tbody>
</table>

#### See the [Google Developers Site](https://developers.google.com/closure/compiler/docs/gettingstarted_app) for documentation including instructions for running the compiler from the command line.

### NodeJS API

You can access the compiler in a JS program by importing
`google-closure-compiler`:

```javascript
import closureCompiler from 'google-closure-compiler';
const { compiler } = closureCompiler;

new compiler({
  js: 'file-one.js',
  compilation_level: 'ADVANCED'
});
```

This package will provide programmatic access to the native Graal binary in most
cases, and will fall back to the Java version otherwise.

#### Please see the [closure-compiler-npm](https://github.com/google/closure-compiler-npm/tree/master/packages/google-closure-compiler) repository for documentation on accessing the compiler in JS.

## Compiling Multiple Scripts

If you have multiple scripts, you should compile them all together with one
compile command.

```bash
google-closure-compiler in1.js in2.js in3.js --js_output_file out.js
```

You can also use minimatch-style globs.

```bash
# Recursively include all js files in subdirs
google-closure-compiler 'src/**.js' --js_output_file out.js

# Recursively include all js files in subdirs, excluding test files.
# Use single-quotes, so that bash doesn't try to expand the '!'
google-closure-compiler 'src/**.js' '!**_test.js' --js_output_file out.js
```

The Closure Compiler will concatenate the files in the order they're passed at
the command line.

If you're using globs or many files, you may start to run into problems with
managing dependencies between scripts. In this case, you should use the
[Closure Library](https://developers.google.com/closure/library/). It contains
functions for enforcing dependencies between scripts, and Closure Compiler will
re-order the inputs automatically.

## Getting Help

1.  Post in the
    [Closure Compiler Discuss Group](https://groups.google.com/forum/#!forum/closure-compiler-discuss).
2.  Ask a question on
    [Stack Overflow](https://stackoverflow.com/questions/tagged/google-closure-compiler).
3.  Consult the [FAQ](https://github.com/google/closure-compiler/wiki/FAQ).

## Building the Compiler

To build the compiler yourself, you will need the following:

Prerequisite                                                               | Description
-------------------------------------------------------------------------- | -----------
[Java 8 or later](https://java.com)                                        | Used to compile the compiler's source code.
[Git](https://git-scm.com/)                                                | Used by Bazel to download dependencies.
[Bazelisk](https://bazel.build/install/bazelisk) | Used to build the various compiler targets.

### Installing Bazelisk

Bazelisk is a wrapper around Bazel that dynamically loads the appropriate
version of Bazel for a given repository. Using it prevents spurious errors that
result from using the wrong version of Bazel to build the compiler, as well as
makes it easy to use different Bazel versions for other projects.

Bazelisk is available through many package managers. Feel free to use whichever
you're most comfortable with.

[Instructions for installing Bazelisk](https://bazel.build/install/bazelisk).

### Building from a terminal

```bash
$ bazelisk build //:compiler_uberjar_deploy.jar
# OR to build everything
$ bazelisk build //:all
```

### Testing from a terminal

Tests can be executed in a similar way. The following command will run all tests
in the repo.

```bash
$ bazelisk test //:all
```

There are hundreds of individual test targets, so it will take a few
minutes to run all of them. While developing, it's usually better to specify
the exact tests you're interested in.

```bash
bazelisk test //:$path_to_test_file
```

### Building from an IDE

See [Bazel IDE Integrations](https://docs.bazel.build/versions/master/ide.html).

### Running

Once the compiler has been built, the compiled JAR will be in the `bazel-bin/`
directory. You can access it with a call to `java -jar ...` or by using the
package.json script:

```bash
# java -jar bazel-bin/compiler_uberjar_deploy.jar [...args]
yarn compile [...args]
```

#### Running using Eclipse

1.  Open the class `src/com/google/javascript/jscomp/CommandLineRunner.java` or
    create your own extended version of the class.
2.  Run the class in Eclipse.
3.  See the instructions above on how to use the interactive mode - but beware
    of the
    [bug](https://stackoverflow.com/questions/4711098/passing-end-of-transmission-ctrl-d-character-in-eclipse-cdt-console)
    regarding passing "End of Transmission" in the Eclipse console.

## Contributing

### Contributor code of conduct

However you choose to contribute, please abide by our
[code of conduct](https://github.com/google/closure-compiler/blob/master/code_of_conduct.md) to
keep our community a healthy and welcoming place.

### Reporting a bug

1.  First make sure that it is really a bug and not simply the way that Closure
    Compiler works (especially true for ADVANCED_OPTIMIZATIONS).
    *   Check the
        [official documentation](https://developers.google.com/closure/compiler/)
    *   Consult the [FAQ](https://github.com/google/closure-compiler/wiki/FAQ)
    *   Search on
        [Stack Overflow](https://stackoverflow.com/questions/tagged/google-closure-compiler)
        and in the
        [Closure Compiler Discuss Group](https://groups.google.com/forum/#!forum/closure-compiler-discuss)
    *   Look through the list of
        [compiler assumptions](https://github.com/google/closure-compiler/wiki/Compiler-Assumptions).
2.  If you still think you have found a bug, make sure someone hasn't already
    reported it. See the list of
    [known issues](https://github.com/google/closure-compiler/issues).
3.  If it hasn't been reported yet, post a new issue. Make sure to add enough
    detail so that the bug can be recreated. The smaller the reproduction code,
    the better.

### Suggesting a feature

1.  Consult the [FAQ](https://github.com/google/closure-compiler/wiki/FAQ) to
    make sure that the behaviour you would like isn't specifically excluded
    (such as string inlining).
2.  Make sure someone hasn't requested the same thing. See the list of
    [known issues](https://github.com/google/closure-compiler/issues).
3.  Read up on
    [what type of feature requests are accepted](https://github.com/google/closure-compiler/wiki/FAQ#how-do-i-submit-a-feature-request-for-a-new-type-of-optimization).
4.  Submit your request as an issue.

### Submitting patches

1.  All contributors must sign a contributor license agreement (CLA). A CLA
    basically says that you own the rights to any code you contribute, and that
    you give us permission to use that code in Closure Compiler. You maintain
    the copyright on that code. If you own all the rights to your code, you can
    fill out an
    [individual CLA](https://code.google.com/legal/individual-cla-v1.0.html). If
    your employer has any rights to your code, then they also need to fill out a
    [corporate CLA](https://code.google.com/legal/corporate-cla-v1.0.html). If
    you don't know if your employer has any rights to your code, you should ask
    before signing anything. By default, anyone with an @google.com email
    address already has a CLA signed for them.
2.  To make sure your changes are of the type that will be accepted, ask about
    your patch on the
    [Closure Compiler Discuss Group](https://groups.google.com/forum/#!forum/closure-compiler-discuss)
3.  Fork the repository.
4.  Make your changes. Check out our
    [coding conventions](https://github.com/google/closure-compiler/wiki/Contributors#coding-conventions)
    for details on making sure your code is in correct style.
5.  Submit a pull request for your changes. A project developer will review your
    work and then merge your request into the project.

## Closure Compiler License

Copyright 2009 The Closure Compiler Authors.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.

## Dependency Licenses

### Rhino

<table>
  <tr>
    <td>Code Path</td>
    <td>
      <code>src/com/google/javascript/rhino</code>, <code>test/com/google/javascript/rhino</code>
    </td>
  </tr>

  <tr>
    <td>URL</td>
    <td>https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Rhino</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>1.5R3, with heavy modifications</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Netscape Public License and MPL / GPL dual license</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>A partial copy of Mozilla Rhino. Mozilla Rhino is an
implementation of JavaScript for the JVM.  The JavaScript parse tree data
structures were extracted and modified significantly for use by Google's
JavaScript compiler.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>The packages have been renamespaced. All code not
relevant to the parse tree has been removed. A JsDoc parser and static typing
system have been added.</td>
  </tr>
</table>

### Args4j

<table>
  <tr>
    <td>URL</td>
    <td>http://args4j.kohsuke.org/</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>2.33</td>
  </tr>

  <tr>
    <td>License</td>
    <td>MIT</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>args4j is a small Java class library that makes it easy to parse command line
options/arguments in your CUI application.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Guava Libraries

<table>
  <tr>
    <td>URL</td>
    <td>https://github.com/google/guava</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>31.0.1</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Apache License 2.0</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Google's core Java libraries.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### JSR 305

<table>
  <tr>
    <td>URL</td>
    <td>https://github.com/findbugsproject/findbugs</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>3.0.1</td>
  </tr>

  <tr>
    <td>License</td>
    <td>BSD License</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Annotations for software defect detection.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### JUnit

<table>
  <tr>
    <td>URL</td>
    <td>http://junit.org/junit4/</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>4.13</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Common Public License 1.0</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>A framework for writing and running automated tests in Java.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Protocol Buffers

<table>
  <tr>
    <td>URL</td>
    <td>https://github.com/google/protobuf</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>3.0.2</td>
  </tr>

  <tr>
    <td>License</td>
    <td>New BSD License</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Supporting libraries for protocol buffers,
an encoding of structured data.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### RE2/J

<table>
  <tr>
    <td>URL</td>
    <td>https://github.com/google/re2j</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>1.3</td>
  </tr>

  <tr>
    <td>License</td>
    <td>New BSD License</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Linear time regular expression matching in Java.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Truth

<table>
  <tr>
    <td>URL</td>
    <td>https://github.com/google/truth</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>1.1</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Apache License 2.0</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Assertion/Proposition framework for Java unit tests</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Ant

<table>
  <tr>
    <td>URL</td>
    <td>https://ant.apache.org/bindownload.cgi</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>1.10.11</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Apache License 2.0</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Ant is a Java based build tool. In theory it is kind of like "make"
without make's wrinkles and with the full portability of pure java code.</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### GSON

<table>
  <tr>
    <td>URL</td>
    <td>https://github.com/google/gson</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>2.9.1</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Apache license 2.0</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>A Java library to convert JSON to Java objects and vice-versa</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Node.js Closure Compiler Externs

<table>
  <tr>
    <td>Code Path</td>
    <td><code>contrib/nodejs</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>https://github.com/dcodeIO/node.js-closure-compiler-externs</td>
  </tr>

  <tr>
    <td>Version</td>
    <td>e891b4fbcf5f466cc4307b0fa842a7d8163a073a</td>
  </tr>

  <tr>
    <td>License</td>
    <td>Apache 2.0 license</td>
  </tr>

  <tr>
    <td>Description</td>
    <td>Type contracts for NodeJS APIs</td>
  </tr>

  <tr>
    <td>Local Modifications</td>
    <td>Substantial changes to make them compatible with NpmCommandLineRunner.</td>
  </tr>
</table>
