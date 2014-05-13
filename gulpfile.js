var gulp = require("gulp");
var concat = require("gulp-concat");
var uglify = require("gulp-uglify");
var minifyCSS = require("gulp-minify-css");

// Concat + Minify Exhibit 3.0 JavaScript
gulp.task("exhibit3-js-concat-minify", function() {
  return gulp.src([
      "src/js/vendor/exhibit3/exhibit-api.js",
      "src/js/vendor/exhibit3/lib/base64.js",
      "src/js/vendor/exhibit3/lib/sprintf.js",
      "src/js/vendor/exhibit3/lib/jquery.history.js",
      "src/js/vendor/exhibit3/lib/jquery.history.shim.js",
      "src/js/vendor/exhibit3/lib/jquery.simile.dom.js",
      "src/js/vendor/exhibit3/lib/jquery.simile.bubble.js",
      "src/js/vendor/exhibit3/lib/es5-shim-2.1.0.js",
      "src/js/vendor/exhibit3/scripts/exhibit.js",
      "src/js/vendor/exhibit3/scripts/bc/bc.js",
      "src/js/vendor/exhibit3/scripts/bc/attributes.js",
      "src/js/vendor/exhibit3/scripts/registry.js",
      "src/js/vendor/exhibit3/scripts/util/util.js",
      "src/js/vendor/exhibit3/scripts/util/debug.js",
      "src/js/vendor/exhibit3/scripts/util/html.js",
      "src/js/vendor/exhibit3/scripts/util/set.js",
      "src/js/vendor/exhibit3/scripts/util/date-time.js",
      "src/js/vendor/exhibit3/scripts/util/units.js",
      "src/js/vendor/exhibit3/scripts/util/bookmark.js",
      "src/js/vendor/exhibit3/scripts/util/localization.js",
      "src/js/vendor/exhibit3/scripts/util/persistence.js",
      "src/js/vendor/exhibit3/scripts/util/history.js",
      "src/js/vendor/exhibit3/scripts/util/settings.js",
      "src/js/vendor/exhibit3/scripts/util/coders.js",
      "src/js/vendor/exhibit3/scripts/util/facets.js",
      "src/js/vendor/exhibit3/scripts/util/views.js",
      "src/js/vendor/exhibit3/scripts/data/database.js",
      "src/js/vendor/exhibit3/scripts/data/database/local.js",
      "src/js/vendor/exhibit3/scripts/data/database/type.js",
      "src/js/vendor/exhibit3/scripts/data/database/property.js",
      "src/js/vendor/exhibit3/scripts/data/database/range-index.js",
      "src/js/vendor/exhibit3/scripts/data/collection.js",
      "src/js/vendor/exhibit3/scripts/data/expression.js",
      "src/js/vendor/exhibit3/scripts/data/expression/collection.js",
      "src/js/vendor/exhibit3/scripts/data/expression/path.js",
      "src/js/vendor/exhibit3/scripts/data/expression/constant.js",
      "src/js/vendor/exhibit3/scripts/data/expression/operator.js",
      "src/js/vendor/exhibit3/scripts/data/expression/function-call.js",
      "src/js/vendor/exhibit3/scripts/data/expression/control-call.js",
      "src/js/vendor/exhibit3/scripts/data/expression/functions.js",
      "src/js/vendor/exhibit3/scripts/data/expression/controls.js",
      "src/js/vendor/exhibit3/scripts/data/expression-parser.js",
      "src/js/vendor/exhibit3/scripts/data/exporter.js",
      "src/js/vendor/exhibit3/scripts/data/exporters/json.js",
      "src/js/vendor/exhibit3/scripts/data/exporters/bibtex.js",
      "src/js/vendor/exhibit3/scripts/data/exporters/tsv.js",
      "src/js/vendor/exhibit3/scripts/data/exporters/html-table.js",
      "src/js/vendor/exhibit3/scripts/data/exporters/rdf-xml.js",
      "src/js/vendor/exhibit3/scripts/data/exporters/semantic-wikitext.js",
      "src/js/vendor/exhibit3/scripts/data/importer.js",
      "src/js/vendor/exhibit3/scripts/data/importers/json.js",
      "src/js/vendor/exhibit3/scripts/data/importers/jsonp.js",
      "src/js/vendor/exhibit3/scripts/data/importers/google-spreadsheet.js",
      "src/js/vendor/exhibit3/scripts/data/importers/babel-based.js",
      "src/js/vendor/exhibit3/scripts/data/importers/html-table.js",
      "src/js/vendor/exhibit3/scripts/data/importers/tsv-csv.js",
      "src/js/vendor/exhibit3/scripts/ui/ui.js",
      "src/js/vendor/exhibit3/scripts/ui/ui-context.js",
      "src/js/vendor/exhibit3/scripts/ui/lens-registry.js",
      "src/js/vendor/exhibit3/scripts/ui/lens.js",
      "src/js/vendor/exhibit3/scripts/ui/coordinator.js",
      "src/js/vendor/exhibit3/scripts/ui/formatter.js",
      "src/js/vendor/exhibit3/scripts/ui/format-parser.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/enumerated-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/list-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/cloud-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/numeric-range-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/alpha-range-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/text-search-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/facets/hierarchical-facet.js",
      "src/js/vendor/exhibit3/scripts/ui/views/view.js",
      "src/js/vendor/exhibit3/scripts/ui/views/view-panel.js",
      "src/js/vendor/exhibit3/scripts/ui/views/ordered-view-frame.js",
      "src/js/vendor/exhibit3/scripts/ui/views/tile-view.js",
      "src/js/vendor/exhibit3/scripts/ui/views/tabular-view.js",
      "src/js/vendor/exhibit3/scripts/ui/views/thumbnail-view.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/color-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/default-color-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/ordered-color-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/color-gradient-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/icon-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/size-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/coders/size-gradient-coder.js",
      "src/js/vendor/exhibit3/scripts/ui/control-panel.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/collection-summary-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/option-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/resizable-div-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/toolbox-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/bookmark-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/reset-history-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/logo.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/legend-widget.js",
      "src/js/vendor/exhibit3/scripts/ui/widgets/legend-gradient-widget.js",
      "src/js/vendor/exhibit3/locales/manifest.js",
      "src/js/vendor/exhibit3/scripts/final.js"
    ])
    .pipe(concat("exhibit3-all.min.js"))
    .pipe(uglify())
    .pipe(gulp.dest("resources/public/js/vendor/"));
});

