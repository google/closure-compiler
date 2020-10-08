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

"""Creates an "Artifact Bundle" (JAR file) for deploying to Maven/Sonatype OSSRH.

This rule requires that gpg be installed locally. The signing
key will be read implicitly from the system. The key passphrase
will be read from --define=CLEARTEXT_GPG_PASSPHRASE, so
DO NOT USE A TRULY SECRET PASSWORD.

Artifact Bundle Docs:
    https://help.sonatype.com/repomanager2/staging-releases/artifact-bundles
    https://central.sonatype.org/pages/manual-staging-bundle-creation-and-deployment.html#bundle-creation

Args:
    pom: (*.xml) The POM to use in this bundle. Can have any name. <version> will be
        replaced with --define=COMPILER_VERSION.
    artifact_id: (string) Maven artifact ID of bundle contents
    jar: (*.jar) The primary component; compiled code.
    javadoc: (*.jar)
    sources: (*.jar)

Returns:
    <artifact_id>_bundle.jar: The bundle JAR
"""

_SNAPSHOT = "1.0-SNAPSHOT"

def _sonatype_artifact_bundle(ctx):
    version = ctx.var.get("COMPILER_VERSION", _SNAPSHOT)
    password = None
    generate_signatures = True

    if version == _SNAPSHOT:
        # A SNAPSHOT version can't be uploaded as a release
        # and thus doesn't need to be signed
        generate_signatures = False
    elif not version.startswith("v") or not version[1:].isdigit():
        fail("--define=COMPILER_VERSION was malformed; got '{0}'".format(version))
    else:
        password = _fail_missing_define(ctx, "CLEARTEXT_GPG_PASSPHRASE")

    # 1. Rename the POM to have the mandatory base name "pom.xml"
    # 2. Confirm the POM is for the right artifact
    # 3. Swap in the correct version number
    updated_pom = _declare_file(ctx, "pom.xml")
    ctx.actions.run_shell(
        outputs = [updated_pom],
        inputs = [ctx.file.pom],
        command = """
            if ! grep -Fq '<artifactId>{0}</artifactId>' '{3}'; then
              echo '{3}: Could not find expected artifact ID' && exit 1;
            fi
            if ! grep -Fq '<version>{1}</version>' '{3}'; then
              echo '{3}: Could not find version tag' && exit 1;
            fi

            cat {3} | sed -E 's#<version>{1}</version>#<version>{2}</version>#g' > '{4}';
        """.format(
            ctx.attr.artifact_id,
            _SNAPSHOT,
            version,
            ctx.file.pom.path,
            updated_pom.path,
        ),
        mnemonic = "UpdatePOM",
    )

    jar_map = {
        "": ctx.file.jar,
        "-javadoc": ctx.file.javadoc,
        "-sources": ctx.file.sources,
    }
    srcs = [updated_pom] + [
        _copy_file(ctx, file, "{0}-{1}{2}.jar".format(ctx.attr.artifact_id, version, suffix))
        for suffix, file in jar_map.items()
        if file
    ]
    files_to_bundle = srcs
    if generate_signatures:
        signatures = [_gpg_signature(ctx, password, f) for f in srcs]
        files_to_bundle += signatures

    # Set all bundle files to be at the top level of the JAR.
    bundle_file_args = []
    for file in files_to_bundle:
        bundle_file_args += ["-C", file.dirname, file.basename]

    java_home = str(ctx.attr._jdk[java_common.JavaRuntimeInfo].java_home)
    bundle = ctx.actions.declare_file("{0}_bundle.jar".format(ctx.attr.artifact_id))
    ctx.actions.run_shell(
        outputs = [bundle],
        inputs = files_to_bundle,
        command = java_home + "/bin/jar cf $@",
        arguments = [bundle.path] + bundle_file_args,
        mnemonic = "SonatypeBundle",
    )

    return [
        DefaultInfo(
            files = depset([bundle]),
            data_runfiles = ctx.runfiles(files = [bundle]),
        ),
    ]

sonatype_artifact_bundle = rule(
    implementation = _sonatype_artifact_bundle,
    attrs = {
        "pom": attr.label(allow_single_file = [".xml"], mandatory = True),
        "artifact_id": attr.string(mandatory = True),
        "jar": attr.label(allow_single_file = [".jar"]),
        "javadoc": attr.label(allow_single_file = [".jar"]),
        "sources": attr.label(allow_single_file = [".jar"]),
        "_jdk": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            providers = [java_common.JavaRuntimeInfo],
        ),
    },
)

def _gpg_signature(ctx, password, src):
    signature = _declare_file(ctx, src.basename + ".asc")
    ctx.actions.run_shell(
        outputs = [signature],
        inputs = [src],
        command = """
          gpg \
            --detach-sign --output='{0}' \
            --batch --pinentry-mode=loopback --passphrase='{1}' \
            --sign '{2}'
        """.format(
            signature.path,
            password,
            src.path,
        ),
        execution_requirements = {
            "local": "Ensure signing happens on the local machine.",
        },
        mnemonic = "LocalSignGPG",
    )

    return signature

def _copy_file(ctx, file, name):
    copy = _declare_file(ctx, name)
    ctx.actions.run_shell(
        outputs = [copy],
        inputs = [file],
        command = "cp $@",
        arguments = [
            file.path,
            copy.path,
        ],
    )

    return copy

def _declare_file(ctx, name):
    return ctx.actions.declare_file("{0}/{1}".format(ctx.attr.name, name))

def _fail_missing_define(ctx, name):
    value = ctx.var.get(name, None)
    if value == None:
        fail(ctx.label, "--define={0} was not set".format(name))
    return value
