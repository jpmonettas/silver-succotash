(ns recom.core
  (:require [org.httpkit.server :as server]
            [org.httpkit.timer :as timer]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clojure.core.async :refer [<! timeout thread go go-loop >!! <!! offer! poll! chan]]
            ))
(def canales (atom {"/disk-usage" []
                    "/sshd" []
                    "/users" []
                    }))
;(println "algo")
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
(private_key "algo")
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
           ;:headers {"Content-type" "text/html; charset=utf-8"
           ;          "Access-Control-Allow-Origin" "*"}
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

(json/write-str {:filename "algo"
               :content "otracosadklasdjnklasdjno"})
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


;(defn open-ports []
;  (map
;    (fn [n]
;      {:user  (nth n 2)
;       :port  (second (str/split (nth n 8) #":")) })
;    (filter not-nil? (map
;                       (fn[n] (if-not
;                                (or (= (nth n 2) "root") (not= (nth n 9) "(LISTEN)") (not= (nth n 4) "IPv4"))
;                                n))
;                       (map
;                         (fn [n] (str/split n #"\s+"))
;                         (str/split-lines (slurp "/home/bhdev/clojure/out"))
;                         )))))

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

(defn publish-sshd []
  (doall (map (fn[ch]
                (server/send! ch
                              (str "data:" (json/json-str (open-ports)) "\n\n")
                              false))
              (@canales "/sshd"))
         ))




;(filter (fn [n] (= (first n) "sshd"))
;        (map (fn [n] (str/split n #"\s+")) (str/split-lines (:out (clojure.java.shell/sh "bash" "-c" "sudo lsof -i -n"))))
;        )

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

;; TODO: use a smart diff functiond and send! each N if theres changes or each M (M>N)
;(def t (Thread. (fn []
;                  (publish-disk-usage)
;                  (Thread/sleep 5000)
;                  (recur))))
;(def t2 (Thread. (fn []
;                  (publish-sshd)
;                  (Thread/sleep 5000)
;                  (recur))))
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

(def test [{"user" "bh247_mypbifsm", "key" nil}
 {"user" "bh247_cxuxnbaj", "key" nil}
 {"user" "bh247_yziymgrm",
  "key"
         "AAAAB3NzaC1yc2EAAAADAQABAAABAQDFZST0Ii2PY3jRgE45A9HLBZCvAPdqVav+F9IPVQpymwi4+YsDts8jcAUzN/5btEUUQx42whCeBxOCJg23rb2sxSpM4PBePh9O0Iw2q+mKQZ3J5RrUzNLAGQhDXg3Dyx9rcSIA7+34/n4oPczEC9t7KzOIUkFnoglhHcPDuGxPMrgwvwx7GYMcRpUphRRp54ian+dubiOw0gg3OnrS4mJcfoJFW0f/CuvUbDk+fETYIJUv5/b1d9kMz95RPnbityT8Sd7iWymvX+o7lkuX8JxxpFs2Z78OsfifKMMrevsPXZ1CLLMfbcutJbMYZtFXLALV7WKw6rD2FPz9xoQGU3LN"}
 {"user" "bh247_afgbadpy",
  "key"
         "AAAAB3NzaC1yc2EAAAADAQABAAABAQDIEyjpyjODq//Ec2rh3jFDCcGA9girrdxnZtxa4rGAYE4yhTqHLLtVco2RBbhq2B7igswhQ0IqcXqAQL+3466EqvonoyFhH8M1W/lUxKitfcvhF66I9CKOpgeC/kJM+HZ/BJlh0mIYHRLTsrytigvP6ZG4rTnvoWHFzBEsG22exOJQDeC2jD3wLea4TXWpdkiJQl67whaPoqqbM9elyvl5q5Hu5kBRY40bg3jgBvzzqxDYPfxBZ04/5ijeL9a/UoPQbJ6+4P9PV+EsfkINOqeRaGsU+Q8DYh/nKrTTuVSYgIIHLvw1YLI6L3Kx1xGycnD/Ezf5IWKLqg2O1feBoXqv"}
 {"user" "bh247_nszckpbx",
  "key"
         "AAAAB3NzaC1yc2EAAAADAQABAAABAQDMXFToYByHQ56IrkrRx7m/0hYCX8JMSn98HInsL/Gffgk35gw6eqZ+YFxkpG1GsMEQZMqe9mfUdQYg7RjgKIa7eaozJwzStovYJFceQhJ73h8ptsHsP6BzQN+gir8PqtMNRwvObL36XyHUC/6twJj5hiINqJVHrhkXBTYPaUVnqWuZLb8e0GU1VKcovdhuNU+CJnccB8rwHf+DGCfpRjC6SK5QPfQs57/OeIvXKM+7e4Pw99YyHeM9GELg5hXYMlPcC4DYi13hR+suuXsBFWuXCF/CMWj83MdGWcD+J6gUL5xqxlf41h0pFRLJx//HVQSzM5nygRm0ZPSVT2+mTMQD"}])
(map (fn [n]
              (row n)
       ) test)
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