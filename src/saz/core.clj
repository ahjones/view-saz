(ns saz.core
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :refer [response content-type status header]]
            [hiccup.core :as html]
            [hiccup.page :refer [html5]])
  (:import [java.util.zip ZipFile]
           [java.util UUID])
  (:gen-class))

(def traces (atom {}))

(defn- get-content-type [path]
  (condp re-find path
    #"\.htm$" "text/html"
    #"\.html$" "text/html"
    #"\.xml$" "application/xml"
    "text/plain"))

(defn get-file [trace path]
  (let [zip (@traces trace)
        path (if (clojure.string/blank? path) "_index.htm" path)
        ct (get-content-type path)]
    (-> (response (.getInputStream zip (.getEntry zip path)))
        (header "Cache-Control" "max-age=3600")
        (content-type (str ct "; charset=utf-8")))))

(defn upload-saz [request]
  (let [tempfile (get-in request [:params :trace :tempfile])
        zip (ZipFile. tempfile)
        id (str (UUID/randomUUID))]
    (swap! traces assoc id zip)
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

(def app (-> routes
             handler/site))
