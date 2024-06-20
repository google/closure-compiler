"""
List of external dependencies from Maven.

Make sure this file only uses two lists, and two lists only.
They are required to be of the same size at all times.
"""

MAVEN_ARTIFACTS = [
    "args4j:args4j:2.33",
    "com.google.code.gson:gson:2.9.1",
    "com.google.errorprone:error_prone_annotations:2.15.0",
    "com.google.guava:failureaccess:1.0.1",
    "com.google.guava:guava:33.0.0-jre",
    "com.google.guava:guava-testlib:33.0.0-jre",
    "com.google.jimfs:jimfs:1.2",
    "com.google.protobuf:protobuf-java:3.25.2",
    "com.google.re2j:re2j:1.3",
    "com.google.truth.extensions:truth-liteproto-extension:1.4.0",
    "com.google.truth.extensions:truth-proto-extension:1.4.0",
    "io.github.java-diff-utils:java-diff-utils:4.12",
    "org.apache.ant:ant:1.10.11",
    "org.jspecify:jspecify:0.3.0",
]

ORDERED_POM_OR_GRADLE_FILE_LIST = [
    "https://github.com/kohsuke/args4j/blob/master/args4j/pom.xml",
    "https://github.com/google/gson/blob/main/gson/pom.xml",
    "https://github.com/google/error-prone/blob/master/annotations/pom.xml",
    "https://github.com/google/guava/blob/master/futures/failureaccess/pom.xml",
    "https://github.com/google/guava/blob/master/guava/pom.xml",
    "https://github.com/google/guava/blob/master/guava-testlib/pom.xml",
    "https://github.com/google/jimfs/blob/master/jimfs/pom.xml",
    "https://github.com/protocolbuffers/protobuf/blob/main/java/core/pom.xml",
    "https://github.com/google/re2j/blob/master/build.gradle",
    "https://github.com/google/truth/blob/master/extensions/liteproto/pom.xml",
    "https://github.com/google/truth/blob/master/extensions/proto/pom.xml",
    "https://github.com/java-diff-utils/java-diff-utils/blob/master/java-diff-utils/pom.xml",
    "https://github.com/apache/ant/blob/master/src/etc/poms/ant/pom.xml",
    "https://github.com/jspecify/jspecify/blob/main/gradle/publish.gradle",
]
