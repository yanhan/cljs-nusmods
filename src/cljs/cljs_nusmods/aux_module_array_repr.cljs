(ns
  ^{:doc "Helper functions for array representation of auxiliary Module object"}
  cljs-nusmods.aux-module-array-repr)

(def
  ^{:doc "Module Type information represented as bitmasks. Retrieved from
          JavaScript globals."}
  MODULE_TYPE (aget js/window "MODULE_TYPE"))

(def
  ^{:doc "Faculty module (bitmask representation)"}
  MODULE_TYPE_FACULTY (aget MODULE_TYPE "Faculty"))

(def
  ^{:doc "Breadth / UE module (bitmask representation)"}
  MODULE_TYPE_UE (aget MODULE_TYPE "UE"))

(def
  ^{:doc "Singapore Studies module (bitmask representation)"}
  MODULE_TYPE_SS (aget MODULE_TYPE "SS"))

(def
  ^{:doc "GEM module (bitmask representation)"}
  MODULE_TYPE_GEM (aget MODULE_TYPE "GEM"))

(def
  ^{:doc "Vector of all Module types bitmasks"}
  MODULE_TYPES_VEC
  [MODULE_TYPE_FACULTY MODULE_TYPE_UE MODULE_TYPE_SS MODULE_TYPE_GEM])

(def
  ^{:doc "Map of Module type bitmask -> String"}
  MODULE_TYPES_MAP
  {MODULE_TYPE_FACULTY "Faculty"
   MODULE_TYPE_UE      "Breadth / UE"
   MODULE_TYPE_SS      "Singapore Studies"
   MODULE_TYPE_GEM     "GEM"})

(def
  ^{:doc "A module not of the above types"}
  NOT_IN_CORS
  "Not in CORS")

(defn get-module-types
  "Retrieves the types of the module (Faculty, Breadth/UE) and returns it as a
   JavaScript array"
  [auxModuleArrayRepr]
  (let [moduleType    (nth auxModuleArrayRepr 2)
        moduleTypeVec (filter (fn [mt] (= (bit-and moduleType mt) mt))
                              MODULE_TYPES_VEC)]
    (if (empty? moduleTypeVec)
      (array NOT_IN_CORS)
      (clj->js (map (fn [moduleType] (get MODULE_TYPES_MAP moduleType))
                    moduleTypeVec)))))

(defn get-module-description
  "Returns the description of a Module, given its auxiliary array
   representation"
  [auxArrayRepr]
  (nth auxArrayRepr 0))

(defn- get-module-department-index
  "Returns the department index (to an array of department strings) of a module
   given its auxiliary array representation"
  [auxModuleArrayRepr]
  (nth auxModuleArrayRepr 1))

(defn get-module-department
  "Returns a String of the module's department if it has no faculty, else
   returns a JavaScript Array with a single String of the module's department"
  [departmentToFacultyIndexHash departmentStringsArray auxModuleArrayRepr]
  (let [moduleDepartmentIndex (get-module-department-index auxModuleArrayRepr)
        departmentString      (nth departmentStringsArray
                                   moduleDepartmentIndex)]
    (if (= moduleDepartmentIndex
           (aget departmentToFacultyIndexHash moduleDepartmentIndex))
      ; Module has no Faculty, return the Department String
      departmentString
      ; Module has a Faculty.
      ; Return a JavaScript array of the Department String.
      ; It turns out that using a JavaScript array is the key to having a nested
      ; hierarchy for an Exhibit 3 HierarchicalFacet
      (array departmentString))))

(defn- get-module-lecturers-index-array
  "Returns a JavaScript Array of indices to an array of Lecturer Strings of a
   module given its auxiliary array representation"
  [auxModuleArrayRepr]
  (nth auxModuleArrayRepr 3))

(defn get-module-lecturers
  "Returns a JavaScript Array of Strings, where each String is the name of a
   Lecturer of the module."
  [lecturersStringsArray auxModuleArrayRepr]
  (let [lecturersIndexArray (get-module-lecturers-index-array
                              auxModuleArrayRepr)]
    (clj->js
      (map (fn [lecturerIdx]
             (nth lecturersStringsArray lecturerIdx))
           lecturersIndexArray))))

(defn- get-module-prereqs
  "Returns an integer index to an Array of Module Prerequisites Strings."
  [auxModuleArrayRepr]
  (nth auxModuleArrayRepr 4))

(defn get-module-prereqs-string
  "Returns a String of the prerequisites of a Module or the integer -1 if it has
   no prerequisites, given the Module's auxiliary array representation."
  [prereqsStringsArray auxModuleArrayRepr]
  (let [modulePrereqsIndex (get-module-prereqs auxModuleArrayRepr)]
    (if (= modulePrereqsIndex -1)
        -1
        (nth prereqsStringsArray modulePrereqsIndex))))

(defn- get-module-preclusions
  "Returns an integer index to a JavaScript Array of Module Preclusions
   Strings."
  [auxModuleArrayRepr]
  (nth auxModuleArrayRepr 5))

(defn get-module-preclusions-string
  "Returns a String of the preclusions of a Module or the integer -1 if it has
   no preclusions, given a Module's auxiliary array representation."
  [preclusionsStringsArray auxModuleArrayRepr]
  (let [modulePreclusionsIndex (get-module-preclusions auxModuleArrayRepr)]
    (if (= modulePreclusionsIndex -1)
        -1
        (nth preclusionsStringsArray modulePreclusionsIndex))))

(defn- get-module-workload
  "Returns an integer index to a JavaScript Array of Module Workload Strings."
  [auxModuleArrayRepr]
  (nth auxModuleArrayRepr 6))

(defn get-module-workload-string
  "Returns a String of the workload of a Module or the integer -1 if it has no
   workload, given a Module's auxiliary array representation."
  [workloadStringsArray auxModuleArrayRepr]
  (let [workloadIndex (get-module-workload auxModuleArrayRepr)]
    (if (= workloadIndex -1)
        -1
        (nth workloadStringsArray workloadIndex))))
