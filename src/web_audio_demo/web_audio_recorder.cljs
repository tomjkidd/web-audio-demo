(ns web-audio-demo.web-audio-recorder
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [>! <! put! take! chan alts!]]
            [goog :as g]
            [goog.i18n.DateTimeFormat :as dtf]))

(def audio-resource-atom
  "An atom so that all references to important js objects can be released
  when recording is stopped"
  (atom nil))

(def recorder-atom
  "An atom that refers to the current WebAudioRecorder"
  (atom nil))

(def ogg-defaults
  "The default settings that are expected by..."
  (-> {"workerDir" "/js/"
       "options" {"encodeAfterRecord" true}
       "encoding" "ogg"}
      (clj->js)))

(def start-chan
  "A channel that is used to trigger recording to start"
  (chan))

(def stop-chan
  "A channel that is used to trigger recording to stop"
  (chan))

(defn cleanup-audio-resources
  "Called to properly close and disconnect web audio resources"
  []
  (let [a @audio-resource-atom]
    (.disconnect (:mic a))

    (let [ts (.getAudioTracks (:media-stream a))]
      (doall (map #(.stop %) ts)))

    (.close (:audio-context a))
    true))

; Inspired by...
; http://dirk.net/2012/07/14/date-formats-in-clojurescript/
(defn format-date-generic
  "Provide a date format string and a Date to get that representation"
  [fmt date]
  (.format (goog.i18n.DateTimeFormat. fmt) (js/Date. date)))

(when-let [gum (.-webkitGetUserMedia js/navigator)]
  (set! (.-getUserMedia js/navigator) gum))

(defn on-get-user-media-error
  "Responds to a MediaStreamError"
  [error]
  (js/console.log "There was an error attempting to connect to your microphone."))

(defn get-media-stream-chan
  "Get a channel whose result is a mediaStream"
  ([] (get-media-stream-chan (chan)))
  ([c]
   (go
     (.getUserMedia js/navigator
                    (clj->js {:audio true})
                    #(put! c %)
                    on-get-user-media-error))
   c))

(def max-delay
  "Max delay to use, in seconds"
  10)

(defn get-input-audio-node
  "Use a mediaStream to get create AudioNode to provide the WebAudioRecorder"
  [stream]
  (let [ac (js/AudioContext.)
        mic (.createMediaStreamSource ac stream)
        gain (.createGain ac)
        delay (.createDelay ac 10)]
    (swap! audio-resource-atom (fn [_] {:mic mic
                                        :media-stream stream
                                        :audio-context ac}))

    (set! (.. gain -gain -value) 1)
    (set! (.. delay -delayTime -value) 0)

    (.connect mic delay)
    (.connect delay gain)
    (.connect gain (.-destination ac))

    gain))

(defn recorder
  "A convenience constructor for WebAudioRecorder"
  [input-node options]
  (js/WebAudioRecorder. input-node options))

(defn media-stream-consumer
  "Consumes from a media stream chan, produces a chan whose result is a WebAudioRecorder"
  [media-stream-chan]
  (let [recorder-chan (chan)]
    (go
      (let [stream (<! media-stream-chan)
            input-node (get-input-audio-node stream)
            r (recorder input-node ogg-defaults)]
        (put! recorder-chan r)))
    recorder-chan))

; TODO: Pull this out to a util
(defn by-id [id]
  (.getElementById js/document (name id)))

(defn- save-file
  [blob-url filename]
  (let [a (by-id "file-download-link")]
    (set! (.-href a) blob-url)
    (set! (.-download a) filename)
    (.click a)))

(defn save-recording
  [blob enc]
  (let [time (js/Date.)
        fmt-time (format-date-generic "yyyyMMdd.HHmmss" time)
        blob-url (.createObjectURL js/URL blob)]
    (save-file blob-url (str fmt-time ".test-audio.ogg"))
    blob-url))

(defn on-complete
  "Provide an implementation for WebAudioRecorder's onComplete callback"
  [recorder blob]
  (swap! audio-resource-atom (fn [_] nil))
  (save-recording blob (.-encoding recorder)))

(defn on-error
  [recorder message]
  (println (str "Error detected: " message)))

(defn record-once
  [start-chan' stop-chan']
  (go
      (<! start-chan)
      (let [ms-chan (get-media-stream-chan)
            rec-chan (media-stream-consumer ms-chan)
            r (<! rec-chan)]
        ; Configure the recorder
        (set! (.-onComplete r) on-complete)
        (set! (.-onError r) on-error)
        (.setOptions r ogg-defaults)

        ; Capture a reference to the recorder
        (swap! recorder-atom (fn [_] r))

        (.startRecording r)

        (<! stop-chan)
        (.finishRecording r)
        (cleanup-audio-resources)))

  ; Now that process to handle recording is defined, start it
  (go (>! start-chan :start)))

(defn start
  "Start recording with the first available media stream"
  []
  (println "Recording...")
  
  ; Pay attention to the next start/stop messages
  (record-once start-chan stop-chan))

(defn stop
  "Stop recording"
  []
  (println "Stopped recording.")
  (go (>! stop-chan :stop)))
