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

"""Expands a template file with support for reading --defines.

Values of substitutions are first formatted with --define values
before being substituted into the template file. Formatting uses
the Python str.format() syntax.

Args:
  name: The name of the rule.
  template: The template file to expand
  out: The destination of the expanded file
  substitutions: A dictionary mapping strings to their substitutions
  defines: A dictionary listing the defines to format with and their default values.
  is_executable: A boolean indicating whether the output file should be executable
"""

def _expand_template_with_defines_impl(ctx):
    defines = {
        k: ctx.var.get(k, v)
        for k, v in ctx.attr.defines.items()
    }
    substitutions = {
        k: v.format(**defines)
        for k, v in ctx.attr.substitutions.items()
    }

    ctx.actions.expand_template(
        template = ctx.file.template,
        output = ctx.outputs.out,
        substitutions = substitutions,
        is_executable = ctx.attr.is_executable,
    )

expand_template_with_defines = rule(
    implementation = _expand_template_with_defines_impl,
    attrs = {
        "template": attr.label(mandatory = True, allow_single_file = True),
        "substitutions": attr.string_dict(mandatory = True),
        "defines": attr.string_dict(mandatory = True),
        "out": attr.output(mandatory = True),
        "is_executable": attr.bool(default = False, mandatory = False),
        "data": attr.label_list(allow_files = True),
    },
)
