(ns recom.views
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com  :refer-macros [handler-fn]]
            [reagent.core :as reagent]))
(def click-count (reagent/atom 0))
(defn title []
  (let [rx (re-frame/subscribe [:pubkey])]
    (fn []
      [re-com/p @rx ])))

(defn sshd []
  (let [conns (re-frame/subscribe [:connections])]
    (fn []
      [re-com/p @conns])))


;(defn modal []
;  [:div
;   {:id "myModal" :class "modal fade" :role "dialog"}
;   [:div
;    {:class "modal-dialog"}
;    [:div
;     {:class "modal-content"}
;     [:div
;      {:class "modal-header"}
;      [:h4 {:class "modal-title"}] "modal header"
;      [:input
;       {:value \u00D7
;        :type "button"
;        :class "close"
;        :data-dismiss "modal"
;        }
;       ]
;      ]
;     [:div
;      {:class "modal-body"}
;      [:p "Some text for the body"]
;      ]
;     [:div
;      {:class "modal-footer"}
;      [:button
;       {:type "button"
;        :class "btn btn-danger"
;        :data-dismiss "modal"
;        :on-click #(swap! click-count dec)
;        }
;       "Cancel "
;       [:span {:class "glyphicon glyphicon-remove"}]]
;      [:button
;       {:type "button"
;        :class "btn btn-success"
;        :data-dismiss "modal"
;        :on-click #(swap! click-count inc)}
;       "OK "
;       [:span {:class "glyphicon glyphicon-ok"}]]
;      ]
;     ]
;    ]
;   ])


(defn public_key []
  (let [key (re-frame/subscribe [:pubkey])]
    [:div
   {:id "myModal" :class "modal" :role "dialog"}
   [:div
    {:class "modal-dialog"}
    [:div
     {:class "modal-content"}
     [:div
      {:class "modal-header"}
      [:h4 {:class "modal-title"}] "public"
      [:input
       {:value \u00D7
        :type "button"
        :class "close"
        :data-dismiss "modal"
        }
       ]
      ]
     [:div
      {:class "modal-body"}
      [:p {:class "container"} @key]
      ]
     ]
    ]
   ]))

(defn modal_open [title]
  [:input {:type "button" :value title :class "btn btn-info btn-lg"
           :data-toggle "modal" :data-target "#myModal"}])
(defn show_public[n]
  (re-frame/dispatch [:pubkey n])
  )
(defn row [i n]
  (if (nil? n)
    [:tr {:key i}
     [:td ""]
             [:td ""]
             [:td {:class "text-center"} [:span {:class "glyphicon glyphicon-download-alt text-muted"}]]
             [:td {:class "text-center"} [:span {:class "glyphicon glyphicon-search"}]]
             ]
    [:tr
     {:key i}
     [:td (:user n)]
             [:td (:port n)]
             [:td {:class "text-center"} [:a {:data-toggle "modal" :data-target "#myModal"} [:span {:class (str "glyphicon glyphicon-download-alt " (if (nil? (:pubkey n)) "text-muted"))}]]]
             [:td {:class "text-center"} [:a {:data-toggle "modal" :data-target "#myModal" :on-click #(show_public (:pubkey n)) }[:span {:class "glyphicon glyphicon-search"}]]]
             ]
    ))
;; glyphicon glyphicon-search

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
(defn enumerate
  "(for [[index item first? last?] (enumerate coll)] ...)  "
  [coll]
  (let [c (dec (count coll))
        f (fn [index item] [item])]
    (map-indexed f coll)))


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

(defn create-table []
  (let [u (re-frame/subscribe [:users])]
    (fn []
      [:div
   {:class "container-fluid"}
   [:table
    {:class "table table-bordered table-responsive"}
    [:thead [:tr [:th "Username"] [:th "Port"] [:th "Private"] [:th "PubKey"]]]
    [:tbody (map-indexed row @u)]
    ]]))
  )
;(:users @re-frame.db/app-db.)
;; TODO: show forwarding status and allow to sort/filter
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

(defn ui []
  (fn []
    [re-com/v-box
     :height "100%"
     :children [
                [re-com/h-box
                 :gap "20px"
                 :children [
                            [re-com/v-box
                             :height "100%"
                             :children [
                                        [:h1 "nav"]]
                             ]
                            [re-com/v-box
                             :height "100%"
                             :children [
                                        [:h1 "Forwarding:"]
                                        [users-table]
                                        ]
                             ]
                            ]]
                [:div {:id "snackbar"}]
                ]
     ]
    )
  )