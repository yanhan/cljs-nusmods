# cljs-nusmods

ClojureScript implementation of [NUSMods](http://nusmods.com)

**NOTE:** Currently this project does nothing. But soon it will do something.

**NOTE:** This is purely a personal project done for the sake of challenging
myself, since it's something I have wanted to do for quite some time.
Recently I've wanted to learn some ClojureScript, and what better way to dive
into it with an actual project?

For actual usage, I strongly recommend you use [NUSMods](http://nusmods.com).
It is less buggy and contains a lot more features.

## System requirements

- lein
- node
- rvm

## Setup

Install the Ruby gems in the `Gemfile`:

    bundle install

Install the necessary nodejs libraries:

    npm install

## Building

For generating CSS from SCSS:

    sass --watch src/scss:resources/public/css

### Generating module information

We will need several files from http://api.nusmods.com/

- `modules.json`. A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/modules.json
- `facultyDepartments.json` A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/facultyDepartments.json

Place these JSON files at the root directory of this repository and
generate a `processed_modules.json` file:

    node helpers/normalize_modules_json.js

Using the `processed_modules.json` file, we generate the `modinfo.js` and
`auxmodinfo.js` files (which are used by the timetable builder code), and copy
them to the `resources/public/js/` directory (these files hold compacted module
information):

    node helpers/convert_modules.js
    cp modinfo.js auxmodinfo.js resources/public/js/

## Running the web server

    lein ring server-headless

And go to http://127.0.0.1:3000/index.html
