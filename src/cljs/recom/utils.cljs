(ns recom.utils
  (:require [re-frame.core :as re-frame]
            [re-com.core :as re-com  :refer-macros [handler-fn]]
            [reagent.core :as reagent]))

(defn copy [text]
  (let [dummy (.createElement js/document "input")]
    (aset dummy "value" text)
    (aset dummy "type" "text")
    (.appendChild (.-body js/document) dummy)
    (.select dummy)
    (.execCommand js/document "copy" dummy)
    ;(.log js/console (str "copied: " text))
    (.removeChild (.-body js/document) dummy)
    )
  )

(defn download-text [filename content]
  (let [dummy (.createElement js/document "a")]
    (aset dummy "href" (str "data:text/plain;charset=utf-8," content))
    (aset dummy "download" filename)
    (.appendChild (.-body js/document) dummy)
    (.click dummy)
    (.removeChild (.-body js/document) dummy)
    )
  )
;(recom.utils/toast "snackbar" "untexto!!" 3000)

(defn toast [container text duration]
  (let [dummy (.getElementById js/document container)]
    (aset dummy "className" "show")
    (aset dummy "innerHTML" text)
    (js/setTimeout (fn []
                     (aset dummy "className" "")
                     (aset dummy "innerHTML" ""))
                   duration)
    )
  )
