#!/usr/bin/env python3

import sys, os, time


defaultIterations = 1

COMMAND_JVM_TARGET = "//:compiler_unshaded"
COMMAND_NATIVE_TARGET = "//:compiler_native"

COMMAND_CLOSURE_SIMPLE = [
  "bazel",
  "run",
  "--ui_event_filters=-info,-stdout,-stderr",
  "--noshow_progress",
  "%binary%",
  "--",
  "--compilation_level=SIMPLE",
  "--js=%input%",
  "--js_output_file=%output%",
  "2>",
  "/dev/null",
]


def render_command_segment(seg, target, bundle, target_label):
  return (
    seg.replace("%binary%", target)
      .replace("%input%", "$(bazel info workspace)/native/bench/subjects/%s.js" % bundle)
      .replace("%output%", "$(bazel info workspace)/native/bench/reports/%s.%s.min.js" % (bundle, target_label))
  )


def compile_command(target, target_label, bundle, args = []):
  base_args = [i for i in map(lambda i: render_command_segment(i, target, bundle, target_label), COMMAND_CLOSURE_SIMPLE)]
  base_args += args
  return base_args

def run_report(bundle, jvm, native):
  print("""

| Bundle: %s
|
| Build times:
| - JVM:    %s
| - Native: %s
| -------------------
| Diff:     %s (%s)

  """ % (
    bundle,
    str(round(jvm)) + "ms",
    str(round(native)) + "ms",
    str(round(jvm - native)) + "ms",
    ((native < jvm) and "+" or "-") +  str(round(100 - ((native/jvm) * 100))) + "%",
  ))

def run_compile(target, target_label, bundle, args = [], iterations = defaultIterations):
  command = compile_command(target, target_label, bundle, args)
  joined = " ".join(command)
  print("- Running %s build for '%s' (iterations: %s)..." % (target_label, bundle, iterations))
  all_measurements = []
  for i in range(0, iterations):
    start = round(time.time() * 1000)
    os.system(" ".join(command))
    end = round(time.time() * 1000)
    all_measurements.append(end - start)
  return sum(all_measurements) / iterations

def run_benchmark(bundle, variant = "SIMPLE", args = []):
  print("\nBenchmarking bundle '%s' (variant: '%s')..." % (bundle, variant))
  jvm_time = run_compile(
    COMMAND_JVM_TARGET,
    "jvm",
    bundle,
    args
  )
  native_time = run_compile(
    COMMAND_NATIVE_TARGET,
    "native",
    bundle,
    args
  )
  run_report(
    bundle,
    jvm_time,
    native_time,
  )

def run_bench(bundle):
  """Run a JVM copy of the Closure Compiler and compare it with a Native copy built with GraalVM."""
  run_benchmark(
    bundle,
  )

if __name__ == "__main__":
  if len(sys.argv) < 2:
    print("Please provide bundle name to test")
    sys.exit(2)
  run_bench(sys.argv[1])

