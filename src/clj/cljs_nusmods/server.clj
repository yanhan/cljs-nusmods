(ns cljs-nusmods.server
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as ring-json]
            [ring.util.response :as resp]
            clj-http.client
            clojure.string
            clout.core
            environ.core
            ring.util.codec
            selmer.parser
            selmer.filters))

; String replace
(selmer.filters/add-filter! :replace clojure.string/replace)

; "sem2" -> "Sem 2"
(selmer.filters/add-filter! :display-sem
                            (fn [s]
                              (clojure.string/capitalize
                                (str (subs s 0 3) " " (last s)))))

(def ^{:doc     "Host url"
       :private true}
  HOST-URL (environ.core/env :host-url))

(def ^{:doc     "Length of Host url"
       :private true}
  HOST-URL-LEN (count HOST-URL))

(def ^{:doc     "YOURLS API for shortening urls"
       :private true}
  YOURLS-URL (environ.core/env :yourls-url))

(def ^{:doc     "YOURLS signature for shortening urls"
       :private true}
  YOURLS-SIG (environ.core/env :yourls-sig))

(def ^{:doc     "Minimum academic year supported"
       :private true}
  ACAD-YEAR-MIN (Integer. (environ.core/env :acad-year-min)))

(def ^{:doc     "Maximum academic year supported"
       :private true}
  ACAD-YEAR-MAX (Integer. (environ.core/env :acad-year-max)))

(def ^{:doc     "Regex for matching academic year and semester after trimming
                 the domain"
       :private true}
  URL-SHORTEN-ACAD-YEAR-SEM-REGEX #"^(\d{4})-(\d{4})/sem(?:1|2)/")

(def ^{:doc     "String containing the regex for matching a module name"
       :private true}
  MODULE-NAME-REGEX-FORMAT "[A-Z]+\\d{4}[A-Z]*")

(def ^{:doc     "Matches a module's lesson for a url to shorten"
       :private true}
  MODULE-LESSON-REGEX
  (re-pattern (str "^" MODULE-NAME-REGEX-FORMAT
                   "_(?:DL|L|LAB|PL|PT|R|SEM|ST|T|T2|T3)=[A-Z0-9]+$")))

(def ^{:doc     "Matches a module without a lesson in a url to shorten"
       :private true}
  MODULE-NO-LESSON-REGEX (re-pattern (str "^" MODULE-NAME-REGEX-FORMAT "$")))

(defn- url-to-shorten-lessons-string-ok?
  "Checks if the lessons string for a url to shorten is ok.
   The lessons string is a '&' separated string of module lessons."
  [lessonsString]
  (or (empty? lessonsString)
      (and (= (first lessonsString) \#)
           (or (= (count lessonsString) 1)
               (every? #(or (not (empty? (re-seq MODULE-LESSON-REGEX %1)))
                            (not (empty? (re-seq MODULE-NO-LESSON-REGEX %1))))
                       (clojure.string/split (subs lessonsString 1) #"&"))))))

(defn- url-to-shorten-right-format?
  "Checks if a url passed in via the `/shorten` route is in an acceptable
   format."
  [url]
  (and (.startsWith url HOST-URL)
       ; check acad year and semester
       (let [urlWithoutDomain (subs url HOST-URL-LEN)

             matchVec
             (re-find URL-SHORTEN-ACAD-YEAR-SEM-REGEX urlWithoutDomain)]
         (and (not (empty? matchVec))
              (let [ayStart (Integer/parseInt (nth matchVec 1))
                    ayEnd   (Integer/parseInt (nth matchVec 2))]
                (and (>= ayStart ACAD-YEAR-MIN)
                     (<= ayStart ACAD-YEAR-MAX)
                     (= (inc ayStart) ayEnd)))

              (url-to-shorten-lessons-string-ok?
                (subs urlWithoutDomain (count (first matchVec))))))))

(defn- shorten-url
  "Obtains a short url for a long cljs-nusmods url"
  [url]
  (let [urlShortenResponse
        (clj-http.client/get YOURLS-URL
                             {:query-params {"action"    "shorturl"
                                             "signature" YOURLS-SIG

                                             "url"
                                             (ring.util.codec/url-encode url)

                                             "format"    "simple"}})

        responseCode (:status urlShortenResponse)]
    (if (= responseCode 200)
        {:status  200
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body    {"status"   200
                   "shortUrl" (:body urlShortenResponse)}}
        {:status  responseCode
         :headers {"Content-Type" "application/json; charset=utf-8"}
         :body    {"status"  responseCode
                   "message" "An error occurred"}})))

(def acad-year-sem-route
  (clout.core/route-compile "/:acad-year/:sem/"
                            {:acad-year #"20\d{2}-20\d{2}"
                             :sem       #"sem(?:1|2)"}))

(defroutes app-routes
  (GET acad-year-sem-route [acad-year sem]
       (selmer.parser/render-file "public/index.html"
                                  {:acad-year acad-year :sem sem
                                   :host-url  HOST-URL}))
  (GET "/" [] (resp/redirect "2014-2015/sem1/"))
  (GET "/shorten" [url]
       (if (url-to-shorten-right-format? url)
           ; do smth meaningful here
           (shorten-url url)
           {:status  400
            :headers {"Content-Type" "application/json; charset=utf-8"}
            :body    {"message"      "url given is unacceptable"}}))
  (route/resources "/"))

(def app
  (ring-json/wrap-json-response (handler/site app-routes)))
