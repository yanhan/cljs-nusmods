// figure out the number of module objects with the keys

var _ = require("lodash");
var MODULES_ARRAY = require(__dirname + "/../modules.json");

var MODULE_KEYS = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Types", "Timetable",
  "Lecturers", "Prerequisites", "CrossModule", "Corequisite"
];

(function() {
  var moduleKeyFreqs = _.reduce(MODULES_ARRAY, function(keyFreqHash, module) {
    _(MODULE_KEYS)
      .filter(function(key) {
        return _.has(module, key);
      })
      .forEach(function(key) {
        if (!_.has(keyFreqHash, key)) {
          keyFreqHash[key] = 0;
        }
        keyFreqHash[key] += 1;
      });
    return keyFreqHash;
  }, {});
  console.log("total number of modules = " + MODULES_ARRAY.length);
  _.forEach(moduleKeyFreqs, function(freq, key) {
    console.log("modules with key \"" + key + "\" = " + freq);
  });

  var arrayOfModulesWithTimetable =
    _.filter(MODULES_ARRAY, function(module) {
      return _.has(module, "Timetable");
    });
  var timetableKeyFreqs =
    arrayOfModulesWithTimetable
      .reduce(function(keyFreqHash, module) {
        _.forEach(module.Timetable, function(lessonObject) {
          _(lessonObject).keys().forEach(function(key) {
            if (!_.has(keyFreqHash, key)) {
              keyFreqHash[key] = 0;
            }
            keyFreqHash[key] += 1;
          });
        });
        return keyFreqHash;
      }, {});
  console.log(
    "\nThere are " + arrayOfModulesWithTimetable.length +
    " modules with the \"Timetable\" key and a total of " +
    _(arrayOfModulesWithTimetable)
      .map(function(mod) {
        return mod.Timetable;
      })
      .flatten()
      .value()
      .length +
    " lesson objects."
  );
  _.forEach(timetableKeyFreqs, function(freq, key) {
    console.log("  " + freq + " lesson objects have the key \"" + key +  "\"");
  });
})();
