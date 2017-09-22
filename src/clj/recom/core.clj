(ns recom.core
  (:import java.security.SecureRandom)
  (:require [org.httpkit.server :as server]
            [org.httpkit.timer :as timer]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clojure.data :as data]
            [clojure.set :as set]
            [clojure.core.async :refer [<! timeout thread go go-loop >!! <!! offer! poll! chan]]
            [compojure.route :refer [files not-found]]
            [compojure.handler :refer [site]] ; form, query params decode; cookie; session, etc
            [compojure.core :refer [routes GET POST DELETE OPTIONS ANY context]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.util.response :as resp]
            [hiccup.page :as h]
            [hiccup.element :as e]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            ))
(def canales (atom {"/users" []
                    ;"/resources" []
                    }))
;; TODO: add add a timestamp to the atom so the client knows if something is missing
(def users (atom {}))

;; for security reasons, we destroy the key after reading
(defn private_key [n]
  (let [pkey (:out (clojure.java.shell/sh "bash" "-c" (str "cat /home/bhdev/private_keys/" n)))]
    (if-not (str/blank? pkey)
      (do
        (clojure.java.shell/sh "bash" "-c" (str "rm /home/bhdev/private_keys/" n))
        {:user n
         :privkey   pkey
         })
      )
    )
  )
;; TODO: we should also delete /home/<user> folder
(defn delete-user [username]
  (clojure.java.shell/sh "bash" "-c"
                         (str "sudo userdel -f " username))
  )

