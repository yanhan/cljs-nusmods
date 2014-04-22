// Department - we convert the strings to title case.
// For instance, "COMPUTER SCIENCE" is converted to "Computer Science".
//
// Special Cases:
//   "CTR FOR ENGLISH LANGUAGE COMMUNICATION"
//     --> Center for English Language Communication
//   "DUKE-NUS GRADUATE MEDICAL SCHOOL S'PORE"
//     --> Duke NUS Graduate Medical School Singapore
//   "NURSING/ALICE LEE CTR FOR NURSING STUD"
//     --> Nursing / Alice Lee Center for Nursing Studies
//   "NUS GRAD SCH FOR INTEGRATIVE SCI & ENGG"
//     --> NUS Graduate School for Integrative Sciences and Engineering
//   "SINGAPORE-MIT ALLIANCE"
//     --> Singapore-MIT Alliance
//   "YALE-NUS COLLEGE"
//     --> Yale-NUS College
exports.normalize_department_string = function (department) {
  if (department === "CTR FOR ENGLISH LANGUAGE COMMUNICATION") {
    department = "Center for English Language Communication";
  } else if (department === "DUKE-NUS GRADUATE MEDICAL SCHOOL S'PORE") {
    department = "Duke-NUS Graduate Medical School Singapore";
  } else if (department === "NURSING/ALICE LEE CTR FOR NURSING STUD") {
    department = "Nursing / Alice Lee Center for Nursing Studies";
  } else if (department === "NUS GRAD SCH FOR INTEGRATIVE SCI & ENGG") {
    department = "NUS Graduate School for Integrative Sciences and Engineering";
  } else if (department === "SINGAPORE-MIT ALLIANCE") {
    department = "Singapore-MIT Alliance";
  } else if (department === "YALE-NUS COLLEGE") {
    department = "Yale-NUS College";
  } else {
    department = department.replace(/\w\S*/g, function(txt) {
      txt = txt.trim();
      return txt.charAt(0).toUpperCase() + txt.slice(1).toLowerCase();
    });
  }
  return department;
};
