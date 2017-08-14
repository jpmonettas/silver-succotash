(ns recom.fxs
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com  :refer-macros [handler-fn]]
            [reagent.core :as reagent]
            [recom.utils :as utils]))

(re-frame/reg-fx
  :copy-to-clipboard
  (fn [m]
    (utils/copy (:text m))
    ;(js/setTimeout #(utils/copy "timeout!!") (:timeout m))
    ))
(re-frame/reg-fx
  :download-text
  (fn [m]
    (utils/download-text (:filename m) (:text m))
    ;(utils/download-text "elninio" "soldado")
    ;(js/setTimeout #(utils/copy "timeout!!") (:timeout m))
    ))
(re-frame/reg-fx
  :toast
  (fn [m]
    (utils/toast "snackbar" (:text m) (:duration m))
    ))
;(re-frame/reg-fx
;  :confirm-user-delete
;  (fn [m]
;    (if
;      (.confirm js/window (str "really delete " (:user m)))
;      (re-frame/dispatch [:rest-delete-user (:user m)])
;      (re-frame/dispatch [:console-log "CANCELED"])
;      )
;    ))
;(.modal (.getElementById js/document "myModal") "")
;(.confirm js/window "algosds")
;(re-frame/reg-fx
;  :copy-to-clipboard
;  (fn [texto]
;    (let [dummy (.createElement js/document "input")]
;      (aset dummy "value" (:text texto))
;      (aset dummy "type" "text")
;      (.appendChild (.-body js/document) dummy)
;      (.select dummy)
;      (.execCommand js/document "copy" dummy)
;      (.removeChild (.-body js/document) dummy)
;      )
;    ))
;(js/setTimeout #(copiarla "") 10)