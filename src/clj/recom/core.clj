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
;; TODO: add add a timestamp to the atom so the client knows if something is missing
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



(defn delete-user [username]
  (clojure.java.shell/sh "bash" "-c"
                         (str "sudo userdel -f " username))
  )



(defn user-exists [user]
  (=(:exit (clojure.java.shell/sh "bash" "-c"
                                  (str "getent passwd " user " > /dev/null"))) 0)
  )
(defn rand-string
  ([] (rand-string 8))
  ([n]
   (let [chars-between #(map char (range (int %1) (inc (int %2))))
         chars (concat
                 ;(chars-between \0 \9)
                       (chars-between \a \z)
                       ;(chars-between \A \Z)
                       ;[\_]
                       )
         password (take n (repeatedly #(rand-nth chars)))]
     (reduce str password)
     )
    ))

;(user-exists "bh247_qxblzaez")
;(user-exists "bh247_muyhfkny")
;(rand-string)
(defn new-username []
  (let [uname (str "bh247_" (rand-string))]
    (if
      (user-exists uname)
      (new-username)
      uname))
  )

(defn grab-a-port []
  (let [port (Integer. (slurp "last_port"))]
    (spit "last_port" (inc port))
    port)
  )
;(slurp "last_port")
;(spit "last_port" "2250")

(grab-a-port)

(defn new-key-pair [username]
  (if (= (:exit (clojure.java.shell/sh "bash" "-c"
                         (str "ssh-keygen -t rsa -f /home/bhdev/private_keys/"
                              username
                              " -q -N ''")
                         )) 0)
    (:out (clojure.java.shell/sh "bash" "-c"
                         (str "cat /home/bhdev/private_keys/" username ".pub | cut -d ' ' -f 2"))))
  )

;; TODO: change permissions for .ssh and .ssh/authorized_keys to avoid being writable by the remote user
;sudo -H -u $U bash -c 'cd ~;mkdir .ssh;chmod 700 .ssh;touch .ssh/authorized_keys;chmod 600 .ssh/authorized_keys'

(defn add-user [user pubkey port]
  (let [content (str "no-pty,no-X11-forwarding,permitopen=\"localhost:"
                     port
                     "\",command=\"/bin/echo do-not-send-commands\" ssh-rsa "
                     pubkey)]
  (clojure.java.shell/sh "bash" "-c"
                         (str "sudo adduser --disabled-password --gecos '' '" user "'" ))
  (clojure.java.shell/sh "bash" "-c" (str "sudo mkdir /home/" user "/.ssh"))
  (clojure.java.shell/sh "bash" "-c" (str "sudo touch /home/" user "/.ssh/authorized_keys"))
  (clojure.java.shell/sh "bash" "-c" (str "echo \""content "\"|sudo tee /home/" user "/.ssh/authorized_keys"))
  ))

(defn create-user []
  (let [user (new-username)
        pubkey (new-key-pair user)
        port (grab-a-port)
        ]
    (add-user user pubkey port)
    )
  key
  )
;(create-user)
;(new-username)
;(new-key-pair (new-username))

;; TODO: use transit instead of json??
;; TODO: use a smart diff function and send! each N if theres changes or each M (M>N)
;; TODO: each M we send a full "frame" to let missing clients resync
(defn mydiff [old new]
  (let [diff  (data/diff old new)]
    (if (or (first diff) (second diff))
      {:remove (first diff)
       :add (second diff)}
      )
    )
  )
;; TODO: on first contact we send base and then publish only differences
(defn handler [req]
  (let [jeyson (if (:body req) (walk/keywordize-keys (json/read-str (slurp (:body req)))))]
    ;(println jeyson)
    (if (contains? @canales (:uri req))
      (server/with-channel req channel
                           (swap! canales (fn [m]
                                            (update m (:uri req) conj channel)))
                           (server/send! channel
                                         {:status 200
                                          :headers {"Content-type" "text/event-stream"
                                                    "Access-Control-Allow-Origin" "*"}}
                                         false)
                           (server/send! channel
                                         (str "data:" (json/json-str (mydiff {} @users)) "\n\n")
                                         false)
                           )
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
          (if (=(:uri req) "/users/delete")
            {:status 200
             :headers {"Content-type" "application/json"
                       "Access-Control-Allow-Origin" "*"
                       "Access-Control-Allow-Headers" "Content-Type"}
             :body (json/write-str{:success (delete-user (:username jeyson))})}
            (if (=(:uri req) "/users/create")
              {:status 200
               :headers {"Content-type" "application/json"
                         "Access-Control-Allow-Origin" "*"
                         "Access-Control-Allow-Headers" "Content-Type"}
               :body (json/write-str{:success (create-user)})}
              {:status 404}))
          )
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
  (let [line (second (str/split (:out (clojure.java.shell/sh "bash" "-c" (str "sudo cat /home/" user "/.ssh/authorized_keys")))#"permitopen="))]
    (if (not (nil? line))
      (str/replace
        (second
          (str/split
            (first
              (str/split
                    line
                #",command"))
            #":"
            ))
        #"\""
        ""
        )
      )
    )
)


(defn users-data []
  (doall (map
    (fn [n]
     {:user n
      :pubkey   (second (str/split (:out (clojure.java.shell/sh "bash" "-c"
                                                                (str "cat /home/bhdev/private_keys/" n ".pub"))) #"\s+"))
      :privkey  (= (:exit (clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/" n ))) 0)
      :port   (port n)
      })
    (list-existing-users)
    )))


(defn publish-users []
  (let [usrs (users-data)
        diff (mydiff (set @users) (set usrs))]
    ;; compare usrs with atom and send/save if different
    (if
      (nil? diff)
      nil
      (do
        (reset! users usrs)
        (doall (map (fn[ch]
                      (server/send! ch
                                    (str "data:" (json/json-str diff) "\n\n")
                                    false))
                    (@canales "/users"))
               ))
      )

    )
  )
@users
;(reset! users {})

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



;(def new-db
;  '({:user "bh247_yziymgrm",
;    :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQDFZST0Ii2PY3jRgE45A9HLBZCvAPdqVav+F9IPVQpymwi4+YsDts8jcAUzN/5btEUUQx42whCeBxOCJg23rb2sxSpM4PBePh9O0Iw2q+mKQZ3J5RrUzNLAGQhDXg3Dyx9rcSIA7+34/n4oPczEC9t7KzOIUkFnoglhHcPDuGxPMrgwvwx7GYMcRpUphRRp54ian+dubiOw0gg3OnrS4mJcfoJFW0f/CuvUbDk+fETYIJUv5/b1d9kMz95RPnbityT8Sd7iWymvX+o7lkuX8JxxpFs2Z78OsfifKMMrevsPXZ1CLLMfbcutJbMYZtFXLALV7WKw6rD2FPz9xoQGU3LN",
;    :privkey false,
;    :port "2228"}
;    {:user "bh247_oyuljuis",
;     :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQCySof7Gx0wCcaZ3EIz/zWtFlvQTxFDZq6V5I/ZSbLUsCHRBNwttBWYDT/bMCvujUuYBSxObejBvlR9wNLxqhpGjZBIuJXltjqq6wrZ49Il7UKBbNOazoRKS/YXiuU6i9DqfUZlMv0+eIANtL4IBz1aHYEy+cPBgmfGjsTYbWniYg9Yr7aONWCZJuv5R9kRDLUmB920xdarBuRQIM9huvfgWXhVtDbJT0fya9SjTLwz5ClWVvzejb52WqYZRFfjdxTdyy1sBQGWNpyTkGuu+yJq6S3NvxZKAJdyS7Q/vVCBOqtvePKXV2jrp+pl0STxArFnYC5fedLL2WDROIWpOJp1",
;     :privkey true,
;     :port ""}
;    {:user "bh247_viizuyvm",
;     :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQC7dRto96AYxqr9GDtcbv+IWPz+xd6DW5IRPlTFBPWnKEqN31Kn4hba9d4fABvoCNi+ssI8ybzK8Gk4T1v06AdguglyAMGqfLDj7PAxsxK1mi8DfNsqG6F4jWPxMjfZrjJVRV6II3xelOD3JZd3AEsUGSE3M02jGcSJ4XQGD0qa6qZcpYnKiSS/IqyI+s9Rfj4F5s3PkZMhSAbn2r0kivd3C0saWoDwS2iX29Z4eK9oFVZbOAZdU/10UnEFGy/3JVMFQDw/OfzW8OI5WXx0/Iwur+jxcl5K9KbBnWKoru6YrBW+pT+CT5hrUqhweng0yYojHwaz89gRpq1B4MpfkmaF",
;     :privkey true,
;     :port ""}
;    {:user "bh247_fcgwsbbc",
;     :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQDJju1zUSru14aD6B2TNQYS0mr1nop14fz4nKJS63VdfEG1QtDPlRWfyxDx6p8h3rOVbDd/hZoovU/y2PDVRKxz/MQDGbRiwG7DI9+ynNAsX+dH6KGC2Y4sG+7OxylMZtDCrOFpoml3hKjjAgmE6Y6ch1TswJ/JaxeEQ4zyWG4j4ii5lxANI45F6x0Ou0sj60xCfGEoDvMgfOFVWpSISyYrUeYaMiaLM+28IXLrIY4qjqe+jFGJQytskw6OhGopk4/oJE9w7xT2CuEw7ThtKZpiOHNfm3iqSQeRcZ8I+NwbbKqzNlogOYiPF3aKtlCYw80cz8aWp3ayBEFkjR+ANTez",
;     :privkey true,
;     :port ""}
;    {:user "bh247_mpodogcb",
;     :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQC+h2tayHrQVVoKXcaeYXLg5mONxP+FlZ2aSSN7Hbu9Q6GfR4x8ICRon+k3Ueja5f77Xa5FfwPzW27JF0HYltGfAarlOxjObihgtwHB/vsN3DjIHfZCGoLv1Nsbpj1Ax+SX2PmDP3jbx3Gc2v+P/N6u0BGKZbZF0DzQmb3RAMrwHamRFl8iCUddH/OLQteknAC0y3new45N//7I7anBTz1Bc1/ehFNwNdziMv87jxsQRtpYv1TtcLMrOroAkeEE2LQTO6WM98P/Ip1imJlh9vk/Um3FuycEZlacgr2cR007S4ACwpRyt3vgsHGpLkjlli4koPNpeQbt+FbYsdgK1iPr",
;     :privkey true,
;     :port "2232"})
;  )
;
;(def old-db '({:user "bh247_yziymgrm",
;  :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQDFZST0Ii2PY3jRgE45A9HLBZCvAPdqVav+F9IPVQpymwi4+YsDts8jcAUzN/5btEUUQx42whCeBxOCJg23rb2sxSpM4PBePh9O0Iw2q+mKQZ3J5RrUzNLAGQhDXg3Dyx9rcSIA7+34/n4oPczEC9t7KzOIUkFnoglhHcPDuGxPMrgwvwx7GYMcRpUphRRp54ian+dubiOw0gg3OnrS4mJcfoJFW0f/CuvUbDk+fETYIJUv5/b1d9kMz95RPnbityT8Sd7iWymvX+o7lkuX8JxxpFs2Z78OsfifKMMrevsPXZ1CLLMfbcutJbMYZtFXLALV7WKw6rD2FPz9xoQGU3LN",
;  :privkey false,
;  :port "2228"}
;  {:user "bh247_nszckpbx",
;   :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQDMXFToYByHQ56IrkrRx7m/0hYCX8JMSn98HInsL/Gffgk35gw6eqZ+YFxkpG1GsMEQZMqe9mfUdQYg7RjgKIa7eaozJwzStovYJFceQhJ73h8ptsHsP6BzQN+gir8PqtMNRwvObL36XyHUC/6twJj5hiINqJVHrhkXBTYPaUVnqWuZLb8e0GU1VKcovdhuNU+CJnccB8rwHf+DGCfpRjC6SK5QPfQs57/OeIvXKM+7e4Pw99YyHeM9GELg5hXYMlPcC4DYi13hR+suuXsBFWuXCF/CMWj83MdGWcD+J6gUL5xqxlf41h0pFRLJx//HVQSzM5nygRm0ZPSVT2+mTMQD",
;   :privkey true,
;   :port ""}
;  {:user "bh247_oyuljuis",
;   :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQCySof7Gx0wCcaZ3EIz/zWtFlvQTxFDZq6V5I/ZSbLUsCHRBNwttBWYDT/bMCvujUuYBSxObejBvlR9wNLxqhpGjZBIuJXltjqq6wrZ49Il7UKBbNOazoRKS/YXiuU6i9DqfUZlMv0+eIANtL4IBz1aHYEy+cPBgmfGjsTYbWniYg9Yr7aONWCZJuv5R9kRDLUmB920xdarBuRQIM9huvfgWXhVtDbJT0fya9SjTLwz5ClWVvzejb52WqYZRFfjdxTdyy1sBQGWNpyTkGuu+yJq6S3NvxZKAJdyS7Q/vVCBOqtvePKXV2jrp+pl0STxArFnYC5fedLL2WDROIWpOJp1",
;   :privkey true,
;   :port ""}
;  {:user "bh247_viizuyvm",
;   :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQC7dRto96AYxqr9GDtcbv+IWPz+xd6DW5IRPlTFBPWnKEqN31Kn4hba9d4fABvoCNi+ssI8ybzK8Gk4T1v06AdguglyAMGqfLDj7PAxsxK1mi8DfNsqG6F4jWPxMjfZrjJVRV6II3xelOD3JZd3AEsUGSE3M02jGcSJ4XQGD0qa6qZcpYnKiSS/IqyI+s9Rfj4F5s3PkZMhSAbn2r0kivd3C0saWoDwS2iX29Z4eK9oFVZbOAZdU/10UnEFGy/3JVMFQDw/OfzW8OI5WXx0/Iwur+jxcl5K9KbBnWKoru6YrBW+pT+CT5hrUqhweng0yYojHwaz89gRpq1B4MpfkmaF",
;   :privkey true,
;   :port ""}
;  {:user "bh247_fcgwsbbc",
;   :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQDJju1zUSru14aD6B2TNQYS0mr1nop14fz4nKJS63VdfEG1QtDPlRWfyxDx6p8h3rOVbDd/hZoovU/y2PDVRKxz/MQDGbRiwG7DI9+ynNAsX+dH6KGC2Y4sG+7OxylMZtDCrOFpoml3hKjjAgmE6Y6ch1TswJ/JaxeEQ4zyWG4j4ii5lxANI45F6x0Ou0sj60xCfGEoDvMgfOFVWpSISyYrUeYaMiaLM+28IXLrIY4qjqe+jFGJQytskw6OhGopk4/oJE9w7xT2CuEw7ThtKZpiOHNfm3iqSQeRcZ8I+NwbbKqzNlogOYiPF3aKtlCYw80cz8aWp3ayBEFkjR+ANTez",
;   :privkey true,
;   :port ""}
;  {:user "bh247_mpodogcb",
;   :pubkey "AAAAB3NzaC1yc2EAAAADAQABAAABAQC+h2tayHrQVVoKXcaeYXLg5mONxP+FlZ2aSSN7Hbu9Q6GfR4x8ICRon+k3Ueja5f77Xa5FfwPzW27JF0HYltGfAarlOxjObihgtwHB/vsN3DjIHfZCGoLv1Nsbpj1Ax+SX2PmDP3jbx3Gc2v+P/N6u0BGKZbZF0DzQmb3RAMrwHamRFl8iCUddH/OLQteknAC0y3new45N//7I7anBTz1Bc1/ehFNwNdziMv87jxsQRtpYv1TtcLMrOroAkeEE2LQTO6WM98P/Ip1imJlh9vk/Um3FuycEZlacgr2cR007S4ACwpRyt3vgsHGpLkjlli4koPNpeQbt+FbYsdgK1iPr",
;   :privkey true,
;   :port "2232"}))
;old-db
;
;

;
;(nil? (mydiff (set old-db) (set new-db)))
;(nil? (mydiff (set old-db) (set old-db)))
;(set/join old-db new-db)
;
;(def dif {:remove #{{:user "bh247_cxuxnbaj", :pubkey nil, :privkey false, :port ""}
;                    {:user "bh247_mypbifsm", :pubkey nil, :privkey false, :port "2345"}},
;          :add #{{:user "bh247_mypbifsm", :pubkey nil, :privkey false, :port "q23"}}}
;  )
;(def data {:users old-db})
;(set (:add dif))
;(set/union
;  (set/difference (:users data) (:remove dif))
;  (:add dif)
;  )
;
;(disj (:users data) {:user "bh247_cxuxnbaj", :pubkey nil, :privkey false, :port ""})
;(assoc db :users (set/union (:users db) add))
;(assoc data :users old-db )
;data
;(mydiff old-db old-db)
;{:remove (first (data/diff old-db new-db))
; :add (second (data/diff old-db new-db))}
;
;
;{:remove (first (data/diff new-db new-db))
; :add (second (data/diff new-db new-db))}