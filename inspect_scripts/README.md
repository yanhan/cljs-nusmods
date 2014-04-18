A series of scripts to inspect the properties, values and types of values in
the `modules.json` file from http://api.nusmods.com/

A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/modules.json

## System Requirements

- [node](http://nodejs.org/)

## Setup

You will need a copy of the `modules.json` from http://api.nusmods.com/

Place that in the `inspect_scripts` directory. One such `modules.json` file is
http://api.nusmods.com/2013-2014/2/modules.json

We make use of some libraries that need to be installed using:

    npm install

## Overview of each file

### types.js

Provides a high level overview each module object in the `modules.json` file.
Prints the names of the properties and type of values in each module object to
stdout.

To run it:

    node inspect_scripts/types.js

### key_frequency.js

Shows how often a key (such as `ModuleCode`, `ModuleTitle`) appears in the set
of all module objects.

To run it:

    node inspect_scripts/key_frequency.js

### value_frequency.js

For every key in a module, gives a summary of the number of values that appear
X times, so we can decide a good strategy to pick for data representation to
minimize the eventual `modules.json` file size.

**NOTE:** Currently this only works for a subset of keys whose values are
strings.

To run it:

    node inspect_scripts/value_frequency.js
