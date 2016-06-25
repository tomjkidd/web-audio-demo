(ns web-audio-demo.web-audio-recorder
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [>! <! put! take! chan alts!]]
            [goog :as g]
            [goog.i18n.DateTimeFormat :as dtf]))

(def test "A test" "WebAudioRecorder")
(defn start
  "Start recording with the first available media stream"
  []
  (println "Recording..."))

(defn stop
  "Stop recording"
  []
  (println "Stopped recording."))
