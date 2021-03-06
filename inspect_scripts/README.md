About
-----

A series of scripts to inspect the properties, values and types of values in
the `modules.json` file from http://api.nusmods.com/

A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/modules.json

## System Requirements

- [node](http://nodejs.org/)

## Setup

### Installing node.js libraries

We make use of some libraries that need to be installed using:

    npm install

### Required files from api.nusmods.com

Run the `helpers/dl-api-nusmods-stuff.sh` script to download the required files
from http://api.nusmods.com . The files will be downloaded to the
`api-nusmods-stuff` folder:

    helpers/dl-api-nusmods-stuff.sh

## Overview of each file

### types.js

Provides a high level overview each module object in the
`api-nusmods-stuff/modules.json` file.
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
minimize the eventual module data file size.

**NOTE:** Currently this only works for a subset of keys whose values are
strings.

To run it:

    node inspect_scripts/value_frequency.js

### values_of_keys.js

Shows all possible values for a key of a Module object in
`api-nusmods-stuff/modules.json`. We use this to inspect certain keys of Module
objects to derive more space efficient data representations.

To run it:

    node inspect_scripts/values_of_keys.js

To inspect the values of keys of Module objects in the
`build-temp/processed_modules.json` file (generated by the
`helpers/normalize_modules_json.js` script), supply the `-p` flag like so:

    node inspect_scripts/values_of_keys.js -p

### exam_date.js

Compares 3 different data storage representations for the `ExamDate` field of a
Module object to determine the one with better space efficiency.

To run it:

    node inspect_scripts/exam_date.js

### module_code_format.js

Inspects all `ModuleCode` values to see if they follow this format:

    <1 or more non digit chars><4 digit chars><0 or more non digit chars>

and print out the `ModuleCode`(s) which do not follow the format.

To run it:

    node inspect_scripts/module_code_format.js

### classno_format.js

Checks if all the `.ClassNo` attribute of Lesson objects match a given regex.

To run it:

    node inspect_scripts/classno_format.js

### no_lessons_maybe_exam.js

Retrieves a list of modules without lessons, then splits them into 2 lists, one
with exams and one without exams, and dumps the module codes to stdout.

To run it:

    node inspect_scripts/no_lessons_maybe_exams.js
