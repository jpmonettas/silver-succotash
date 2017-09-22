(ns recom.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [recom.events]
            [recom.subs]
            [recom.views :as views]
            [recom.config :as config]
            [re-frisk.core :refer [enable-re-frisk!]]
            [recom.fxs]
            [day8.re-frame.http-fx]
            [recom.db :as db]
            [ajax.core :refer [GET POST]]
            [ajax.core :as ajax]
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
;(defn login []
;  (println "login")
;  (go (let [response (<! (http/post "http://localhost:9094/login"
;                                    {:with-credentials? false
;                                     :json-params  @db/credentials}))]
;        (prn (:body response))
;        (reset! db/token (:body response))))

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text)))

(defn open-sse [response]
  (.log js/console (str response))
  (reset! db/token response)
  (def users (js/EventSource.
               (str "http://localhost:9094/users?token-auth=" @db/token)
               ))
  (set! (.-onmessage users) (fn [ev]
                              (re-frame/dispatch [:users (.-data ev)])
                              ))
  )
(defn start-sse []
  (println "login")
  (POST "http://localhost:9094/login"
        {
         :with-credentials? false
         :params  @db/credentials
         :handler open-sse
         :error-handler error-handler
         :format          (ajax/json-request-format)
         :response-format (ajax/text-response-format)
         }
        )
  )

  (defn ^:export init []
  (dev-setup)
  (mount-root)
  (start-sse)
  ;(def users (js/EventSource.
  ;             (str "http://localhost:9094/users?token-auth" @db/token)
  ;                             ))
  ;(set! (.-onmessage users) (fn [ev]
  ;                                  (re-frame/dispatch [:users (.-data ev)])
  ;                                  ))
  )




