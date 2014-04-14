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

## Overview of each file

### types.js

Provides a high level overview each module object in the `modules.json` file.
Prints the names of the properties and type of values in each module object to
stdout.

To run it:

    node inspect_types/types.js
