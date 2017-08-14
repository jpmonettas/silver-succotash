(ns recom.core
  (:require [org.httpkit.server :as server]
            [org.httpkit.timer :as timer]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clojure.core.async :refer [<! timeout thread go go-loop >!! <!! offer! poll! chan]]
            ))
(def canales (atom {"/users" []
                    ;"/resources" []
                    }))


;; for security reasons, we destroy the key after reading
(defn private_key [n]
  (let [pkey (:out (clojure.java.shell/sh "bash" "-c" (str "cat /home/bhdev/private_keys/" n)))]
    (if pkey
       (clojure.java.shell/sh "bash" "-c" (str "rm /home/bhdev/private_keys/" n)))
    {:user n
     :privkey   pkey
     }
    )
  )

(defn handler [req]
  (let [jeyson (if (:body req) (walk/keywordize-keys (json/read-str (slurp (:body req)))))]
    (println jeyson)
    (if (contains? @canales (:uri req))
      (server/with-channel req channel
                           (swap! canales (fn [m]
                                            (update m (:uri req) conj channel)))
                           (server/send! channel
                                         {:status 200
                                          :headers {"Content-type" "text/event-stream"
                                                    "Access-Control-Allow-Origin" "*"}}
                                         false))
      (if (=(:uri req) "/private_key")
        {:status 405}
        (if (=(:uri req) "/users/private_key")
          {:status 200
           :headers {"Content-type" "application/json"
                     "Access-Control-Allow-Origin" "*"
                     "Access-Control-Allow-Headers" "Content-Type"}
           :body  (json/write-str{:filename (:username jeyson)
                                  :content (private_key (:username jeyson))})
           }
          {:status 404})
        )
      )
    )

  )


(defn publish-disk-usage []
  (doall (map (fn[ch]
  (server/send! ch
     (str "data:"
          (->> (str/split-lines (:out (clojure.java.shell/sh "df" "-h")))
               (str/join "\ndata:"))
          "\n\n")
     false))
  (@canales "/disk-usage"))
  ))

(def not-nil? (complement nil?))

(defn open-ports []
  (map
    (fn [n]
      {:user  (nth n 2)
       :port  (second (str/split (nth n 8) #":")) })
    (filter not-nil? (map
                       (fn[n] (if-not
                                (or (= (nth n 2) "root") (not= (nth n 9) "(LISTEN)") (not= (nth n 4) "IPv4"))
                                n))
                       (filter (fn [n] (= (first n) "sshd"))
                               (map (fn [n] (str/split n #"\s+")) (str/split-lines (:out (clojure.java.shell/sh "bash" "-c" "sudo lsof -i -n"))))
                               )
                       ))))

;; we prefix users so we can id them
(defn users []
  (filter (fn [n] (str/starts-with? n "bh247_"))
          (str/split-lines (:out (clojure.java.shell/sh "bash" "-c" "awk -F':' '{ print $1}' /etc/passwd"))))
  )
(defn port [user]
  (str/replace
  (second
  (str/split
    (first
  (str/split
  (second
    (str/split (:out (clojure.java.shell/sh "bash" "-c"
                                          (str "sudo cat /home/" user "/.ssh/authorized_keys")))
               #"permitopen="))
  #",command"))
    #":"
  ))
  #"\""
  ""
  ))

(port "bh247_mypbifsm")
(users)
(defn public_key [n]
  {:user n
   :pubkey   (second (str/split (:out (clojure.java.shell/sh "bash" "-c"
                                                          (str "cat /home/bhdev/private_keys/" n ".pub"))) #"\s+"))
   :privkey  (= (:exit (clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/" n ))) 0)
   :port   (port n)
   }
  )

 (clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/bh247_afgbad1y" "&& echo true"))

;(public_key "bh247_nszckpbx")
(defn user_keys [] (map public_key (users)))
;; use transit instead of json??
(defn publish-users []
  (doall (map (fn[ch]
                (server/send! ch
                              (str "data:" (json/json-str (user_keys)) "\n\n")
                              false))
              (@canales "/users"))
         ))

;; TODO: use a smart diff function and send! each N if theres changes or each M (M>N)

(def t3 (Thread. (fn []
                   (publish-users)
                   (Thread/sleep 5000)
                   (recur))))
;(.start t)
;(.start t2)
(.start t3)

; #' passes reference instead of copy
(def s (server/run-server #'handler {:port 9094}))
;(s)
;(edn/readstring)
;transit


(defn row [n]
  [:tbody [:tr [:td (n "user")] [:td (n "key")] [:td {:class "text-center"} [:span {:class "glyphicon glyphicon-download-alt text-muted"}]]]]
  )
(row {"user" "bh247_nszckpbx",
      "key" "AAAABjMWj83MdGWcD+JSzM5nygRm0ZPSVT2+mTMQD"})
(defn create-table []
  [:div
   {:class "container-fluid"}
   [:table
    {:class "table table-bordered table-responsive"}
    [:thead [:tr [:th "id"] [:th "Username"] [:th "Port"] [:th "Status"] [:th "Private"] [:th "PubKey"]]]
    [(map (fn [n]
            (row n)
            ) test)]
    ]]
  )