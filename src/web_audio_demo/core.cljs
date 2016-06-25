(ns web-audio-demo.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.browser.repl :as repl]
            [cljs.core.async :as async
             :refer [>! <! put! take! chan alts!]]
            [goog.events :as events]
            [goog.dom.classes :as classes]
            [web-audio-demo.web-audio-recorder :as war])
  (:import [goog.events EventType]))

;; (defonce conn
;;   (repl/connect "http://localhost:9000/repl"))

(enable-console-print!)

(println "Hello world!")

(println war/test)

(defn by-sel
  "by-sel :: string -> Element
  Short-hand for document.querySelector(sel)"
  [selector]
  (.querySelector js/document selector))

(defn events->chan
  "events->chan :: Element -> goog.events.EventType -> cljs.core.async.channel
  Given an element and a type of event, return a channel that receives the events
  from the element."
  ([el event-type] (events->chan el event-type (chan)))
  ([el event-type c]
   (events/listen el event-type
                  (fn [e] (put! c e)))
   c))

(defn record-app
  "A state machine that will allow recording, ignoring states that are not desired"
  []
  (let [start-clicks (events->chan (by-sel "#start-button") EventType.CLICK)
        stop-clicks (events->chan (by-sel "#stop-button") EventType.CLICK)
        running (atom false)]
    (go
      (loop []
        (let [[v c] (alts! [start-clicks stop-clicks])]
          (cond
            (= c stop-clicks)
            (if @running
              (do (println "Stop click received")
                  ;; TODO: Save the recording to file
                  (war/stop)
                  
                  ;; Start the state machine over again
                  (record-app))
              (do
                (println "Haven't started yet, ignoring stop click.")
                (recur)))
            
            (= c start-clicks)
            (do (if @running
                  (do (println "Already recording, ignoring start click"))
                  (do
                    (println "Start click received")
                    (war/start)
                    (swap! running (fn [_] true))))
                (recur))
            :else
            (do
              (println "Unknown signal, ignoring...")
              (recur))))))))

(record-app)
