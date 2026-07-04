(ns spine.lint-test
  (:require [clojure.test :refer [deftest is]]
            [spine.lint :as lint]))

(deftest clj-leading-dash-banner-flagged
  (let [fs (lint/scan-source
             "src/foo.clj"
             ";; --- thread-local arena pooling ----------\n(defn x [])\n")]
    (is (= 1 (count fs)))
    (is (= "src/banner" (:rule (first fs))))))

(deftest bare-section-label-is-not-a-banner
  (let [fs (lint/scan-source "src/foo.clj" ";;;; Indentation\n(defn x [])\n")]
    (is (empty? fs))))

(deftest clj-comment-wall-of-four-flagged
  (let [fs (lint/scan-source
             "src/foo.clj"
             "(ns foo)\n;; one\n;; two\n;; three\n;; four\n(defn x [])\n")
        wall (some #(when (= "src/comment-wall" (:rule %)) %) fs)]
    (is wall)
    (is (= 2 (:line wall)))))

(deftest three-comment-lines-are-not-a-wall
  (let [fs (lint/scan-source
             "src/foo.clj"
             "(ns foo)\n;; one\n;; two\n;; three\n(defn x [])\n")]
    (is (empty? fs))))

(deftest leading-header-block-is-exempt
  (let [fs (lint/scan-source
             "src/foo.clj"
             (str ";; header one\n;; header two\n;; header three\n;; header four\n"
                  "(ns foo)\n(defn x [])\n"))]
    (is (empty? fs))))

(deftest shebang-and-header-block-exempt
  (let [fs (lint/scan-source
             "hooks/foo.clj"
             (str "#!/usr/bin/env mino\n;; h1\n;; h2\n;; h3\n;; h4\n"
                  "(println 1)\n"))]
    (is (empty? fs))))

(deftest bare-separator-line-continues-a-comment-run
  ;; A bare `;;` separator is part of a comment run, not a breaker.
  (let [fs (lint/scan-source
             "src/foo.clj"
             "(ns foo)\n;; a\n;;\n;; b\n;; c\n;; d\n(defn x [])\n")]
    (is (= 1 (count fs)))
    (is (= "src/comment-wall" (:rule (first fs))))))

(deftest docstring-lines-do-not-count-as-a-wall
  (let [fs (lint/scan-source
             "src/foo.clj"
             "(defn x\n  \"line one\n   line two\n   line three\n   line four\"\n  [])\n")]
    (is (empty? fs))))

(deftest elixir-hash-banner-flagged
  (let [fs (lint/scan-source "lib/foo.ex" "# --- setup ----\n")]
    (is (= 1 (count fs)))
    (is (= "src/banner" (:rule (first fs))))))

(deftest elixir-interpolation-is-not-a-comment
  (let [fs (lint/scan-source "lib/foo.ex" "#{:a 1}\n")]
    (is (empty? fs))))

(deftest c-slash-banner-flagged
  (let [fs (lint/scan-source "src/foo.c" "// --- entry ---\n")]
    (is (= 1 (count fs)))
    (is (= "src/banner" (:rule (first fs))))))

(deftest zig-slash-banner-flagged
  (let [fs (lint/scan-source "src/foo.zig" "// --- entrypoint ---\n")]
    (is (= 1 (count fs)))
    (is (= "src/banner" (:rule (first fs))))))

(deftest non-source-file-is-skipped
  (let [fs (lint/scan-source "README.md" ";; --- not source ---\n")]
    (is (nil? fs))))
