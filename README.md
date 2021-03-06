# cljs-nusmods

ClojureScript implementation of [NUSMods](http://nusmods.com)

**Working demo:** [http://cljs-nusmods.pangyanhan.com](http://cljs-nusmods.pangyanhan.com)

**Blog Post:** [Link](http://blog.pangyanhan.com/posts/2014-06-15-announcing-cljs-nusmods.html)

**NOTE:** Currently this project does something useful, but a lot of useful
features are still missing and there's a lot more to come. Stay tuned!

**NOTE:** This is purely a personal project done for the sake of challenging
myself, since it's something I have wanted to do for quite some time.
Recently I've wanted to learn some ClojureScript, and what better way to dive
into it with an actual project?

For actual usage, I strongly recommend you use [NUSMods](http://nusmods.com).
It is less buggy and contains a lot more features.

## System requirements

- Leiningen
- node
- Python 2.7 (for running `helpers/dl-api-nusmods-stuff.py`)
- virtualenv (for running `helpers/dl-api-nusmods-stuff.py`)

## Setup

### Leiningen

Follow the installation instructions in the
[Leiningen repository](https://github.com/technomancy/leiningen).

### Node.js

Choose one of the following ways to install Node.js:
- install it using your favorite package manager
- clone the [Node.js github repo](https://github.com/joyent/node), checkout a
tag, build and install it

### Node.js libraries

Install the necessary nodejs libraries:

    npm install

### Install gulp

Gulp needs to be installed globally to be run as an executable:

    sudo npm install -g gulp@3.6.2

### virtualenv

To install virtualenv on an Ubuntu based system:

    sudo apt-get install python-virtualenv

Next, create a virtualenv folder named `venv` at the root of the repository
and install the Python libraries:

    virtualenv venv
    . venv/bin/activate
    pip install -r requirements.txt
    deactivate # exit the virtualenv

### Files from http://api.nusmods.com

Some files from [NUSMods API](http://api.nusmods.com) are required for
cljs-nusmods.

To download them:

    . venv/bin/activate
    python helpers/dl-api-nusmods-stuff.py
    deactivate # exit the virtualenv

## Building everything

Execute the following command:

    gulp

This is a rather expensive operation due primarily to the minification step and
ClojureScript compilation with advanced optimizations.

Check out the "**Some individual build tasks explained**" section below for
finer grained builds.

## Running cljs-nusmods

### Configuration

This project makes use of the [environ](https://github.com/weavejester/environ/)
library by James Reeves for configuration.

Currently, all "environment variables" (in quotes because you can supply the
values in other ways) are used for enabling url shortening using
[YOURLS](http://yourls.org/).

There are some steps we take to check a url submitted for shortening so that
the url shortening service is only used to shorten urls for cljs-nusmods.
However, these checks are pretty much futile if someone really wants to abuse
the url shortening service.

The following environment variables are used within the web server code:

- HOST_URL

The location where your cljs-nusmods is hosted, **ending with a `/` character**.
For my production site, this is `http://cljs-nusmods.pangyanhan.com/`.

This is used for checking the any url submitted for shortening so at the very
least, we know that it is used for shortening cljs-nusmods urls.
Please follow the format described, otherwise you will face issues shortening
urls.

- YOURLS_URL

Your own [YOURLS API url](http://yourls.org/#API), or a YOURLS API url which
someone has granted you permission to use.

- YOURLS_SIG

For [passwordless YOURLS API access](https://github.com/YOURLS/YOURLS/wiki/PasswordlessAPI).
**Keep this secret!**

- ACAD_YEAR_MIN

The minimum academic year enabled. If the minimum academic year you support is
`AY2011/2012`, this value should be set to `2011`.

This is used for checking the url format for url shortening so we only shorten
urls for supported academic years.

- ACAD_YEAR_MAX

The maximum academic year enabled, similar use case as per `ACAD_YEAR_MIN`.
If the max academic year you support is `AY2016/2017`, this value should be set
to `2016`.

Read the sections below to find out how to supply these "environment variables".

### Running in development

For running in development, I personally use a `.lein-env` file at the top level
of the repository to hold the "environment variables" in the following format:

    { :host-url       "value here"
      :yourls-url     "value here"
      :yourls-sig     "value here"
      :acad-year-min  valueHere
      :acad-year-max  valueHere }

To run the web server:

    lein ring server-headless

Access the application via http://127.0.0.1:3000

### Running in production

For production, I run an uberjar and supply environment variables:

    gulp # Builds everything. very important!
    lein ring uberjar

    HOST_URL='insert your host url' \
      YOURLS_URL='insert your YOURLS url' \
      YOURLS_SIG='insert your YOURLS sig' \
      ACAD_YEAR_MIN=minAcadYear \
      ACAD_YEAR_MIN=maxAcadYear \
      java -jar target/cljs-nusmods-0.1.0-SNAPSHOT-standalone.jar

## Development

Run the following:

    gulp watch

Any changes to the `src/scss/style.scss` will force a recompilation of SCSS to
CSS.

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
the fun if we don't do that?). As such, this is the slowest step in the entire
build process.

[Leiningen](https://github.com/technomancy/leiningen) (Leiningen) and the
[lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild) are used for
helping us setup and compile the ClojureScript code in the project.

To clean any compiled files:

    lein cljsbuild clean

To compile the ClojureScript code into JavaScript:

    lein cljsbuild once

## Credits

First and foremost, to [Eu Beng Hee](https://github.com/ahbeng) for making
his wonderful [NUSMods](https://github.com/ahbeng/NUSMods) project open source.
This project would probably have taken a very different route without using it
as a reference.

### Tools and Libraries Used

This project will probably be impossible without the following awesome
tools and libraries, used both directly and indirectly (even though the
documentation may not be so awesome for some of them):

#### Clojure / ClojureScript related
- [Leiningen](https://github.com/technomancy/leiningen) (hey look, my hair's not
on fire, but it's not long anyway)
- [lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild)
- [jayq](https://github.com/ibdknox/jayq) (jQuery wrapper for ClojureScript)
- [Compojure](https://github.com/weavejester/compojure) (backend web framework)
- [Selmer](https://github.com/yogthos/Selmer) (Jinja2 like templating)

#### JavaScript libraries (front end):
- [jQuery](http://jquery.com/)
- [jQuery UI](https://jqueryui.com/)
- [Exhibit 3.0](http://www.simile-widgets.org/exhibit3/)
(**THE** main library in the Module Finder page; documentation can be hard to
find)
- [Select 2](http://ivaynberg.github.io/select2/)
(For the `Select Modules for Timetable` input box)
- [qTip2](http://qtip2.com/) (Tooltips)
- [pace](http://github.hubspot.com/pace/docs/welcome/)
(Automatic page load progress bar)
- [ZeroClipboard](https://github.com/zeroclipboard/zeroclipboard)
(Copy to clipboard using Flash)

#### CSS libraries / tools:
- [gulp-sass](https://github.com/dlmanning/gulp-sass)
(Enables us to write SCSS instead of CSS, huge boon)
- [Font Awesome](http://fortawesome.github.io/Font-Awesome/) - Scalable vector
icons

#### JavaScript libraries (back end):
- [gulp.js](http://gulpjs.com/) (Making Grunt obsolete, kind of)
- [gulp-concat](https://github.com/wearefractal/gulp-concat)
- [gulp-uglify](https://www.npmjs.org/package/gulp-uglify)
- [gulp-minify-css](https://github.com/jonathanepollack/gulp-minify-css)
- [gulp-sass](https://github.com/dlmanning/gulp-sass)
- [gulp-shell](https://github.com/sun-zheng-an/gulp-shell)
- [lodash](http://lodash.com/)
(more modern version of [Underscore.js](http://underscorejs.org/); use this and
you may very well not see any `for` loops in your JavaScript for a start)
- [Moment.js](http://momentjs.com/)
- [minimist](https://github.com/substack/minimist)
- [node-mkdirp](https://github.com/substack/node-mkdirp)
