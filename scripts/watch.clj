(require '[cljs.build.api :as b])

(b/watch "src"
  {:main 'web-audio-demo.core
   :output-to "out/web_audio_demo.js"
   :output-dir "out"})
