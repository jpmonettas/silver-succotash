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
            [compojure.route :refer [files not-found]]
            [compojure.handler :refer [site]] ; form, query params decode; cookie; session, etc
            [compojure.core :refer [defroutes GET POST DELETE OPTIONS ANY context]]
            [ring.middleware.cors :refer [wrap-cors wrap-params]]
            [cemerick.friend :as friend]
            [ring.util.response :as resp]
            [hiccup.page :as h]
            [hiccup.element :as e]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
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



(def login-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h3 "Login"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      [:div "Username" [:input {:type "text" :name "username"}]]
      [:div "Password" [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Login"}]]]]]])


;; TODO: on first contact we send base and then publish only differences
;; TODO: be sure we have a POST so we dont execute on OPTIONS and then on POST
(defroutes all-routes
           (GET "/" req
                (h/html5
                    [:p (if-let [identity (friend/identity req)]
                          (apply str "Logged in, with these roles: "
                                 (-> identity friend/current-authentication :roles))
                          "anonymous user")]
                    login-form

                    [:ul [:li (e/link-to "/role-user" "Requires the `user` role")]
                     [:li (e/link-to "/role-admin" "Requires the `admin` role")]
                     [:li (e/link-to  "/requires-authentication"
                                     "Requires any authentication, no specific role requirement")]]
                    [:p (e/link-to "/logout" "Click here to log out") "."]))
           (GET "/login" req (h/html5 login-form))
           (GET "/logout" req
                (friend/logout* (resp/redirect (str (:context req) "/"))))
           (GET "/requires-authentication" req
                (friend/authenticated "Thanks for authenticating!"))
           (GET "/role-user" req
                (friend/authorize #{::user} "You're a user!"))
           (GET "/role-admin" req
                (friend/authorize #{::admin} "You're an admin!"))
           (GET "/users" [] show-users)
           (POST "/users/create" req (friend/authenticated handle-create-user))
           (context "/user/:id" []
                    (GET "/private_key" [] get-private-key)
                    (POST "/delete" [] handle-delete-user)
                    )

           ;(files "/static/") ;; static file url prefix /static, in `public` folder
           (not-found "<p>Page not found.</p>")) ;; all other, return 404


(def handler
(wrap-cors (site all-routes) :access-control-allow-origin [#".*"]
           :access-control-allow-methods [:get :put :post :delete]))



(def tokens->users (atom {}))

(def secured-app
  (wrap-cors
    (site
      (friend/authenticate all-routes
         {:allow-anon? true
          :unauthenticated-handler #(workflows/http-basic-deny "Friend demo" %)
          :workflows [(workflows/http-basic
                        :credential-fn #(creds/bcrypt-credential-fn @users2 %)
                        :realm "Friend demo")]}))
    :access-control-allow-origin [#".*"]
    :access-control-allow-methods [:get :put :post :delete])
  )

(def serv2 (server/run-server #'secured-app {:port 9094}))
;(serv)
;(serv2)

(defn token-auth-mid [next-h]
  (fn [req]
    (if (contains? @tokens->users (get-in req [:headers "token-auth"]))
      (next-h req)
      {:status 403})))

(def non-secure (POST "/login" [user pass]
                  (if (= pass "pass")
                    (let [token (str (rand-int 1000))]
                      (swap! tokens->users assoc token user)
                      {:status 200
                       :body token})
                    {:status 403})))
(def secure-rutas
  (routes
    (GET "/test" req
      {:body "Hola"})
    (GET "/pepe" req
      {:body "Chau"})))

(def rutas
  (-> (routes

        non-secure

        (-> secure-rutas
            token-auth-mid))

      wrap-keyword-params
      wrap-params))

(rutas {:request-method :post
        :uri "/login"
        :query-string "user=token&pass=pass"})

(rutas {:request-method :get
        :uri "/test"
        :headers {"token-auth" "417"}})

@tokens->users