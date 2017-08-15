(ns recom.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
  :users
  (fn [db _]
    ;(.log js/console "users_sub")
    (:users db)
    ))