(defn line->port [[proc _ user _ proto _ _ _ full-port status]]
  {:proc proc
   :proto proto
   :user user
   :port (second (str/split full-port #":"))
   :open? (= status "(LISTEN)")})

(defn lines->ports [lsof-lines]
  (->> lsof-lines
       (map #(str/split % #"\s+"))
       (map line->port)))

(defn open-ssh-connections [connections]
  (filter (fn [{:keys [proc proto user port open?]}]
            (and (= proc "sshd")
                 (not= user "root")
                 (= proto "IPv4")
                 open?))
          connections))

(defn list-ssh-connections []
  (-> (clojure.java.shell/sh "bash" "-c" "sudo lsof -i -n")
    :out
    str/split-lines
    lines->ports
    open-ssh-connections
    ))


(defn is-active-port [ssh-connections user port]
  (contains? ssh-connections
             {:proc "sshd", :proto "IPv4", :user user, :port port, :open? true})
  )
;(is-active-port (set (list-ssh-connections)) "bh247_xcuhgzvr" "2291")
;(contains? (set (list-ssh-connections))
;      {:proc "sshd", :proto "IPv4", :user "bh247_xcuhgzvr", :port "2291", :open? true})
(defn user-exists [user]
  (=(:exit (clojure.java.shell/sh "bash" "-c"
                                  (str "getent passwd " user " > /dev/null"))) 0)
  )

(defn rand-string
  ([] (rand-string 8))
  ([n]
   (let [chars-between #(map char (range (int %1) (inc (int %2))))
         chars (concat
                 (chars-between \a \z)
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
  (let [content (str
                  "no-pty,no-X11-forwarding,permitopen=\"localhost:"
                  port
                  "\",command=\"/bin/echo do-not-send-commands\""
                  ", ssh-rsa "
                  pubkey)]
    (clojure.java.shell/sh "bash" "-c"
                           (str "sudo adduser --disabled-password --gecos '' '" user "'" ))
    (clojure.java.shell/sh "bash" "-c" (str "sudo mkdir /home/" user "/.ssh"))
    (clojure.java.shell/sh "bash" "-c" (str "sudo touch /home/" user "/.ssh/authorized_keys"))
    (clojure.java.shell/sh "bash" "-c" (str "sudo tee /home/" user "/.ssh/authorized_keys") :in content)
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


(defn list-existing-users []
  (filter
    #(str/starts-with? % "bh247_")
    (->>
      (clojure.java.shell/sh "bash" "-c" "awk -F':' '{ print $1}' /etc/passwd")
      (:out)
      (str/split-lines)
      )
    )
  )
;(list-existing-users)
(defn port [user]
  (->> user
       (#(str "sudo cat /home/" % "/.ssh/authorized_keys"))
       (clojure.java.shell/sh "bash" "-c")
       (:out)
       (#(str/split % #"permitopen="))
       (second)
       (#(str/split % #",command"))
       (first)
       (#(str/split % #":"))
       (second)
       (#(str/replace % #"\"" ""))
       )
  )
(port "bh247_bntdaitw")

(defn users-data []
  (let [open (list-ssh-connections)]
    (doall (map
             (fn [n]
               {:user n
                :pubkey   (second (str/split (:out (clojure.java.shell/sh "bash" "-c"
                                                                          (str "cat /home/bhdev/private_keys/" n ".pub"))) #"\s+"))
                :privkey  (= (:exit (clojure.java.shell/sh "bash" "-c" (str "test -f /home/bhdev/private_keys/" n ))) 0)
                :port   (port n)
                :active (is-active-port (set (list-ssh-connections)) n (port n))
                })
             (list-existing-users)
             ))
    )
  )


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
;@users
;(reset! users {})

(def t3 (Thread. (fn []
                   (publish-users)
                   (Thread/sleep 5000)
                   (recur))))

(.start t3)

; #' passes reference instead of copy
;(def s (server/run-server #'handler {:port 9094}))
;(s)
;(edn/readstring)
;transit


(defn show-users [req]
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
                       ))

(defn get-private-key [req]
  (let [params (:params req)
        privkey (private_key (:id params))]
    (if privkey
      {:status  200
       :headers {"Content-type" "application/json"}
       :body    (json/write-str {:filename (:id params)
                                 :content  privkey})
       }
      {:status  404}
      )
    )
  )
(defn handle-create-user [req]
    {:status 200
     :headers {"Content-type" "application/json"
               "Access-Control-Allow-Origin" "*"}
     :body  (json/write-str{:success (create-user)})
     })

(defn handle-delete-user [req]
  (let [params (:params req)]
    (println (str "deleted: " (:id params)))
    {:status  200
     :headers {"Content-type" "application/json"}
     :body    (json/write-str {:success (delete-user (:id params))})}
    )
  )
(def tokens->users (atom {}))
;; TODO: secure random token (ex hashing a random number)
;; TODO: assigning session id (auth= id:token) and time validity to token
;;

(defn random-bytes
  [length]
  (let [gen (new SecureRandom) key (byte-array length)]
    (.nextBytes gen key)
    key))

(defn- hexify [bs]
  (let [hex [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \a \b \c \d \e \f]]
    (letfn [(hexify-byte [b]
              (let [v (bit-and b 0xFF)]
                [(hex (bit-shift-right v 4)) (hex (bit-and v 0x0F))]))]
      (apply str (mapcat hexify-byte bs)))))


;; TODO: use :expires instead of timestamp
(defn login [creds]
  (println "login")
  (let [user (:user creds)
        pass (:pass creds)]
    (if (and (= pass "pass") (= user "admin"))
      (let [token (hexify (random-bytes 32))
            timestamp (quot (System/currentTimeMillis) 1000)]
        (swap! tokens->users assoc token {:user user :timestamp timestamp})
        {:status 200
         :body token})
      {:status 403})
    )
  )
;(login "admin" "pass")
@tokens->users
;; TODO: increase :expires each time we succeed
(defn token-auth-mid [next-h]
  (fn [req]
    (println req)
    (let [token (if (= (:uri req) "/users")
                   (get-in req [:params "token-auth"]) ;; EvSource doesnt handle headers well
                   (get-in req [:headers "token-auth"]))]
      (if (contains? @tokens->users token)
        (next-h req)
        {:status 403})
      )))
;; TODO: destroy the token
(defn logout [req]
  "logout")

;; TODO: on first contact we send base and then publish only differences
;; TODO: be sure we have a POST so we dont execute on OPTIONS and then on POST
(def private-routes
  (routes
    (GET "/logout" req logout)
    (GET "/users" [] show-users)
    (POST "/users/create" req handle-create-user)
    (context "/user/:id" []
      (GET "/private_key" [] get-private-key)
      (POST "/delete" [] handle-delete-user)
      )
    )) ;; all other, return 404
(def public-routes
  (routes
    (POST "/login" req (login (clojure.walk/keywordize-keys (:json-params req))))
    (GET "/" req "Home")
    )
  )

(def app
  (wrap-cors
    (-> (routes
          public-routes
          (-> private-routes
              token-auth-mid)
          (not-found {:status 404})
          )
        wrap-json-params
        wrap-params
        )
  :access-control-allow-origin [#".*"]
  :access-control-allow-methods [:get :put :post :delete])
  )
(app {:request-method :get
      :uri "/"})
;(app {:request-method :post
;        :uri "/login"
;        :query-string "user=admin&pass=pass"})
(app {:request-method :post
      :uri "/login"
      :json-params {:user "admin" :pass "pass"}})
(app {:request-method :get
        :uri "/logout"
        :headers {"token-auth" "04a01a9e2490d7feb2935f10a212369f0183d18aa806fde084b498ec3b0dba85"}})



(def serv (server/run-server #'app {:port 9094}))
;(serv)
