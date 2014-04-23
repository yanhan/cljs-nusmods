// figure out the number of module objects with the keys

var _ = require("lodash");
var modulesJSON = require(__dirname + "/../modules.json");

var moduleKeys = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Types", "Timetable",
  "Lecturers", "Prerequisites", "CrossModule", "Corequisite"
];

(function() {
  var moduleKeyFreqs = _.reduce(modulesJSON, function(keyFreqHash, module) {
    _(moduleKeys)
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
  console.log("total number of modules = " + modulesJSON.length);
  _.forEach(moduleKeyFreqs, function(freq, key) {
    console.log("modules with key \"" + key + "\" = " + freq);
  });
})();
