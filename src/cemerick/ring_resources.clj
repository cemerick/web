(ns cemerick.ring-resources
  (:require (compojure [route :as route]
                       [handler :as handler]
                       [core :refer (defroutes GET HEAD)])
            [ring.util.response :as response]
            [ring.middleware.file-info :as file-info]
            [ring.middleware.content-type :as content-type]
            [clojure.java.io :as io]))

;; discussion here: https://gist.github.com/3655445

;; from ring.util.response, tweaked to return a Last-Modified header for resources
(defn resource-response
  "Returns a Ring response to serve a packaged resource, or nil if the
  resource does not exist.
  Options:
    :root - take the resource relative to this root"
  [req path & [opts]]
  (let [path (-> (str (:root opts "") "/" path)
                 (.replace "//" "/")
                 (.replaceAll "^/" ""))]
    (if-let [^java.net.URL resource (io/resource path)]
      (case (.getProtocol resource)
        "file"
        (let [file (#'response/url-as-file resource)]
          (if-not (.isDirectory file)
            (response/response file)))
        
        "jar"
        (let [mod-date (->> (.toExternalForm resource)
                         (re-find #"file:(.+)!")
                         second
                         java.io.File.
                         .lastModified
                         java.util.Date.)
              mod-date-str (.format (file-info/make-http-format) mod-date)]
          ;; it'd be nice if the file-info ns provided some canned functions
          ;; for generating file-esque responses from a bag of known parameters
          ;; (which can come from either a resource in a jar or a regular file)
          (if (#'file-info/not-modified-since? req mod-date)
            (-> (response/response "")
              (response/status 304)
              (response/header "Content-Length" 0)
              (response/header "Last-Modified" mod-date-str))
            (-> (io/input-stream resource)
              response/response
              (response/header "Last-Modified" mod-date-str))))
        
        (response/response (io/input-stream resource))))))

;; from compojure.route, tweaked to use the above variant of resource-response
(defn resources
  "A route for serving resources on the classpath. Accepts the following
  keys:
    :root - the root prefix to get the resources from. Defaults to 'public'."
  [path & [options]]
  (-> (GET (#'route/add-wildcard path) {{resource-path :*} :route-params :as req}
        (let [root (:root options "public")]
          (resource-response req (str root "/" resource-path))))
      (file-info/wrap-file-info (:mime-types options))
      (content-type/wrap-content-type options)))

