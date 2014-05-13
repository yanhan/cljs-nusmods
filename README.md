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

### Ruby Gems

Install the Ruby gems in the `Gemfile`:

    bundle install

### Node.js libraries

Install the necessary nodejs libraries:

    npm install

### Files from http://api.nusmods.com

Execute the following commands to download some required files from
http://api.nusmods.com to the `api-nusmods-stuff` folder:

    helpers/dl-api-nusmods-stuff.sh

The required files are the following:

- `modules.json`. A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/modules.json
- `facultyDepartments.json`. A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/facultyDepartments.json
- `lessonTypes.json`. A concrete example of such a file is
http://api.nusmods.com/2013-2014/2/lessonTypes.json

## Building everything

Execute the following command:

    gulp

This is a rather expensive operation due primarily to the minification step and
ClojureScript compilation with advanced optimizations.

Check out the "**Some individual build tasks explained**" section below for
finer grained builds.

## Running the web server

    lein ring server-headless

And go to http://127.0.0.1:3000/index.html

## Running Tests

To build the test files:

    test_helpers/build_test.sh

Running the tests:

    lein cljsbuild test

## Some individual build tasks explained

For more information on the `gulp` tasks, open `gulpfile.js` and read the
`gulp.task` parts.

### Compiling SCSS to CSS

    gulp sass

### Exhibit 3.0 task

This task:

    gulp exhibit3

does the following:

- concatenate and minify all Exhibit 3.0 JavaScript files
- concatenate and minify all Exhibit 3.0 CSS files
- copies all Exhibit 3.0 images to their destination
- copies the English locale file to its destination

Running it once should suffice, since the minification is a rather expensive
step.

### Compiling ClojureScript

We make use of **Advanced Optimizations** for our ClojureScript code (where's
the fun if you don't do that?). As such, this is the slowest step in the entire
build process.

[lein](https://github.com/technomancy/leiningen) (Leiningen) and the
[lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild) are used for
helping us setup and compile the ClojureScript code in the project.

To clean any compiled files:

    lein cljsbuild clean

To compile the ClojureScript code into JavaScript:

    lein cljsbuild once

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
