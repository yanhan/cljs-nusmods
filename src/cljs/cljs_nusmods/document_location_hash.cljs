(ns ^{:doc ""}
  cljs-nusmods.document-location-hash
  (:require clojure.string
            [cljs-nusmods.localStorage :as localStorage]))

(def ^{:doc     "Key for storing the `document.location.hash` indicating
                 modules and lessons selected by the user"
       :private true}
  LOCALSTORAGE-DOC-LOCATION-HASH-KEY "cljs-nusmods:url-hash")

(defn- remove-leading-sharp-from-url-hash
  "Removes the leading '#' character from a url hash"
  [urlHash]
  (if (= "#" (first urlHash))
      (subs urlHash 1)
      urlHash))

(defn get-url-hash
  "Retrieves the current value of `document.location.hash`"
  []
  (remove-leading-sharp-from-url-hash
    (aget (aget js/document "location") "hash")))

(defn get-url-hash-from-local-storage
  "Retrieves the `document.location.hash` stored in `localStorage`"
  []
  (localStorage/get-item LOCALSTORAGE-DOC-LOCATION-HASH-KEY))

(defn set-url-hash!
  "Sets `document.location.hash` to a given string and updates the url hash in
   LocalStorage"
  [newDocLocationHash]
  (let [finalDocLocationHash (remove-leading-sharp-from-url-hash
                               newDocLocationHash)]
    (aset (aget js/document "location") "hash" finalDocLocationHash)
    (localStorage/set-item LOCALSTORAGE-DOC-LOCATION-HASH-KEY
                           finalDocLocationHash)))

(defn update-with-new-module!
  "Updates document.location.hash with the module url hash of newly added
   module."
  [moduleUrlHash]
  (let [orgUrlHash (get-url-hash)]
    (.log js/console (str "orgUrlHash = \"" orgUrlHash "\""))
    (set-url-hash! (str orgUrlHash
                        (if (empty? orgUrlHash) "" "&")
                        moduleUrlHash))))

(defn remove-module!
  "Removes a module from `document.location.hash`"
  [moduleCode moduleHasLessons?]
  (let [orgUrlHash (get-url-hash)

        urlHashWithoutModule
        (if moduleHasLessons?
            (clojure.string/replace
              orgUrlHash
              (re-pattern (str moduleCode "_[A-Z]{1,3}=[^&]+&?")) "")
            ; for modules without lessons, replace the module code itself
            (clojure.string/replace orgUrlHash
                                    (re-pattern (str moduleCode "&?")) ""))]
    (set-url-hash! (clojure.string/replace urlHashWithoutModule #"&$" ""))))

(defn update-with-changed-lesson-group!
  "Updates a module's lesson type in `document.location.hash` with a changed
   changed lesson group."
  [moduleCode lessonTypeShortForm newLessonGroup]
  (let [orgUrlHash (get-url-hash)

        newUrlHash
        (clojure.string/replace orgUrlHash
                                (re-pattern (str moduleCode "_"
                                                 lessonTypeShortForm "=[^&]+"))
                                (str moduleCode "_" lessonTypeShortForm "="
                                     newLessonGroup))]
    (set-url-hash! newUrlHash)))

(defn reset-url-hash!
  "Resets `document.location.hash` to the empty string"
  []
  (set-url-hash! ""))
