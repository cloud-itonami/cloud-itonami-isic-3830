(ns recovery.facts
  "Per-jurisdiction materials-recovery grading-standard regulatory
  catalog -- the G2-style spec-basis table the Traceability Governor
  checks every grading/verify proposal against ('did the advisor cite
  an OFFICIAL public source for this jurisdiction's materials-
  recovery grading standard, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official waste/
  circular-economy regulator or recognized grading-standard body (see
  `:provenance`); they are a STARTING catalog, not a from-scratch
  survey of all ~194 jurisdictions. Extending coverage is additive:
  add one map to `catalog`, cite a real source, done -- never invent a
  jurisdiction's requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  weight/scale-record/material-composition-scan-record/contamination-
  inspection-record/buyer-specification-match-record evidence set
  submitted in some form; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:grading/verify` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (METI, Ministry of Economy, Trade and Industry)"
          :legal-basis "資源有効利用促進法 (Act on the Promotion of Effective Utilization of Resources)"
          :national-spec "再生資源の分別・品位評価に関する基準"
          :provenance "https://www.meti.go.jp/policy/recycle/main/data/law/law1.html"
          :required-evidence ["計量記録 (weight/scale-record)"
                              "材料組成スキャン記録 (material-composition-scan-record)"
                              "汚染検査記録 (contamination-inspection-record)"
                              "買主仕様適合記録 (buyer-specification-match-record)"]}
   "USA" {:name "United States"
          :owner-authority "Environmental Protection Agency (EPA) / Institute of Scrap Recycling Industries (ISRI)"
          :legal-basis "Resource Conservation and Recovery Act (RCRA, 42 U.S.C. §6901 et seq.) / ISRI Scrap Specifications Circular"
          :national-spec "Scrap-material grading, contamination and reporting requirements"
          :provenance "https://www.epa.gov/rcra"
          :required-evidence ["Weight/scale record"
                              "Material-composition-scan record"
                              "Contamination-inspection record"
                              "Buyer-specification-match record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Environment Agency / Waste and Resources Action Programme (WRAP)"
          :legal-basis "Waste (England and Wales) Regulations 2011 / WRAP grading guidance"
          :national-spec "Materials-recovery grading, contamination and reporting standards"
          :provenance "https://www.gov.uk/government/organisations/environment-agency"
          :required-evidence ["Weight/scale record"
                              "Material-composition-scan record"
                              "Contamination-inspection record"
                              "Buyer-specification-match record"]}
   "DEU" {:name "Germany"
          :owner-authority "Umweltbundesamt (UBA)"
          :legal-basis "Kreislaufwirtschaftsgesetz (KrWG, Circular Economy Act)"
          :national-spec "Sortier- und Qualitätsbewertungsstandards für Sekundärrohstoffe"
          :provenance "https://www.umweltbundesamt.de/themen/abfall-ressourcen"
          :required-evidence ["Wiegeaufzeichnung (weight/scale-record)"
                              "Materialzusammensetzungsscan (material-composition-scan-record)"
                              "Kontaminationsprüfbericht (contamination-inspection-record)"
                              "Abnehmerspezifikationsabgleich (buyer-specification-match-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to certify a
  material grade or publish an impact report on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3830 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `recovery.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
