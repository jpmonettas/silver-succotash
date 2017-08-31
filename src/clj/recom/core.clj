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

(def not-nil? (complement nil?))
(defn open-ports []
  (map
    (fn [n]
      {:user  (nth n 2)
       :port  (second (str/split (nth n 8) #":"))
       })
    (filter not-nil?
            (map
              (fn[n] (if-not (or (= (nth n 2) "root") (not= (nth n 9) "(LISTEN)") (not= (nth n 4) "IPv4")) n))
              (filter (fn [n] (= (first n) "sshd")) (map (fn [n] (str/split n #"\s+")) (str/split-lines (:out (clojure.java.shell/sh "bash" "-c" "sudo lsof -i -n")))))
              ))))
(open-ports)
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
    user
    )
  )

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


