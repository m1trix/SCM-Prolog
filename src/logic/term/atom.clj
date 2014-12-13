(def atom-name-pattern #"[a-z][a-zA-Z_]*|'[^']+'")
(def atom-name-with-no-qoutes-pattern #"[^']+")


(declare atom->string)
(declare atom-unify)


(defrecord PrologAtom [name]
  IPrologTerm
  (to-string [this pool] (atom->string this))
  (unify [this other pool] (atom-unify this other pool))
  (generate [this names] [this names]))


(defn create-atom
  "Creates a PrologAtom."
  [[name]]
  (if (nil? (re-matches atom-name-pattern name))
    (throw (Exception. (format "Cannot create PrologAtom with name \"%s\"!" name)))
    (PrologAtom. name)))


(defmacro prolog-atom?
  "Tells whether the term is an atom."
  [term]
  `(= (type ~term) PrologAtom))


(defn unify-atoms
  "Two PrologAtoms unify if their names are the same
  when the quotes are removed."
  [x y pool]
  ;; Getting the unqoted names
  (let [name-x (re-find atom-name-with-no-qoutes-pattern (. x name))
        name-y (re-find atom-name-with-no-qoutes-pattern (. y name))]
    (if (= name-x name-y)
      [true pool]
      [false pool])))


(defn atom->string
  "Returns a string that holds the output form of the PrologAtom."
  [atom]
  (. atom name))


(defn atom-unify
  "Unifies a PrologAtom with a IPrologTerm inside the pool."
  [atom term pool]
  (cond

   (prolog-atom? term)
   (unify-atoms atom term pool)

   (prolog-var? term)
   (unify term atom pool)

   :else [false pool]))
