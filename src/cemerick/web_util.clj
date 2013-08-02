(ns cemerick.web-util
  (:require [net.cgrand.enlive-html :as en]
            clojure.string
            [ring.util.response :as resp]))

(defn minify-css
  "Minifies the given CSS string, returning the result.
   If you're minifying static files, please use YUI or similar."
  [css]
  (-> css
    (clojure.string/replace #"[\n|\r]" "")
    (clojure.string/replace #"/\*.*?\*/" "")
    (clojure.string/replace #"\s+" " ")
    (clojure.string/replace #"\s*:\s*" ":")
    (clojure.string/replace #"\s*,\s*" ",")
    (clojure.string/replace #"\s*\{\s*" "{")
    (clojure.string/replace #"\s*}\s*" "}")
    (clojure.string/replace #"\s*;\s*" ";")
    (clojure.string/replace #";}" "}")))

(defn html-from
  [path]
  (en/at (en/xml-resource path)
         [en/comment-node] nil
         [:head :style] #(update-in % [:content] (partial map minify-css))))

;; elastic beanstalk's load balancer tacks on an explicit text/plain Content-Type if we don't have one specified already
(defn html-content-type-middleware
  [handler]
  (fn [request]
    (let [resp (handler request)
          resp (if (map? resp) resp (resp/response resp))]
      (if (-> resp :headers (get "Content-Type"))
        resp
        (-> resp
          (resp/content-type "text/html")
          (resp/charset "UTF-8"))))))

