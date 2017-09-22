(ns recom.db
  (:require
    [reagent.core :as reagent]
    ))

(def credentials (reagent/atom {:user "admin"
                                :pass "pass"}))
(def token (reagent/atom ""))
