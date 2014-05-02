# [Google Closure Compiler](https://developers.google.com/closure/compiler/)

The [Closure Compiler](https://developers.google.com/closure/compiler/) performs
checking, instrumentation, and optimizations on JavaScript code.

## Quick Start

1. Ensure that you have [Java 7](http://www.java.com/) installed.

2. Download the [Closure Compiler]
(https://code.google.com/p/closure-compiler/wiki/BinaryDownloads?tm=2).

3. Run the following command from the command line to compile a JavaScript file:
   
   ```
   java -jar compiler.jar --js hello.js --js_output_file hello-compiled.js
   ```

Read the [documentation]
(http://code.google.com/closure/compiler/docs/gettingstarted_app.html) to learn
more about how to use the Closure Compiler.

## Building it Yourself

Note: The Closure Compiler requires [Java 7 or higher](http://www.java.com/).

### Using [Ant](http://ant.apache.org/)

1. Download the [Ant build tool](http://ant.apache.org/bindownload.cgi).

2. At the root of the source tree, there is an Ant file named ```build.xml```.
   To use it, navigate to the same directory and type the command

    ```
    ant jar
    ```

    This will produce a jar file called ```build/compiler.jar```.

### Using [Eclipse](http://www.eclipse.org/)

1. Download and open the [Eclipse IDE](http://www.eclipse.org/).
2. Navigate to ```File > New > Project ...``` and create a Java Projet. Give
   the project a name.
3. Select ```Create project from existing source``` and choose the root of the
   checked-out source tree as the existing directory.
3. Navigate to the ```build.xml``` file. You will see all the build rules in
   the Outline pane. Run the ```jar``` rule to build the compiler in
   ```build/compiler.jar```.

## Running

On the command line, type

```
java -jar compiler.jar
```

This starts the compiler in interactive mode. Type

```javascript
var x = 17 + 25;
```

then hit "Enter", then hit "Ctrl-Z" (on Windows) or "Ctrl-D" (on Mac or Linux)
and "Enter" again. The Compiler will respond:

```javascript
var x=42;
```

The Closure Compiler has many options for reading input from a file, writing
output to a file, checking your code, and running optimizations. To learn more,
type

```
java -jar compiler.jar --help
```

More detailed information about running the Closure Compiler is available in the
[documentation](http://code.google.com/closure/compiler/docs/gettingstarted_app.html).

## Compiling Multiple Scripts

If you have multiple scripts, you should compile them all together with one
compile command.

```
java -jar compiler.jar --js=in1.js --js=in2.js ... --js_output_file=out.js
```

The Closure Compiler will concatenate the files in the order they're passed at
the command line.

If you need to compile many, many scripts together, you may start to run into
problems with managing dependencies between scripts. In this case, you should
use the [Closure Library](https://developers.google.com/closure/library/). It
contains functions for enforcing dependencies between scripts, and a tool
called ````calcdeps.py```` that knows how to give scripts to the Closure Compiler
in the right order.

## Closure Compiler License

Copyright 2009 The Closure Compiler Authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

## Dependency Licenses

### Rhino

Two copies of Rhino exist in this repository:

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/rhino</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://www.mozilla.org/rhino</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>Trunk</td>
  </tr>
  
  <tr>
    <td>License</td>
    <td>Netscape Public License and MPL / GPL dual license</td>
  </tr>
  
  <tr>
    <td>Description</td>
    <td>Mozilla Rhino is an implementation of JavaScript for the JVM.</td>
  </tr>
  
  <tr>
    <td>Local Modifications</td>
    <td>Minor changes to parsing JSDoc that usually get pushed
  up-stream to Rhino trunk.</td>
  </tr>
</table>

<table>
  <tr>
    <td>Code Path</td>
    <td>
      <code>src/com/google/javascript/rhino</code>, <code>test/com/google/javascript/rhino</code>
    </td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://www.mozilla.org/rhino</td>
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
implementation of JavaScript for the JVM.  The JavaScript parser and
the parse tree data structures were extracted and modified
significantly for use by Google's JavaScript compiler.</td>
  </tr>
  
  <tr>
    <td>Local Modifications</td>
    <td>The packages have been renamespaced. All code not
relevant to parsing has been removed. A JsDoc parser and static typing
system have been added.</td>
  </tr>
</table>

### Args4j

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/args4j.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>https://args4j.dev.java.net/</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>2.0.26</td>
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
    <td>Code Path</td>
    <td><code>lib/guava.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://code.google.com/p/guava-libraries/</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>17.0</td>
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
    <td>Code Path</td>
    <td><code>lib/jsr305.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://code.google.com/p/jsr-305/</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>svn revision 47</td>
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

### Jar Jar Links

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/jarjar.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://jarjar.googlecode.com/</td>
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
    <td>A utility for repackaging Java libraries.</td>
  </tr>
  
  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### JUnit

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/junit.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://sourceforge.net/projects/junit/</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>4.11</td>
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
    <td>Code Path</td>
    <td><code>lib/protobuf-java.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://code.google.com/p/protobuf/</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>2.5.0</td>
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

### Ant

<table>
  <tr>
    <td>Code Path</td>
    <td>
      <code>lib/ant.jar</code>, <code>lib/ant-launcher.jar</code>
    </td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://ant.apache.org/bindownload.cgi</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>1.8.1</td>
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

### JSON

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/json.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://json.org/java/index.html</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>JSON version 20090211</td>
  </tr>
  
  <tr>
    <td>License</td>
    <td>MIT license</td>
  </tr>
  
  <tr>
    <td>Description</td>
    <td>JSON is a set of java files for use in transmitting data in JSON format.</td>
  </tr>
  
  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Mockito

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/mockito-core.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>https://code.google.com/p/mockito</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>1.9.5</td>
  </tr>
  
  <tr>
    <td>License</td>
    <td>MIT license</td>
  </tr>
  
  <tr>
    <td>Description</td>
    <td>Mockito is an open source testing framework for Java. The framework allows the
creation of Test Double objects (called "Mock Objects") in automated unit tests
for the purpose of Test-driven Development (TDD) or Behavior Driven Development
(BDD).</td>
  </tr>
  
  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>

### Objenesis

<table>
  <tr>
    <td>Code Path</td>
    <td><code>lib/objenesis.jar</code></td>
  </tr>

  <tr>
    <td>URL</td>
    <td>http://objenesis.org</td>
  </tr>
  
  <tr>
    <td>Version</td>
    <td>1.2</td>
  </tr>
  
  <tr>
    <td>License</td>
    <td>Apache 2.0 license</td>
  </tr>
  
  <tr>
    <td>Description</td>
    <td>Depended by lib/mockito-core.jar, not used directly.</td>
  </tr>
  
  <tr>
    <td>Local Modifications</td>
    <td>None</td>
  </tr>
</table>
