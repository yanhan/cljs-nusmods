#!/bin/bash

# Remove globals file required for running tests
rm -f test/js/globals.js

# Generate fresh globals file
node test_helpers/gen_module_types_for_test.js >test/js/globals.js

# Compile ClojureScript into JavaScript
lein cljsbuild clean
lein cljsbuild once
