;; ===========================================================
;;  The Parser is the link between the user input and the
;;  Interpreter. The Parser reads a string from the user,
;;  'understands' it't meaning and creates the Prolog Terms
;;  that it describes.
;;
(ns logic.parser
  (:use [logic.term])
  (:refer-clojure :exclude [resolve]))


(def priority
  {\, 3
   \; 2
   ":-" 1
   \( 0})


(defn remove-spaces [input]
  (->> input
       (re-seq (re-pattern #"\"[^\"]*\"|\'[^\']*\'|[^'\"]+"))
       (map #(if (or (re-matches (re-pattern #"\"[^\"]*\"") %)
                     (re-matches (re-pattern #"'[^']*'") %))
               %
               (-> %
                   (clojure.string/replace #"[ ]+" " ")
                   (clojure.string/replace #"(?<=[a-z0-9A-Z])[ ](?=[a-zA-Z0-9\[(])" "'")
                   (clojure.string/replace #"[ ]" "")
                   (clojure.string/replace #"'" " "))))
       (reduce str)))


(defn find-atom-single
  "If a string starts with a single word atom name
  the function returns the name and the rest of the string.
  Otherwise it returns empty name and the input string."
  [input]
  (if (re-matches (re-pattern #"[a-z]") (subs input 0 1))
    (loop [name (subs input 0 1)
           text (subs input 1)]
      (if (re-matches (re-pattern #"[a-zA-Z0-9_]") (subs text 0 1))
        (recur (str name (first text))
               (subs text 1))
        [name text]))
    ["" input]))


(defn find-atom-multi
  "If a string starts with a multi word atom name
  the function returns the name and the rest of the string.
  Otherwise it returns empty name and the input string."
  [input]
  (if (= \' (first input))
    (loop [name ""
           res (subs input 1)]
      (if (= \' (first res))
        [(str \' name \')
         (subs res 1)]
        (recur (str name (first res))
               (subs res 1))))
    ["" input]))


(defn find-atom
  "If a string starts with an atom name
  the function returns that name and the rest of the stirng.
  Otherwise it returns empty name and the input stirng."
  [input]
  (let [[single-word result] (find-atom-single input)]
    (if (= "" single-word)
      (find-atom-multi input)
      [single-word result])))


(defn find-string
  "If a string starts with a inline string
  the function returns that string and the rest of the input.
  Otherwise it returns empty string and the same input."
  [input]
  (if (= \" (first input))
    (loop [s ""
           res (subs input 1)]
      (if (= \" (first res))
        [(str \" s \")
         (subs res 1)]
        (recur (str s (first res))
               (subs res 1))))
    ["" input]))


(defn find-variable
  "If a string starts with a single word Variable name
  the function returns the name and the rest of the string.
  Otherwise it returns empty name and the input string."
  [input]
  (if (re-matches (re-pattern #"[A-Z_]") (subs input 0 1))
    (loop [name (subs input 0 1)
           text (subs input 1)]
      (if (re-matches (re-pattern #"[a-zA-Z0-9_]") (subs text 0 1))
        (recur (str name (first text))
               (subs text 1))
        [name text]))

    ["" input]))


(defn find-number
  "If a string starts with a number the function
  returns a string of that number and the rest of the input.
  Otherwise it returns an empty string and the same input."
  [input]
  (loop [num ""
        res input]
    (let [sym (first res)]
    (if (re-matches (re-pattern #"[0-9.]") (str sym))
      (recur (str num sym)
             (subs res 1))
      [num res]))))


(defn find-next
  [input]
  (let [[atom res] (find-atom input)]
    (if-not (= "" atom) [atom res]

      (let [[var res] (find-variable input)]
        (if-not (= "" var) [var res]

          (let [[num res] (find-number input)]
            (if-not (= "" num) [(read-string num) res]

              (let [[s res] (find-string input)]
                (if-not (= "" s) [s res]
                  (throw (Exception. (str "Missing operator before: \"" (-> input (subs 1) find-next first) "\"."))))))))))))



(defn extract-atom
  [input]
  (let [[atom result] (find-atom input)]
    (if (= "" atom)
      [nil input]
      [(create atom) result])))


(defn extract-variable
  [input]
  (let [[var result] (find-variable input)]
    (if (= "" var)
      [nil input]
      [(create var) result])))


(defn extract-list [input]
  (if-not (= \[ (first input))
    [nil input]
    (loop [elems []
           text (subs input 1)]
      (cond

       (= \, (first text))
       (recur elems (subs text 1))

       (= \[ (first text))
       (let [[new-list new-text] (extract-list text)]
         (recur (conj elems new-list) new-text))

       (= \| (first text))
       (recur (conj elems "|")
              (subs text 1))

       (= \] (first text))
       [elems (subs text 1)]

       :else
       (let [[term new-text] (find-next text)]
         (recur (conj elems term) new-text))))))


(defn extract-arguments
  [input]
  (if-not (= \( (first input))
    [nil input]
    (loop [args []
           text (subs input 1)]
      (cond

       (= \, (first text))
       (recur args (subs text 1))

       (= \) (first text))
       [(create-arguments args) (subs text 1)]

       (= \[ (first text))
       (let [[list new-text] (extract-list text)]
         (recur (conj args list) new-text))

       :else
       (let [[term new-text] (find-next text)]
          (recur (conj args term) new-text))))))


(defn extract-next [text]
  (let [[atom atom-text :as result] (extract-atom text)]
    (if atom
      (let [[args fact-text] (extract-arguments atom-text)]
        (if args
          [(->PrologFact atom args) fact-text]
          result)))))


(defn execute [op obs]
  (let [index (- (count obs) (:arity op))
        terms (subvec obs index)
        rest-obs (subvec obs 0 index)]
    (cond

   (= \, (:sign op)) (conj rest-obs (->PrologConjunction terms))

   (= \; (:sign op)) (conj rest-obs (->PrologDisjunction terms))

   (= ":-" (:sign op)) (conj rest-obs (->PrologRule (first terms) (second terms))))))


(defn add-operation
  [operations objects sign]
  (loop [ops operations
         obs objects]
    (cond

     (empty? ops)
     [(conj ops {:sign sign :arity 2}) obs]

     (and (= \, sign) (-> ops peek :sign (= \,)))
     [(conj (pop ops) (assoc (peek ops) :arity (-> ops peek :arity inc))) obs]

     (and (= \; sign) (-> ops peek :sign (= \;)))
     [(conj (pop ops) (assoc (peek ops) :arity (-> ops peek :arity inc))) obs]

     (<= (priority sign)
         (-> ops peek :sign priority))
     (recur (pop ops)
            (execute (peek ops)
                     obs))

     :else
     [(conj ops {:sign sign :arity 2}) obs])))


(defn parse [input]
  (loop [text (remove-spaces input)
         ops []
         obs []]

;;    DEBUG:
;;     (print ops " ")
;;     (print "[ ")
;;     (doseq [x obs] (print (output x {}) ""))
;;     (println "]")

    (cond

     (= \. (first text))
     (if (next text)
       (throw (Exception. (str "Missing operator before: \"" (-> obs last (output {})) "\".")))
       (loop [inner-ops ops
              inner-obs obs]
         (if (empty? inner-ops)
           (if (next inner-obs)
             (throw (Exception. (str "Missing operator before: \"" (-> inner-obs last (output {})) "\".")))
             (first inner-obs))
           (recur (pop inner-ops) (execute (peek inner-ops) inner-obs)))))

     (= \space (first text))
     (throw (Exception. (str "Missing operator before: \"" (-> text (subs 1) find-next first) "\".")))

     (or (= \, (first text))
         (= \; (first text)))
     (let [[new-ops new-obs] (add-operation ops obs (first text))]
       (recur (subs text 1) new-ops new-obs))

     (or (= ":-" (subs text 0 2)))
     (let [[new-ops new-obs] (add-operation ops obs (subs text 0 2))]
       (recur (subs text 2) new-ops new-obs))

     :else
     (let [[term rest-text] (extract-next text)]
       (recur rest-text ops (conj obs term))))))

(-> "atom(pesho, pesho)." parse)
