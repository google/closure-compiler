"""Bazel declarations to run a native binary copy of Closure Compiler in downstream Bazel projects."""


# sample link (for `_bindist("1.0.0", "darwin", "arm64")`):
# https://github.com/sgammon/closure-compiler/releases/download/v1.0.0/compiler_native_1.0.0_darwin_arm64.tar.gz
def _bindist(version, os, arch):
    return "https://github.com/sgammon/closure-compiler/releases/download/v%s/compiler_native_v%s_%s_%s.tar.gz" % (
        version,
        version,
        os,
        arch
    )

# sample bundle (for `_bindist_bundle("1.0.0", "linux", archs = ["arm64", "s390x"])`):
# "linux": {
#     "arm64": _bindist("1.0.0", "linux", "arm64"),
#     "s390x": _bindist("1.0.0", "linux", "s390x"),
# },
def _bindist_bundle(version, os, archs = []):
    return dict([
        (arch, _bindist(version, os, arch))
        for arch in archs
    ])

# sample version: (for `_bindist_bundle("1.0.0", bundles = {"darwin": ["arm64"], "linux": ["arm64"]})`)
# "1.8.6": {
#     "linux": {
#         "arm64": _bindist("1.0.0", "linux", "arm64"),
#     },
#     "darwin": {
#         "arm64": _bindist("1.0.0", "darwin", "arm64"),
#     },
# },
def _bindist_version(version, bundles = {}):
    return dict([
        (os, _bindist_bundle(version, os, archs))
        for os, archs in bundles.items()
    ])


# version checksums (static)
_compiler_version_checksums = {
    "v20220612_darwin_arm64": "1a787ec3a242e19589b041586dd487c13daa4dc0c19afb846cb31128f7606d87",
    "v20220612_linux_amd64": "71d9488a6e3bf536e80b9cf74d353c8c42b7883d9d54de742152908390cbba0b",
}

# version configs (static)
_compiler_version_configs = {
    "v20220612": _bindist_version(
        version = "20220612",
        bundles = {
            "darwin": ["arm64"],
        },
    ),
}

_compiler_latest_version = "v20220612"

def _get_platform(ctx):
    res = ctx.execute(["uname", "-p"])
    arch = "amd64"
    if res.return_code == 0:
        uname = res.stdout.strip()
        if uname == "arm":
            arch = "arm64"
        elif uname == "aarch64":
            arch = "aarch64"

    if ctx.os.name == "linux":
        return ("linux", arch)
    elif ctx.os.name == "mac os x":
        if arch == "arm64" or arch == "aarch64":
            return ("darwin", "arm64")
        return ("darwin", "x86_64")
    else:
        fail("Unsupported operating system: " + ctx.os.name)

def _compiler_bindist_repository_impl(ctx):
    platform = _get_platform(ctx)
    version = ctx.attr.version

    # resolve dist
    config = _compiler_version_configs[version]
    link = config[platform[0]][platform[1]]
    sha = _compiler_version_checksums["%s_%s_%s" % (version, platform[0], platform[1])]

    urls = [link]
    ctx.download_and_extract(
        url = urls,
        sha256 = sha,
    )

    ctx.file("BUILD", """exports_files(glob(["**/*"]))""")
    ctx.file("WORKSPACE", "workspace(name = \"{name}\")".format(name = ctx.name))


closure_compiler_bindist_repository = repository_rule(
    attrs = {
        "version": attr.string(
            mandatory = True,
            default = _compiler_latest_version,
        ),
    },
    implementation = _compiler_bindist_repository_impl,
)
