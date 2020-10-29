load("//tools/build_defs/js:rules.bzl", "js_binary")

def serialized_ast_file(name, ordered_srcs = []):
    """Creates a single serialized AST file from compiling all of the input files."""
    jsast = name
    binary_name = name + "_bin"
    js_binary(
        name = binary_name,
        compiler = "//javascript/tools/jscompiler:head",
        compile = 1,
        defs = [
            "--language_out=NO_TRANSPILE",
            "--typed_ast_output_file__experimental__DO_NOT_USE=%s" % jsast,
        ],
        include_default_externs = "off",
        extra_outputs = [jsast],
        srcs = ordered_srcs,
    )

def per_file_serialized_asts(name, ordered_srcs = []):
    """Creates a serialized AST file corresponding to each of the input files"""
    ijs_files = []
    ast_files = []
    for src in ordered_srcs:
        js_binary(
            name = src + ".i",
            compiler = "//javascript/tools/jscompiler:head",
            defs = ["--incremental_check_mode=GENERATE_IJS"],
            include_default_externs = "off",
            srcs = [src],
            # Due to b/131758317, the manifest generation uses :default with the head flags, which break
            # binaries using new flags with :head. Ban this binary from TAP to workaround.
            tags = ["notap"],
        )

        serialized_ast_file(
            name = src + ".jsast",
            ordered_srcs = ijs_files + [src],
        )

        ijs_files.append(src + ".i.js")
        ast_files.append(src + ".jsast")

    return ast_files
