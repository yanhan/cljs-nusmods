var _ = require("lodash");
var argv = require("minimist")(process.argv.slice(2));
// Supply `-p` flag to inspect values of processed_modules.json
var MODULES_ARRAY = require(
  __dirname + "/../" + (argv["p"] ? "processed_modules.json" : "modules.json")
);

var show_all_possible_values_for_key_with_string_value = function(
    objList, key, prefixText) {
  if (prefixText) {
    console.log(prefixText);
  }
  console.log(_.uniq(_.reduce(objList, function(valueArray, obj) {
    if (_.has(obj, key)) {
      valueArray.push(obj[key]);
    }
    return valueArray;
  }, []).sort(), true));
};

var show_all_possible_values_for_key_with_array_of_strings_value = function(
    moduleList, key) {
  console.log();
  console.log("All possible values for Module." + key + ":");
  console.log(_(moduleList)
    .filter(function(mod) { return _.has(mod, key); })
    .map(function(mod) {
      return _.map(mod[key], function(s) { return s.trim(); });
    })
    .flatten()
    .uniq()
    .value()
  );
};

show_all_possible_values_for_key_with_string_value(MODULES_ARRAY,
  "ModuleCredit", "All possible values for Module.ModuleCredit:"
);
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY, "ExamDate",
  "All possible values for Module.ExamDate:"
);
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY, "Department",
  "All possible values for Module.Department:"
);
show_all_possible_values_for_key_with_string_value(MODULES_ARRAY,
  "ModuleCredit", "All possible values for Module.ModuleCredit:"
);
show_all_possible_values_for_key_with_array_of_strings_value(MODULES_ARRAY,
  "Types"
);
