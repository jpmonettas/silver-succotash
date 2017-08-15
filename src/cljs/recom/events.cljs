(ns recom.events
  (:require [re-frame.core :as re-frame]
            [clojure.walk :as walk]
            [ajax.core :as ajax]))


(re-frame/reg-event-db
  :users
  (fn  [db [_ users]]
    (.log js/console "user data received")
    (assoc db :users (walk/keywordize-keys (js->clj (.parse js/JSON users))))
    ))

(re-frame/reg-event-fx
  :copy-public-key
  (fn [cofx [_ key-text]]
    {:copy-to-clipboard {:text key-text
                         :timeout 10000}}))

;(re-frame/reg-event-fx
;  :delete-user
;  (fn [cofx [_ username]]
;    {:confirm-user-delete {:user username}}))

(re-frame/reg-event-fx
  :console-log
  (fn  [db [_ msg]]
    (.log js/console msg)
    ))
;(re-frame/reg-event-fx
;  :save-private-key
;  (fn  [db [_ msg]]
;    (.log js/console (:content msg))
;    ))
(re-frame/reg-event-fx
  :save-private-key
  (fn [cofx [_ body]]
    {:download-text {:text (:privkey (:content body))
                         :filename (:filename body)}}))

(re-frame/reg-event-fx                             ;; note the trailing -fx
  :download-private-key                      ;; usage:  (dispatch [:handler-with-http])
  (fn [{:keys [db]} [_ username]]                    ;; the first param will be "world"
    {:http-xhrio {:method          :post
                  :uri             "http://localhost:9094/users/private_key"
                  :params {:username username}
                  :timeout         8000
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
                  :on-success      [:save-private-key]
                  :on-failure      [:console-log]
                  }}))

(re-frame/reg-event-fx
  :toast
  (fn [cofx [_ toast]]
    (.log js/console toast)
    {:toast toast}))

;; TODO: on error notify somehow, on success do nothing as the screen will be automagically refreshed by EventSource
(re-frame/reg-event-fx                             ;; note the trailing -fx
  :delete-user                      ;; usage:  (dispatch [:handler-with-http])
  (fn [{:keys [db]} _]                    ;; the first param will be "world"
    {:db   (assoc db :show-twirly true)   ;; causes the twirly-waiting-dialog to show??
     :http-xhrio {:method          :post
                  :uri             "http://localhost:9094/users/delete"
                  :timeout         8000
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
                  :on-success      [:http-result]
                  :on-failure      [:toast {:text "Error Deleting User" :duration 3000}]
                  }}))


(re-frame/reg-event-db
  :http-result
  (fn [db [_ result]]
    (assoc db :api-result result)))