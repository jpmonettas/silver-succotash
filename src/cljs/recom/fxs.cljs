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
;; TODO: implement a notification/log box that accumulates responses and shows them for N mins
(re-frame/reg-fx
  :toast
  (fn [m]
    (utils/toast "snackbar" (:text m) (:duration m))
    ))
