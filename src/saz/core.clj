(ns saz.core
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :refer [response content-type status header]]
            [hiccup.core :as html]
            [hiccup.page :refer [html5]]
            [environ.core :refer [env]]
            [clojure.java.io :refer [file copy]])
  (:import [java.util.zip ZipFile]
           [java.util UUID])
  (:gen-class))

(def traces (atom {}))
(def store-dir (file (env :store-path)))

(defn create-zip-file
  []
  (java.io.File/createTempFile "saz" "" store-dir))

(defn- get-content-type [path]
  (condp re-find path
    #"\.htm$" "text/html"
    #"\.html$" "text/html"
    #"\.xml$" "application/xml"
    "text/plain"))

(defn get-file [trace path]
  (let [zip (ZipFile. (file (@traces trace)))
        path (if (clojure.string/blank? path) "_index.htm" (clojure.string/replace path #"\\" "/"))
        ct (get-content-type path)]
    (-> (response (.getInputStream zip (.getEntry zip path)))
        (header "Cache-Control" "max-age=36000")
        (content-type (str ct "; charset=utf-8")))))

(defn upload-saz [request]
  (let [tempfile (get-in request [:params :trace :tempfile])
        outfile (create-zip-file)
        id (str (UUID/randomUUID))]
    (copy tempfile outfile)
    (swap! traces assoc id (.getCanonicalPath outfile))
    (-> (response "")
        (status 303)
        (header "Location" (str "/files/" id "/")))))

(def page
  (html5
   (html/html
    [:body
     [:form {:action "/files" :enctype "multipart/form-data" :method "post"}
      [:input {:type "file" :name "trace"}]
      [:input {:type "submit" :value "Upload"}]]])))

(defroutes routes
  (GET "/" request (-> (response page) (content-type "text/html; charset=utf-8")))
  (GET "/files/:trace/*" [trace *] (get-file trace *))
  (POST "/files" request (upload-saz request)))

(defn write-db
  [k r old new]
  (spit (str (env :store-path) "/index.db") (pr-str new)))

(defn init
  []
  (try
    (let [dbfile (read-string (slurp (str (env :store-path) "/index.db")))]
      (swap! traces merge dbfile))
    (catch Exception e))
  (add-watch traces :key write-db))

(def app (-> routes
             handler/site))