// Concat + Minify Exhibit 3.0 CSS
gulp.task("exhibit3-css-concat-minify", function() {
  return gulp.src([
      "src/js/vendor/exhibit3/styles/graphics.css",
      "src/js/vendor/exhibit3/styles/exhibit.css",
      "src/js/vendor/exhibit3/styles/browse-panel.css",
      "src/js/vendor/exhibit3/styles/lens.css",
      "src/js/vendor/exhibit3/styles/control-panel.css",
      "src/js/vendor/exhibit3/styles/util/facets.css",
      "src/js/vendor/exhibit3/styles/util/views.css",
      "src/js/vendor/exhibit3/styles/views/view-panel.css",
      "src/js/vendor/exhibit3/styles/views/tile-view.css",
      "src/js/vendor/exhibit3/styles/views/tabular-view.css",
      "src/js/vendor/exhibit3/styles/views/thumbnail-view.css",
      "src/js/vendor/exhibit3/styles/widgets/collection-summary-widget.css",
      "src/js/vendor/exhibit3/styles/widgets/resizable-div-widget.css",
      "src/js/vendor/exhibit3/styles/widgets/bookmark-widget.css",
      "src/js/vendor/exhibit3/styles/widgets/toolbox-widget.css",
      "src/js/vendor/exhibit3/styles/widgets/legend-widget.css",
      "src/js/vendor/exhibit3/styles/widgets/option-widget.css",
      "src/js/vendor/exhibit3/styles/widgets/reset-history-widget.css"
    ])
    .pipe(concat("exhibit3-styles.min.css"))
    .pipe(minifyCSS())
    .pipe(gulp.dest("resources/public/css/"));
});

// Copy Exhibit 3.0 images
gulp.task("exhibit3-images", function() {
  return gulp.src(["src/js/vendor/exhibit3/images/**/*"])
    .pipe(gulp.dest("resources/public/exhibit3/images/"));
});

// Copy Exhibit 3.0 english locale
gulp.task("exhibit3-locale", function() {
  return gulp.src(["src/js/vendor/exhibit3/locales/en/locale.js"])
    .pipe(gulp.dest("resources/public/exhibit3/locales/en/"));
});

// All Exhibit 3.0 related tasks
gulp.task("exhibit3", [
  "exhibit3-js-concat-minify", "exhibit3-css-concat-minify", "exhibit3-images",
  "exhibit3-locale"
]);

// Default task
gulp.task("default", ["exhibit3"]);
