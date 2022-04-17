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

(def *state
  (atom {:table-filter ""
         :selected-table ""}))

(defn tables-view [{:keys [table-filter]}]
  {:fx/type :v-box
   :children [{:fx/type :label
               :text "Table filter:"}
              {:fx/type :text-field
               :text table-filter
               :on-text-changed #(swap! *state assoc :table-filter %)}
              {:fx/type :label
               :text "Tables:"}
              ;; temporary added
              {:fx/type :label
               :text (str ":table-filter contains " table-filter)}]})


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
                                       :table-filter table-filter}
                                      {:fx/type columns-view
                                       :selected-table selected-table}]}]}}})
(def renderer
  (fx/create-renderer
   :middleware (fx/wrap-map-desc (fn [state]
                                   {:fx/type root-view
                                    :state state}))))

(defn initialize-cljfx []
  (fx/mount-renderer *state renderer))

(defn -main
  [& args]
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
  (swap! *state assoc :table-filter "Blah blah")

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
