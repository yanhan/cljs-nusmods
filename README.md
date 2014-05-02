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
- `lessonTypes.json`. A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/lessonTypes.json

Place these JSON files at the root directory of this repository and
generate a `processed_modules.json` file:

    node helpers/normalize_modules_json.js

Using the `processed_modules.json` file, we generate the `modinfo.js` and
`auxmodinfo.js` files (which are used by the timetable builder code), and copy
them to the `resources/public/js/` directory (these files hold compacted module
information):

    node helpers/convert_modules.js
    cp modinfo.js auxmodinfo.js resources/public/js/

### Building minified libraries

Execute this command to build the minified libraries:

    gulp

The default gulp task does the following:

- Concatenates all Exhibit 3.0 JavaScript files and minifies it, then copies it
to `resources/public/js/vendor/exhibit3-all.min.js`
- Concatenates all Exhibit 3.0 CSS files and minifies it, then copies it to
`resources/public/css/exhibit3-styles.min.css`
- Copies the `images` folder and the English locale file of the Exhibit 3.0
library and places them under the same directory hierarchy at
`resources/public/exhibit3/`

## Running the web server

    lein ring server-headless

And go to http://127.0.0.1:3000/index.html

## Running Tests

To build the test files:

    test_helpers/build_test.sh

Running the tests:

    lein cljsbuild test

## Credits

First and foremost, to [Eu Beng Hee](https://github.com/ahbeng) for making
his wonderful [NUSMods](https://github.com/ahbeng/NUSMods) library open source.
This project would probably have taken a very different route without using it
as a reference.

### Libraries Used

This project will probably be impossible without the following awesome
libraries (even though the documentation may not be so awesome for some of
them):

#### JavaScript libraries (front end):
- [jQuery](http://jquery.com/)
- [jQuery UI](https://jqueryui.com/)
- [lodash](http://lodash.com/)
(more modern version of [Underscore.js](http://underscorejs.org/); use this and
you may very well not see any `for` loops in your JavaScript for a start)
- [Exhibit 3.0](http://www.simile-widgets.org/exhibit3/)
(**THE** main library in the Module Finder page; documentation can be hard to
find)
- [Select 2](http://ivaynberg.github.io/select2/)
(For the `Select Modules for Timetable` input box)

#### CSS libraries / tools:
- [Zurb Foundation 5](http://foundation.zurb.com/)
- [Compass](http://compass-style.org/)
(Enables us to write SCSS instead of CSS, huge boon)

#### JavaScript libraries (back end):
- [gulp.js](http://gulpjs.com/) (Making Grunt obsolete, kind of)
- [gulp-concat](https://github.com/wearefractal/gulp-concat)
- [gulp-uglify](https://www.npmjs.org/package/gulp-uglify)
- [gulp-minify-css](https://github.com/jonathanepollack/gulp-minify-css)
- [Moment.js](http://momentjs.com/)
- [minimist](https://github.com/substack/minimist)
