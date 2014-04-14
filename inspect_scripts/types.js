// Gives a high level overview of the keys and types of the values indexed
// by the keys in each individual module object of the modules.json file
//
// The modules.json file is based on the ones from here:
//
//     http://api.nusmods.com/
//
// IIRC, this script makes use of this one:
//
//     http://api.nusmods.com/2013-2014/2/modules.json
//
// Place the modules.json file in the same `inspect_scripts` directory as this
// script.
//
// Run this script using:
//
//     node inspect_scripts/types.js

var modulesJSON = require("./modules.json");

var get_type_string = function(o) {
  return (o instanceof Array ? "array" : typeof o);
};

var get_keys_and_types_in_modules = function(moduleList) {
  var i;
  var nrModules = moduleList.length;
  var mod;
  var prop;
  var propVal;
  var valueType;
  var keysWithinModule = {};
  for (i = 0; i < nrModules; i += 1) {
    mod = modulesJSON[i];
    for (prop in mod) {
      if (mod.hasOwnProperty(prop)) {
        propVal = mod[prop];
        valueType = get_type_string(propVal);
        if (!keysWithinModule.hasOwnProperty[prop]) {
          keysWithinModule[prop] = {};
        }
        keysWithinModule[prop][valueType] = true;
      }
    }
  }
  return keysWithinModule;
};

// find out all possible keys within a single module object
console.log("Keys in single module object and their JS types:\n");
console.log(get_keys_and_types_in_modules(modulesJSON));

var get_types_in_module_prop_array = function(moduleList, prop) {
  var i;
  var mod;
  var nrModules = moduleList.length;
  var contentsArray;
  var k;
  var nrTypes;
  var contentObj;
  var contentObjType;
  var typesHash = {};
  for (i = 0; i < nrModules; i += 1) {
    mod = moduleList[i];
    if (mod.hasOwnProperty(prop)) {
      contentsArray = mod[prop];
      nrTypes = contentsArray.length;
      for (k = 0; k < nrTypes; k += 1) {
        contentObj = contentsArray[k];
        contentObjType = get_type_string(contentObj);
        typesHash[contentObjType] = true;
      }
    }
  }
  return typesHash;
};

console.log("\nJS types in \"Types\" key:");
console.log(get_types_in_module_prop_array(modulesJSON, "Types"));

console.log("\nJS types in \"Lecturers\" key:");
console.log(get_types_in_module_prop_array(modulesJSON, "Lecturers"));

console.log("\nJS types in \"Timetable\" key:");
console.log(get_types_in_module_prop_array(modulesJSON, "Timetable"));

var get_types_in_timetable_array_object = function(modulesList) {
  var i;
  var nrModules = modulesList.length;
  var mod;
  var ttArray;
  var k;
  var ttLen;
  var ttObj;
  var prop;
  var propVal;
  var propValType;
  var keysAndTypesHash = {};
  for (i = 0; i < nrModules; i += 1) {
    mod = modulesList[i];
    if (mod.hasOwnProperty("Timetable")) {
      ttArray = mod["Timetable"];
      ttLen = ttArray.length;
      for (k = 0; k < ttLen; k += 1) {
        ttObj = ttArray[i];
        for (prop in ttObj) {
          if (ttObj.hasOwnProperty(prop)) {
            propVal = ttObj[prop];
            propValType = get_type_string(propVal);
            if (!keysAndTypesHash.hasOwnProperty(prop)) {
              keysAndTypesHash[prop] = {};
            }
            keysAndTypesHash[prop][propValType] = true;
          }
        }
      }
    }
  }
  return keysAndTypesHash;
};

console.log("\nKeys and JS types in each object of \"Timetable\" key:");
console.log(get_types_in_timetable_array_object(modulesJSON));
