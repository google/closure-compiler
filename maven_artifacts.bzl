"""List of external dependencies from Maven."""

MAVEN_ARTIFACTS = [
    "args4j:args4j:2.33",
    "com.google.code.gson:gson:2.9.1",
    "com.google.errorprone:error_prone_annotations:2.15.0",
    "com.google.guava:failureaccess:1.0.1",
    "com.google.guava:guava:32.1.2-jre",
    "com.google.guava:guava-testlib:32.1.2-jre",
    "com.google.jimfs:jimfs:1.2",
    "com.google.protobuf:protobuf-java:3.21.12",
    "com.google.re2j:re2j:1.3",
    "com.google.truth.extensions:truth-liteproto-extension:1.1",
    "com.google.truth.extensions:truth-proto-extension:1.1",
    "io.github.java-diff-utils:java-diff-utils:4.12",
    "org.apache.ant:ant:1.10.11",
    "org.jspecify:jspecify:0.3.0",
]

# Note the added "@" after version tag to make easier to extract the root url
ORDERED_POM_OR_GRADLE_FILE_LIST = [
    "https://github.com/kohsuke/args4j/blob/args4j-site-2.33@/args4j/pom.xml",
    "https://github.com/google/gson/blob/gson-parent-2.9.1@/gson/pom.xml",
    "https://github.com/google/error-prone/blob/v2.15.0@/annotations/pom.xml",
    "https://github.com/google/guava/blob/failureaccess-v1.0.1@/futures/failureaccess/pom.xml",
    "https://github.com/google/guava/blob/v32.1.2@/guava/pom.xml",
    "https://github.com/google/guava/blob/v32.1.2@/guava-testlib/pom.xml",
    "https://github.com/google/jimfs/blob/v1.2@/jimfs/pom.xml",
    "https://github.com/protocolbuffers/protobuf/blob/v3.21.12@/java/core/pom.xml",
    "https://github.com/google/re2j/blob/re2j-1.3@/build.gradle",
    "https://github.com/google/truth/blob/release_1_1@/extensions/liteproto/pom.xml",
    "https://github.com/google/truth/blob/release_1_1@/extensions/proto/pom.xml",
    "https://github.com/java-diff-utils/java-diff-utils/blob/java-diff-utils-parent-4.12@/java-diff-utils/pom.xml",
    "https://github.com/apache/ant/blob/rel/1.10.11@/src/etc/poms/ant/pom.xml",
    "https://github.com/jspecify/jspecify/blob/v0.3.0@/gradle/publish.gradle",
]
