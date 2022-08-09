(ns sqlinspector.core
  (:gen-class)
  (:require [next.jdbc :as jdbc]
            [cljfx.api :as fx]
            [cljfx.ext.table-view :as fx.ext.table-view]
            [clojure.core.cache :as cache])
  )

(def db
  "Connection configuration of the database.
  (currently hardcoded)"
  {:dbtype "sqlserver"
           :user "test_user"
           :password "test123!"
           :host "127.0.0.1"
           :encrypt false
           :dbname "test_db"})

(def ds
  (jdbc/get-datasource db))

(defn retrieve-all-tables
  []
  (jdbc/execute! ds [(str "select name as table_name, create_date, modify_date \n"
                          "from sys.tables order by name")]))

(defn retrieve-table-columns
  [table-name]
  (jdbc/execute! ds
                 [(str "select \n"
                       "  c.column_id, \n"
                       "  c.name as column_name, \n"
                       "  t.[name] as type_name, \n"
                       "  c.max_length, \n"
                       "  c.is_nullable, \n"
                       "  c.is_identity \n"
                       "from sys.columns c \n"
                       "join sys.types t on t.system_type_id = c.system_type_id \n"
                       "where c.[object_id] = object_id(?) \n"
                       "order by c.column_id")
                  table-name]))

(def *state
  (atom (fx/create-context
         {:table-filter ""
          :selected-table ""
          :tables []}
         cache/lru-cache-factory)))

(defn tables-view [{:keys [fx/context]}]
  {:fx/type :v-box
   :children [{:fx/type :label
               :text "Table filter:"}
              {:fx/type :text-field
               :text (fx/sub-val context :table-filter)
               ;; use a map
               :on-text-changed {:event/type :update-table-filter
                                 :fx/sync true}}
              {:fx/type :label
               :text "Tables:"}
              ;; temporary added
              {:fx/type :label
               :text (str ":table-filter contains " (fx/sub-val context :table-filter))}
              {:fx/type fx.ext.table-view/with-selection-props
               :props {:selection-mode :single}
               :desc {:fx/type :table-view
                      :columns [{:fx/type :table-column
                                 :text "Tablename"
                                 :cell-value-factory identity
                                 :cell-factory {:fx/cell-type :table-cell
                                                :describe (fn [table-data]
                                                            #_(println "Data for the cell Tablename is:" table-data)
                                                            {:text (:table_name table-data) })}}]
                      :items (fx/sub-val context :tables)}}]})


(defn columns-view [{:keys [fx/context]}]
  {:fx/type :v-box
   :children [{:fx/type :label
               :text (str "Columns for table: " (fx/sub-val context :selected-table))}]})

(defn root-view [_]
  {:fx/type :stage
   :showing true
   :title "SQL inspector"
   :width 500
   :height 300
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [{:fx/type :split-pane
                              :items [{:fx/type tables-view}
                                      {:fx/type columns-view}]}]}}})

(defn event-handler [e]
  (println "event-handler:" e)
  (let [{:keys [event/type fx/event fx/context]} e]
    (case type
      :update-table-filter {:context (fx/swap-context context assoc :table-filter event)})))


;; Notice this is "def" and not "defn" as wrap-co-effects and wrap-effects
;; return a function.
(def map-event-handler
    (-> event-handler
        (fx/wrap-co-effects
         {:fx/context (fx/make-deref-co-effect *state)})
        (fx/wrap-effects
         {:context (fx/make-reset-effect *state)
          :dispatch fx/dispatch-effect})))

(def renderer
  (fx/create-renderer
   :middleware (comp
                ;; Pass context to every lifecycle as part of option map
                fx/wrap-context-desc
                (fx/wrap-map-desc (fn [_]{:fx/type root-view})))
   :opts {:fx.opt/map-event-handler map-event-handler
          :fx.opt/type->lifecycle #(or (fx/keyword->lifecycle %)
                                       ;; For functions in ':fx/type' values, pass
                                       ;; context from option map to these functions
                                       (fx/fn->lifecycle-with-context %))}))

(defn initialize-cljfx []
  (fx/mount-renderer *state renderer))

(defn -main
  [& args]
  (reset! *state (fx/swap-context @*state assoc :tables (retrieve-all-tables)))
  (initialize-cljfx))

;;----------------------------------------------------------------------------------------
;; Below is a big chunk of comment. This is used to enter expressions in the REPL directly
;; from within the Editor.
;;----------------------------------------------------------------------------------------
(comment

  ;; run the application from the REPL
  (-main)

  ;; Whenever we changed the user interface we must rerender
  ;; This is something we will often do, so keep this in the comment
  (renderer)

  ;; Manually set the filter
  (reset! *state (fx/swap-context @*state assoc :table-filter "Blah blah"))

  ;; Test if the event handler gives us the expected result
  (event-handler {:event/type :update-table-filter :fx/event "xyz" :state {:table-filter "abc"}})

  ;; This can be used to check if we can still connect to the database
  (jdbc/execute! ds ["select 123 as just_a_number"])

  ;; These tables are created to have something in the database
  (jdbc/execute! ds [(str "create table t_customer ( \n"
                          "  id_customer int not null identity(1,1) \n"
                          "    constraint pk_t_customer primary key, \n"
                          "  first_name varchar(250), \n"
                          "  last_name varchar(250), \n"
                          "  last_modified datetime not null \n"
                          "    constraint df_t_customer default (getdate()))")])

  (jdbc/execute! ds [(str "create table t_address_type ( \n"
                          "  address_type varchar(50) not null \n"
                          "    constraint pk_t_address_type primary key, \n"
                          "  info varchar(250))")])

  (jdbc/execute! ds [(str "create table t_customer_address ( \n"
                          "  id_customer_address int not null identity(1,1) \n"
                          "    constraint pk_t_customer_address primary key, \n"
                          "  id_customer int not null \n"
                          "    constraint fk_t_customer_address__customer \n"
                          "    foreign key references t_customer(id_customer), \n"
                          "  address_type varchar(50) not null \n"
                          "    constraint fk_t_customer_address__addres_type \n"
                          "    foreign key references t_address_type(address_type), \n"
                          "  is_default bit not null \n "
                          "    constraint df_t_customer_address default (0), \n"
                          "  info varchar(250))")])

  ;; check if the new database functions work as expected
  (retrieve-all-tables)
  (retrieve-table-columns "t_customer")


)
