// figure out the number of module objects with the keys

var modulesJSON = require("./modules.json");
var nrModules = modulesJSON.length;

var moduleKeys = [
  "ModuleCode", "ModuleTitle", "Department", "ModuleDescription",
  "ModuleCredit", "Workload", "Preclusion", "ExamDate", "Types", "Timetable",
  "Lecturers", "Prerequisites", "CrossModule", "Corequisite"
];

(function() {
  var moduleKeyFreqs = {};
  var i;
  var mod;
  var modKey;
  var k;
  var prop;
  var nrModuleKeys = moduleKeys.length;
  for (i = 0; i < nrModules; i += 1) {
    mod = modulesJSON[i];
    for (k = 0; k < nrModuleKeys; k += 1) {
      modKey = moduleKeys[k];
      if (mod.hasOwnProperty(modKey)) {
        if (moduleKeyFreqs[modKey]) {
          moduleKeyFreqs[modKey] += 1;
        } else {
          moduleKeyFreqs[modKey] = 1;
        }
      }
    }
  }
  console.log("total number of modules = " + nrModules);
  for (prop in moduleKeyFreqs) {
    if (moduleKeyFreqs.hasOwnProperty(prop)) {
      console.log("modules with key \"" + prop + "\" = " +
        moduleKeyFreqs[prop]
      );
    }
  }
})();
