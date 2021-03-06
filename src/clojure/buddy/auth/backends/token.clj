;; Copyright 2013-2015 Andrey Antukh <niwi@niwi.be>
;;
;; Licensed under the Apache License, Version 2.0 (the "License")
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns buddy.auth.backends.token
  (:require [buddy.auth.protocols :as proto]
            [buddy.auth.utils :as utils]
            [buddy.auth :refer [authenticated?]]
            [buddy.sign.generic :refer [loads]]
            [buddy.core.util :refer [maybe-let]]
            [clojure.string :refer [split]]
            [ring.util.response :refer [response response? header status]]))

(defn parse-authorization-header
  "Given a request, try extract and parse
  authorization header."
  [request]
  (let [headers (utils/lowercase-headers (:headers request))
        pattern (re-pattern "^Token (.+)$")]
    (some->> (get headers "authorization")
             (re-find pattern)
             (second))))

(defn signed-token-backend
  [{:keys [privkey unauthorized-handler max-age]}]
  (reify
    proto/IAuthentication
    (parse [_ request]
      (parse-authorization-header request))
    (authenticate [_ request data]
      (assoc request :identity (loads data privkey {:max-age max-age})))

    proto/IAuthorization
    (handle-unauthorized [_ request metadata]
      (if unauthorized-handler
        (unauthorized-handler request metadata)
        (if (authenticated? request)
          (-> (response "Permission denied")
              (status 403))
          (-> (response "Unauthorized")
              (status 401)))))))

(defn token-backend
  [{:keys [authfn unauthorized-handler]}]
  {:pre [(fn? authfn)]}
  (reify
    proto/IAuthentication
    (parse [_ request]
      (parse-authorization-header request))
    (authenticate [_ request token]
      (let [rsq (authfn request token)]
        (if (response? rsq) rsq
            (assoc request :identity rsq))))

    proto/IAuthorization
    (handle-unauthorized [_ request metadata]
      (if unauthorized-handler
        (unauthorized-handler request metadata)
        (if (authenticated? request)
          (-> (response "Permission denied")
              (status 403))
          (-> (response "Unauthorized")
              (status 401)))))))
