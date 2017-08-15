(ns recom.core
  (:require [org.httpkit.server :as server]
            [org.httpkit.timer :as timer]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clojure.data :as data]
            [clojure.set :as set]
            [clojure.core.async :refer [<! timeout thread go go-loop >!! <!! offer! poll! chan]]
            ))
(def canales (atom {"/users" []
                    ;"/resources" []
                    }))
(def users (atom {}))


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

;; TODO: on first contact we send base and then publish only differences
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



(defn list-existing-users []
  (filter
    (fn [n] (str/starts-with? n "bh247_"))
    (str/split-lines (:out (clojure.java.shell/sh "bash" "-c" "awk -F':' '{ print $1}' /etc/passwd")))
    )
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

;(port "bh247_mypbifsm")
;(users)
;(defn public_key [n]
;  {:user n
;   :pubkey   (second (str/split (:out (clojure.java.shell/sh "bash" "-c"
;                                                          (str "cat /home/bhdev/private_keys/" n ".pub"))) #"\s+"))
;   :privkey  (= (:exit (clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/" n ))) 0)
;   :port   (port n)
;   }
;  )
;(clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/bh247_afgbad1y" "&& echo true"))
;(public_key "bh247_nszckpbx")
(defn users-data []
  (map
    (fn [n]
     {:user n
      :pubkey   (second (str/split (:out (clojure.java.shell/sh "bash" "-c"
                                                                (str "cat /home/bhdev/private_keys/" n ".pub"))) #"\s+"))
      :privkey  (= (:exit (clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/" n ))) 0)
      :port   (port n)
      })
    (list-existing-users)
    ))
;; TODO: use transit instead of json??
;; TODO: use a smart diff function and send! each N if theres changes or each M (M>N)
(defn mydiff [old new]
  (let [diff  (data/diff old new)]
    (if (or (first diff) (second diff))
      {:remove (first diff)
       :add (second diff)}
      )
    )
  )

(defn publish-users []
  (let [usrs (users-data)
        diff (mydiff @users usrs)]
    ;; compare usrs with atom and send/save if different
    (reset! users usrs)
    (doall (map (fn[ch]
                  (server/send! ch
                                (str "data:" (json/json-str diff) "\n\n")
                                false))
                (@canales "/users"))
           ))
  )
@users
(reset! users {})

(def t3 (Thread. (fn []
                   (publish-users)
                   (Thread/sleep 5000)
                   (recur))))

(.start t3)

; #' passes reference instead of copy
(def s (server/run-server #'handler {:port 9094}))
;(s)
;(edn/readstring)
;transit


;(defn row [n]
;  [:tbody [:tr [:td (n "user")] [:td (n "key")] [:td {:class "text-center"} [:span {:class "glyphicon glyphicon-download-alt text-muted"}]]]]
;  )

;(defn create-table []
;  [:div
;   {:class "container-fluid"}
;   [:table
;    {:class "table table-bordered table-responsive"}
;    [:thead [:tr [:th "id"] [:th "Username"] [:th "Port"] [:th "Status"] [:th "Private"] [:th "PubKey"]]]
;    [(map (fn [n]
;            (row n)
;            ) test)]
;    ]]
;  )
(def old-db
  (set '(
          {:user "bh247_mypbifsm", :pubkey nil, :privkey false, :port "2345"}
          {:user "bh247_cxuxnbaj", :pubkey nil, :privkey false, :port ""}
          {:user "bh247_yziymgrm",
  :pubkey
        "AAAAB3NzaC1yc2EAAAADAQABAAABAQDFZST0Ii2PY3jRgE45A9HLBZCvAPdqVav+F9IPVQpymwi4+YsDts8jcAUzN/5btEUUQx42whCeBxOCJg23rb2sxSpM4PBePh9O0Iw2q+mKQZ3J5RrUzNLAGQhDXg3Dyx9rcSIA7+34/n4oPczEC9t7KzOIUkFnoglhHcPDuGxPMrgwvwx7GYMcRpUphRRp54ian+dubiOw0gg3OnrS4mJcfoJFW0f/CuvUbDk+fETYIJUv5/b1d9kMz95RPnbityT8Sd7iWymvX+o7lkuX8JxxpFs2Z78OsfifKMMrevsPXZ1CLLMfbcutJbMYZtFXLALV7WKw6rD2FPz9xoQGU3LN",
  :privkey false,
  :port "2228"}
 {:user "bh247_afgbadpy",
  :pubkey
        "AAAAB3NzaC1yc2EAAAADAQABAAABAQDIEyjpyjODq//Ec2rh3jFDCcGA9girrdxnZtxa4rGAYE4yhTqHLLtVco2RBbhq2B7igswhQ0IqcXqAQL+3466EqvonoyFhH8M1W/lUxKitfcvhF66I9CKOpgeC/kJM+HZ/BJlh0mIYHRLTsrytigvP6ZG4rTnvoWHFzBEsG22exOJQDeC2jD3wLea4TXWpdkiJQl67whaPoqqbM9elyvl5q5Hu5kBRY40bg3jgBvzzqxDYPfxBZ04/5ijeL9a/UoPQbJ6+4P9PV+EsfkINOqeRaGsU+Q8DYh/nKrTTuVSYgIIHLvw1YLI6L3Kx1xGycnD/Ezf5IWKLqg2O1feBoXqv",
  :privkey true,
  :port ""}
 {:user "bh247_nszckpbx",
  :pubkey
        "AAAAB3NzaC1yc2EAAAADAQABAAABAQDMXFToYByHQ56IrkrRx7m/0hYCX8JMSn98HInsL/Gffgk35gw6eqZ+YFxkpG1GsMEQZMqe9mfUdQYg7RjgKIa7eaozJwzStovYJFceQhJ73h8ptsHsP6BzQN+gir8PqtMNRwvObL36XyHUC/6twJj5hiINqJVHrhkXBTYPaUVnqWuZLb8e0GU1VKcovdhuNU+CJnccB8rwHf+DGCfpRjC6SK5QPfQs57/OeIvXKM+7e4Pw99YyHeM9GELg5hXYMlPcC4DYi13hR+suuXsBFWuXCF/CMWj83MdGWcD+J6gUL5xqxlf41h0pFRLJx//HVQSzM5nygRm0ZPSVT2+mTMQD",
  :privkey true,
  :port ""})))

(def new-db
  (set '({:user "bh247_mypbifsm", :pubkey nil, :privkey false, :port "q23"},
   {:user "bh247_yziymgrm",
    :pubkey
          "AAAAB3NzaC1yc2EAAAADAQABAAABAQDFZST0Ii2PY3jRgE45A9HLBZCvAPdqVav+F9IPVQpymwi4+YsDts8jcAUzN/5btEUUQx42whCeBxOCJg23rb2sxSpM4PBePh9O0Iw2q+mKQZ3J5RrUzNLAGQhDXg3Dyx9rcSIA7+34/n4oPczEC9t7KzOIUkFnoglhHcPDuGxPMrgwvwx7GYMcRpUphRRp54ian+dubiOw0gg3OnrS4mJcfoJFW0f/CuvUbDk+fETYIJUv5/b1d9kMz95RPnbityT8Sd7iWymvX+o7lkuX8JxxpFs2Z78OsfifKMMrevsPXZ1CLLMfbcutJbMYZtFXLALV7WKw6rD2FPz9xoQGU3LN",
    :privkey false,
    :port "2228"},
   {:user "bh247_afgbadpy",
    :pubkey
          "AAAAB3NzaC1yc2EAAAADAQABAAABAQDIEyjpyjODq//Ec2rh3jFDCcGA9girrdxnZtxa4rGAYE4yhTqHLLtVco2RBbhq2B7igswhQ0IqcXqAQL+3466EqvonoyFhH8M1W/lUxKitfcvhF66I9CKOpgeC/kJM+HZ/BJlh0mIYHRLTsrytigvP6ZG4rTnvoWHFzBEsG22exOJQDeC2jD3wLea4TXWpdkiJQl67whaPoqqbM9elyvl5q5Hu5kBRY40bg3jgBvzzqxDYPfxBZ04/5ijeL9a/UoPQbJ6+4P9PV+EsfkINOqeRaGsU+Q8DYh/nKrTTuVSYgIIHLvw1YLI6L3Kx1xGycnD/Ezf5IWKLqg2O1feBoXqv",
    :privkey true,
    :port ""},
   {:user "bh247_nszckpbx",
    :pubkey
          "AAAAB3NzaC1yc2EAAAADAQABAAABAQDMXFToYByHQ56IrkrRx7m/0hYCX8JMSn98HInsL/Gffgk35gw6eqZ+YFxkpG1GsMEQZMqe9mfUdQYg7RjgKIa7eaozJwzStovYJFceQhJ73h8ptsHsP6BzQN+gir8PqtMNRwvObL36XyHUC/6twJj5hiINqJVHrhkXBTYPaUVnqWuZLb8e0GU1VKcovdhuNU+CJnccB8rwHf+DGCfpRjC6SK5QPfQs57/OeIvXKM+7e4Pw99YyHeM9GELg5hXYMlPcC4DYi13hR+suuXsBFWuXCF/CMWj83MdGWcD+J6gUL5xqxlf41h0pFRLJx//HVQSzM5nygRm0ZPSVT2+mTMQD",
    :privkey true,
    :port ""})))


;(mydiff old-db new-db)
;(mydiff old-db old-db)
;{:remove (first (data/diff old-db new-db))
; :add (second (data/diff old-db new-db))}
;
;
;{:remove (first (data/diff new-db new-db))
; :add (second (data/diff new-db new-db))}