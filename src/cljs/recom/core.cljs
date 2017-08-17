(ns recom.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [recom.events]
            [recom.subs]
            [recom.views :as views]
            [recom.config :as config]
            [re-frisk.core :refer [enable-re-frisk!]]
            [recom.fxs]
            [day8.re-frame.http-fx]
            ))


(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (enable-re-frisk!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/ui]
                  (.getElementById js/document "app")))

(defn event-source [url on-message on-error]
  (let [ev-source (js/EventSource. url)]
    (set! (.-onmessage ev-source) on-message)
    (set! (.-onerror ev-source) on-error)
    ev-source))
;; TODO: baseurl as config param

(defn ^:export init []
  (dev-setup)
  (mount-root)
  (def users (js/EventSource. "http://localhost:9094/users"))
  (set! (.-onmessage users) (fn [ev]
                                    (re-frame/dispatch [:users (.-data ev)])
                                    ))
  )




