(ns untangled.i18n.util
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]))

; helpers for i18n lein plugin
(defn wrap-with-swap [& {:keys [locale translation]}]
  (let [trans-namespace (symbol (str "untangled.translations." locale))
        ns-decl (pp/write (list 'ns trans-namespace (list :require 'untangled.i18n.core)) :stream nil)
        trans-def (pp/write (list 'def 'translations translation) :stream nil)
        swap-decl (pp/write (list 'swap! 'untangled.i18n.core/*loaded-translations*
                                  (list 'fn '[x] (list 'assoc 'x locale 'translations))) :stream nil)
        comment ";; This file was generated by untangled's i18n leiningen plugin."]
    (str/join "\n\n" [ns-decl comment trans-def swap-decl])))

(defn write-cljs-translation-file [fname translations-string]
  (spit fname translations-string))


; po file parsing helpers
(defn group-chunks [translation-chunk]
  (reduce (fn [acc line]
            (let [unescaped-newlines (str/replace line #"\\n" "\n")]
              (if (re-matches #"^msg.*" line)
               (conj acc [unescaped-newlines])
               (update-in acc [(dec (count acc))] conj unescaped-newlines))))
          [] translation-chunk))

(defn join-quoted-strings [strings]
  (reduce (fn [acc quoted-string]
            (str acc (last (re-matches #"(?ms)^.*\"(.*)\"" quoted-string)))) "" strings))


; po file parsing heavy lifters
(defn group-translations [fname]
  (let [fstring (slurp fname)
        trans-chunks (rest (clojure.string/split fstring #"(?ms)\n\n"))
        grouped-chunks (map clojure.string/split-lines trans-chunks)
        comment? #(re-matches #"^#.*" %)
        uncommented-chunks (map #(remove comment? %) grouped-chunks)
        keyed-chunks (map group-chunks uncommented-chunks)]
    (if (empty? keyed-chunks) nil keyed-chunks)))

(defn inline-strings [acc grouped-trans-chunk]
  (reduce (fn [mapped-translation trans-subcomponent]
            (let [key (->> trans-subcomponent first (re-matches #"^(msg[a-z]+) .*$") last keyword)
                  value (join-quoted-strings trans-subcomponent)]
              (assoc mapped-translation key value))) acc grouped-trans-chunk))

(defn map-translations [fname]
  (let [translation-groups (group-translations fname)
        mapped-translations (reduce (fn [trans-maps translation]
                                      (conj trans-maps (inline-strings {} translation)))
                                      [] translation-groups )]
    (reduce (fn [acc translation]
              (assoc acc (str (:msgctxt translation) "|" (:msgid translation)) (:msgstr translation)))
            {} mapped-translations)))