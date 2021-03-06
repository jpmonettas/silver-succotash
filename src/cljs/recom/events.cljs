(ns recom.events
  (:require [re-frame.core :as re-frame]
            [clojure.walk :as walk]
            [ajax.core :as ajax]
            [clojure.set :as set]
            [recom.utils :as utils]
            [recom.db :as db]
            [no.en.core :refer [base64-encode]]))
(re-frame/reg-event-db
  :users
  (fn  [db [_ users]]
    (let [data (walk/keywordize-keys (js->clj (.parse js/JSON users)))
          add (remove nil? (:add data))
          remove (remove nil? (:remove data))]
      (.log js/console remove)
      (.log js/console add)
      ;(assoc db :users (set/union (:users db) add))
      (assoc db :users (set/union
        (set/difference (set (:users db)) remove)
        add
        ))
      )
    ))

(re-frame/reg-event-db
  :select-all
  (fn  [db _ ]
    (reset! recom.views/all-selected (not @recom.views/all-selected ))
    (if @recom.views/all-selected
      (let [users (:users db)
            t  (map
                 (fn [n]
                      [:user (:user n)]
                      )
                    users)
            ]
        (reset! recom.views/ticked? (set t))
        )
      (reset! recom.views/ticked? {})
      )
    db
    ))

(re-frame/reg-event-db
  :sort-by
  (fn  [db [_ by]]
      (.log js/console (str "sort by " by))
    (assoc db :users (sort-by by (:users db)))
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

(re-frame/reg-event-fx
  :save-token
  (fn [db [_ token]]
    (.log js/console (str "received:" token))
    (reset! db/token token)
    {}
    ))
(re-frame/reg-event-fx                             ;; note the trailing -fx
  :download-private-key                      ;; usage:  (dispatch [:handler-with-http])
  (fn [{:keys [db]} [_ username]]                    ;; the first param will be "world"
    {:http-xhrio {:method          :get
                  :headers { :token-auth @db/token}
                  :uri             (str "http://localhost:9094/user/" username "/private_key")
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
  (fn [{:keys [db]} [_ username]]                    ;; the first param will be "world"
    {:http-xhrio {:method          :post
                  :headers { :token-auth @db/token}
                  :uri             (str "http://localhost:9094/user/" username "/delete")
                  :params {}
                  :timeout         8000
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
                  :on-success      [:toast {:text "deleted" :duration 3000}]
                  :on-failure      [:toast {:text "Error Deleting User" :duration 3000}]
                  }}))
;; TODO: baseurl as config param
;; TODO: get created user id from response
(re-frame/reg-event-fx                             ;; note the trailing -fx
  :send-command                      ;; usage:  (dispatch [:handler-with-http])
  (fn [{:keys [db]} [_ req]]                    ;; the first param will be "world"
    {:http-xhrio {:method          :post
                  ;:with-credentials true
                  :headers { :token-auth @db/token}
                  :uri             (str "http://localhost:9094/" (:endpoint req))
                  :params (:params req)
                  :timeout         8000
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})  ;; IMPORTANT!: You must provide this.
                  :on-success      [:toast {:text (str "Created user" ) :duration 3000}]
                  :on-failure      [:toast {:text "Error Creating User" :duration 3000}]
                  }}))
(re-frame/reg-event-fx                             ;; note the trailing -fx
  :login-user                      ;; usage:  (dispatch [:handler-with-http])
  (fn [{:keys [db]} [_ credentials]]                    ;; the first param will be "world"
    (.log js/console (str "credentials: " @db/credentials))
    {:http-xhrio {:method          :post
                  ;:with-credentials true
                  :uri             (str "http://localhost:9094/login")
                  :params  credentials
                  :timeout         8000
                  :format          (ajax/json-request-format)
                  :response-format (ajax/text-response-format)
                  :on-success      [:save-token]
                  :on-failure      [:failed-login]
                  }}))
(re-frame/reg-event-db
  :http-result
  (fn [db [_ result]]
    (assoc db :api-result result)))