(ns recom.core-test
  (:require [clojure.test :refer :all]
            [recom.core :refer :all]))


(deftest app-test

  (testing "A get to home should return 200"
   (is (= (app {:request-method :get
                :uri "/"})
          {:status 200,
           :headers {"Content-Type" "text/html; charset=utf-8"},
           :body "Home"})))

  (testing "A post to /login with wrong username or pass should return 403"
   (is (= (app {:request-method :post
                :uri "/login"
                :query-string "user=admin&pass=pass"})
          {:status 403, :headers {}, :body ""})))

  (testing "A post to /login with correct username and pass should return work"
    (let [{:keys [status body]} (app {:request-method :post
                                     :uri "/login"
                                     :json-params {:user "admin" :pass "pass"}})]
     (is (and (= status 200)
              (string? body)
              (not-empty body))))))
