(ns sqlinspector.core
  (:gen-class)
  (:require [next.jdbc :as jdbc]
            [cljfx.api :as fx])
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
  (jdbc/execute! ds [(str "select name, create_date, modify_date as table_name \n"
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

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

;;----------------------------------------------------------------------------------------
;; Below is a big chunk of comment. This is used to enter expressions in the REPL directly
;; from within the Editor.
;;----------------------------------------------------------------------------------------
(comment

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


  ;;**********
  ;; step 4.2
  ;;**********
  ;; cljfx Hello World
  (fx/on-fx-thread
   (fx/create-component
    {:fx/type :stage
     :showing true
     :title "Cljfx example"
     :width 300
     :height 100
     :scene {:fx/type :scene
             :root {:fx/type :v-box
                    :alignment :center
                    :children [{:fx/type :label
                                :text "Hello from SQL Inspector"}]}}}))
  ;;**********
  ;; step 4.3
  ;;**********
  ;; Get interactive

  ;; A state with some dummy data that we will display
  (def *state
    (atom {:table-filter "some filter data"}))


  (defn root-view [{{:keys [table-filter]} :state}]
    {:fx/type :stage
     :showing true
     :title "SQL inspector"
     :width 500
     :height 300
     :scene {:fx/type :scene
             :root {:fx/type :v-box
                    :children [{:fx/type :label
                                :text "Table filter:"}
                               {:fx/type :text-field
                                :text table-filter}]}}})

  (def renderer
    (fx/create-renderer
     :middleware (fx/wrap-map-desc (fn [state]
                                     {:fx/type root-view
                                      :state state}))))

  ;; Start watching for state changes and call the renderer.
  ;; This will show the window.
  (fx/mount-renderer *state renderer)

  ;; Whenever we update the state, the text-field should update too.
  (reset! *state {:table-filter "filter changed"})

  ;;**********
  ;; step 4.4
  ;;**********
  ;; The skeleton of the application
  (defn root-view [{{:keys [table-filter selected-table]} :state}]
    {:fx/type :stage
     :showing true
     :title "SQL inspector"
     :width 500
     :height 300
     :scene {:fx/type :scene
             :root {:fx/type :v-box
                    :children [{:fx/type :split-pane
                                :items [{:fx/type :v-box
                                         :children [{:fx/type :label
                                                     :text "Table filter:"}
                                                    {:fx/type :text-field
                                                     :text table-filter}
                                                    {:fx/type :label
                                                     :text "Tables:"}]}
                                        {:fx/type :v-box
                                         :children [{:fx/type :label
                                                     :text (str "Columns for table:"
                                                                selected-table)}]}]}]}}})
  ;; We updated the view, thus must re-render.
  (renderer)

  ;; The new root-view expects the key :selected-table in the state,
  ;; so add it to the state.
  ;; Because we renderer is watching *state (see step 4.3) the screen
  ;; is immediately updated.
  (reset! *state {:table-filter "some filter data xx"
                  :selected-table "t_my_table"})

  ;;**********
  ;; step 4.5
  ;;**********
  ;; Move tables and columns in their own function
  (defn tables-view [{:keys [table-filter]}]
    {:fx/type :v-box
     :children [{:fx/type :label
                 :text "Table filter:"}
                {:fx/type :text-field
                 :text table-filter}
                {:fx/type :label
                 :text "Tables:"}]})

  (defn columns-view [{:keys [selected-table]}]
    {:fx/type :v-box
     :children [{:fx/type :label
                 :text (str "Columns for table: " selected-table)}]})

  (defn root-view [{{:keys [table-filter selected-table]} :state}]
    {:fx/type :stage
     :showing true
     :title "SQL inspector"
     :width 500
     :height 300
     :scene {:fx/type :scene
             :root {:fx/type :v-box
                    :children [{:fx/type :split-pane
                                :items [{:fx/type tables-view
                                         :table-filter table-filter }
                                        {:fx/type columns-view
                                         :selected-table selected-table }]}]}}})
  ;; We updated the view, thus must re-render.
  (renderer)
)
