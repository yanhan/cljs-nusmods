(ns ^{:doc "Code for Select2 input boxes - `Select Modules for Timetable`
            input boxes.

            NOTE: Code here makes use of the `ModulesMap` JavaScript global
                  variable."
      }
  cljs-nusmods.select2
  (:use [jayq.core :only [$]]))

(def ^{:doc "jQuery objects for the Select2 input boxes"}
  Select2-Boxes
  [($ :#search-modules) ($ :#tt-search-modules)])

(defn- select2-query-fn
  "query function for the Select2 `Select Modules for Timetable` input"
  [options]
  (let [; Get global `ModulesMap` variable
        ModulesMap     (aget js/window "ModulesMap")
        resultsPerPage 20]
    (if (nil? (aget options "context"))
        (let [nrModules    (count ModulesMap)
              searchTerm   (.toUpperCase (aget options "term"))
              ; vector of initial search results
              firstResults
                (reduce
                  (fn [resultsVec [moduleCode module]]
                    (let [moduleName (get module "name")
                          haystack   (str (.toUpperCase moduleCode)
                                          " "
                                          (.toUpperCase moduleName))]
                      (if (not= -1 (.indexOf haystack searchTerm))
                          (conj resultsVec
                                {:id   moduleCode
                                 :text (str moduleCode " " moduleName)})
                          resultsVec)))
                  []
                  ModulesMap)
              sortedSearchResults
                (sort (fn [modA modB] (< (:id modA) (:id modB))) firstResults)]
          (aset options "context"
                (js-obj "searchResults" sortedSearchResults))))

    ((aget options "callback")
     (let [ctx           (aget options "context")
           searchResults (aget ctx "searchResults")]
       (if (not (empty? searchResults))
           (let [resultsForCurrentPage (take resultsPerPage searchResults)
                 remSearchResults      (drop resultsPerPage searchResults)]
             (aset ctx "searchResults" remSearchResults)
             (clj->js {"more" true, "context" ctx,
                       "results" resultsForCurrentPage}))
           ; no more results
           (js-obj "more" false, "context" ctx, "results" (array)))))))

(defn init-select2-element
  "Initialize the Select2 elements.
   This function should only be called after the JavaScript global variable
   `ModulesMap` is set"
  [$jqElem]
  (let [ModulesMap  (aget js/window "ModulesMap")]
    (.select2
      $jqElem
      (js-obj
        "multiple"      true
        "width"         "100%"
        "placeholder"   "Type code/title to add mods"
        "initSelection"
        (fn [elem callback]
          (callback
            (clj->js
              (map (fn [moduleCode]
                     {"id" moduleCode
                      "text" (str moduleCode " "
                                  (get-in ModulesMap [moduleCode "name"]))})
                   (.split (.val elem) ",")))))

        "query"         select2-query-fn))))
