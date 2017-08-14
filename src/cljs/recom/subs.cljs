(ns recom.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))
;
;(re-frame/reg-sub
; :disk
; (fn [db _]
;   (.log js/console "disk_sub")
;   (:disk-usage db)
;   ))
;(re-frame/reg-sub
;  :connections
;  (fn [db _]
;    (.log js/console "connections_sub")
;    (:connections db)
;    ))
(re-frame/reg-sub
  :users
  (fn [db _]
    (.log js/console "users_sub")
    (:users db)
    ))
(re-frame/reg-sub
  :pubkey
  (fn [db _]
    (:pubkey db)
    ))