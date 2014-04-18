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

Generating the `modinfo.js` and `auxmodinfo.js` files, and copying them to
the `resources/public/js/` directory (these files hold compacted module
information):

    node convert_modules.js
    cp modinfo.js auxmodinfo.js resources/public/js/

## Running the web server

    lein ring server-headless

And go to http://127.0.0.1:3000/index.html
