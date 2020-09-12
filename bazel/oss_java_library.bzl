# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""See oss_jar_set"""

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

def oss_java_library(
        name,
        shared_attrs,
        java_attrs,
        javadoc_attrs):
    """A set of JARs for OSS release.

    Args:
        name: (string)
        shared_attrs: (Dict) Args to all libraries
        java_attrs: (Dict) Args to java_library or java_binary, overriding shared_attrs
        javadoc_attrs: (Dict) Args to javadoc_library, overriding shared_attrs

    Generates:
        <name>: (java_library|java_binary)
        <name>.javadoc: (javadoc_library)
        <name>.sources: (sources JAR)
    """
    native.java_library(
        name = name,
        **_copy_and_merge(shared_attrs, java_attrs)
    )

    javadoc_library(
        name = name + ".javadoc",
        **_copy_and_merge(shared_attrs, javadoc_attrs)
    )

    native.alias(
        name = name + ".sources",
        actual = "lib{0}-src.jar".format(name),
    )

def _copy_and_merge(to_copy, to_merge):
    copy = dict(to_copy)
    copy.update(to_merge)
    return copy
