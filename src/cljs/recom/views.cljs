(ns recom.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com  :refer-macros [handler-fn]]
            [reagent.core :as reagent]))
;; TODO: add a selection checkbox
(defn data-row
  [row i col-widths mouse-over click-msg]
  (let [mouse-over-row? (identical? @mouse-over row)]
    ^{:key i}[re-com/h-box
     :class    "rc-div-table-row"
     :attr     {:on-mouse-over (handler-fn (reset! mouse-over row))
                :on-mouse-out  (handler-fn (reset! mouse-over nil))}
     :children [[re-com/label :label (:user row) :width (:username col-widths)]
                [re-com/label :label (:port row) :width (:port col-widths)]
                [re-com/h-box
                 :gap      "2px"
                 :width    (:pubkey col-widths)
                 :align    :center
                 :children [[re-com/label :label (:pubkey row) :width "180px"
                             :style {:max-width "180px"
                                     :text-overflow "ellipsis"
                                     :overflow "hidden"}
                             ]
                            [re-com/row-button
                             :md-icon-name    "zmdi zmdi-copy"
                             :mouse-over-row? mouse-over-row?
                             :tooltip         "Copy pubkey to clipboard"
                             :on-click        #(re-frame/dispatch [:copy-public-key (:pubkey row)])
                             ]
                            ]]
                [re-com/h-box
                 :gap      "2px"
                 :width    (:privkey col-widths)
                 :align    :center
                 :children [(if (:privkey row)
                              [re-com/row-button
                               :md-icon-name    "zmdi zmdi-download"
                               :mouse-over-row? (:privkey row)
                               :tooltip         "Download private key.\n Key copy will be deleted afterwards\n"
                               :on-click        #(re-frame/dispatch [:download-private-key (:user row)])
                               ]
                              )]
                 ]
                [re-com/h-box
                 :gap      "2px"
                 :width    (:actions col-widths)
                 :align    :center
                 :children [[re-com/row-button
                             :md-icon-name    "zmdi zmdi-edit"
                             :mouse-over-row? mouse-over-row?
                             :tooltip         "Edit this line"
                             :on-click        #(reset! click-msg (str "edit row " i))]
                            [re-com/row-button
                             :md-icon-name    "zmdi zmdi-delete"
                             :mouse-over-row? mouse-over-row?
                             :tooltip         "Delete this line"
                             :on-click    (fn []
                                            (if
                                              (.confirm js/window (str "really delete " (:user row)))
                                              (re-frame/dispatch [:delete-user (:user row)])
                                              (.log js/console "canceled by user")
                                              ))
                             ;:on-click #(re-frame/dispatch [:delete-user (:user row)])
                             ]]]
                ]]))
;; TODO: add a "select all" checkbox
;; TODO: add "by column" sorting capability
(defn data-table
  [rows col-widths]
  (let [mouse-over (reagent/atom nil)
        click-msg  (reagent/atom "")]
    (fn []
      [re-com/v-box
       :align    :start
       :gap      "10px"
       :children [
                  [re-com/v-box
                   :class    "rc-div-table"
                   :children [[re-com/h-box
                               :class    "rc-div-table-header"
                               :children [[re-com/label :label "Username" :width (:username    col-widths)]
                                          [re-com/label :label "Port"    :width (:port    col-widths)]
                                          [re-com/label :label "Public"      :width (:pubkey     col-widths)]
                                          [re-com/label :label "Private"    :width (:privkey    col-widths)]
                                          [re-com/label :label "Actions" :width (:actions col-widths)]]]
                              (doall
                                (map-indexed
                                  (fn [i row]
                                    (data-row row i col-widths mouse-over click-msg)
                                    ) @rows))
                              ]]

                  ]])))


;(:users @re-frame.db/app-db.)
;; TODO: show forwarding status and allow to sort/filter
;; TODO: add a link to copy to clipboard the connection one-liner with PORT USER and HOST_URL
(defn users-table []
  "A table with recom"
  (let [col-widths  {:username "150px" :port "100px" :pubkey "200px" :privkey "80px" :actions "75px"}
        u           (re-frame/subscribe [:users])]
    (fn []
      [re-com/v-box
       :gap "20px"
       :children [[data-table u col-widths]
                  ]]
    )
  ))

(defn side-bar []
  [re-com/v-box
   :height "300px"
   :width "120px"
   :children [
              [re-com/button
               :class     "btn btn-default btn-block"
               :on-click  #(re-frame/dispatch [:send-command {:endpoint "users/create"}])
               :label     "create user"]
              [re-com/button :class "btn btn-default btn-block" :label "create bulk"]
              [re-com/button :class "btn btn-default btn-block" :label "delete selected"]
              [re-com/button :class "btn btn-default btn-block" :label "import"]
              [re-com/button :class "btn btn-default btn-block" :label "export"]
              ]
   ]
  )
;; TODO: add a search box
(defn ui []
  (fn []
    [re-com/v-box
     :height "100%"
     :children [
                [re-com/h-box
                 :gap "20px"
                 :children [
                            [re-com/v-box
                             :width "120px"
                             :height "100%"
                             :children [[re-com/v-box
                                         :height "40px"
                                         :children []
                                         ]
                                        [side-bar]
                                        ]
                             ]
                            [re-com/v-box
                             :height "100%"
                             :children [
                                        [re-com/v-box
                                        :height "40px"
                                        :children []
                                        ]
                                        [users-table]
                                        ]
                             ]
                            ]]
                [:div {:id "snackbar"}]
                ]
     ]
    )
  )