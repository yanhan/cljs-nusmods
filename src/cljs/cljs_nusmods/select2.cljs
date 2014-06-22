(ns ^{:doc "Code for Select2 input boxes - `Select Modules for Timetable`
            input boxes.

            NOTE: Code here makes use of the `ModulesMap` JavaScript global
                  variable."
      }
  cljs-nusmods.select2
  (:use [jayq.core :only [$ attr on parent]]))

(def ^{:doc "jQuery object for the Select2 input box"}
  $Select2-Box ($ :#search-modules))

(def ^{:doc     "jQuery object for the container of the Select2 input box"
       :private true}
  $Select2-Container ($ :#search-modules-container))

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
                   (filter (fn [s] (not= s "")) (.split (.val elem) ","))))))

        "query"         select2-query-fn))

    ; Add `select2-container-active` class to maintain CSS shadows that were
    ; added when the select2 menu is open
    (on $jqElem "select2-close"
        (fn []
          (.addClass ($ ".select2-container") "select2-container-active")))))

(defn select2-box-set-val
  "Sets the value for a Select2 jQuery object. The supplied value should be a
   JavaScript Array of module code Strings."
  [$select2-box moduleCodeStrings]
  (.select2 $select2-box "val" moduleCodeStrings true))

(defn select2-box-reset-val
  "Resets the value for a Select2 jQuery object"
  [$select2-box]
  (.select2 $select2-box "val" "" true))

(defn shift-select2-container-to
  "Shifts the Select2 container jQuery object to another id."
  [fromParentId toParentId]
  (let [currentParentId (attr (parent $Select2-Container) "id")]
    (if (= fromParentId currentParentId)
        (if (= toParentId "timetable-builder-controls")
          (.append ($ (str "#" toParentId)) $Select2-Container)
          (.prepend ($ (str "#" toParentId)) $Select2-Container)))))
