#
# Benchmark suite
#

all: subjects reports


SUBJECTS ?= https://unpkg.com/react@18.1.0/cjs/react.development.js \
		https://unpkg.com/lodash@4.17.21/lodash.js \
		https://unpkg.com/jquery@3.6.0/dist/jquery.js

TARGETS ?= react.development \
		lodash \
		jquery

REPORTS = $(patsubst %,%.jvm.min.js,$(TARGETS)) $(patsubst %,%.native.min.js,$(TARGETS))

WGET_ARGS ?= -q --progress=dot


reports: subjects  ## Generate benchmark reports.
	@echo "Benchmarking Closure Compiler (JVM vs. Native)..."
	@for target in $(TARGETS); do python3 bench.py "$$target"; done

subjects:  ## Download subject test files.
	@echo "Downloading test bundles..."
	@mkdir subjects
	@cd subjects && for subj in $(SUBJECTS) ; do \
		echo "- Downloading '$$subj'..." && \
		wget $(WGET_ARGS) $$subj; \
		done

clean:  ## Clean benchmark reports and subject test files.
	@echo "Cleaning subjects..."
	@rm -fr subjects
	@echo "Cleaning reports..."
	@rm -fr reports

.PHONY: clean

