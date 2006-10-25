--
--   Licensed to the Apache Software Foundation (ASF) under one or more
--   contributor license agreements.  See the NOTICE file distributed with
--   this work for additional information regarding copyright ownership.
--   The ASF licenses this file to You under the Apache License, Version 2.0
--   (the "License"); you may not use this file except in compliance with
--   the License.  You may obtain a copy of the License at
--
--      http://www.apache.org/licenses/LICENSE-2.0
--
--   Unless required by applicable law or agreed to in writing, software
--   distributed under the License is distributed on an "AS IS" BASIS,
--   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--   See the License for the specific language governing permissions and
--   limitations under the License.
--
AUTOCOMMIT OFF;

-- MODULE DML027

-- SQL Test Suite, V6.0, Interactive SQL, dml027.sql
-- 59-byte ID
-- TEd Version #

-- AUTHORIZATION HU
   set schema HU;

--O   SELECT USER FROM HU.ECCO;
  VALUES USER;
-- RERUN if USER value does not match preceding AUTHORIZATION comment

-- date_time print

-- TEST:0124 UPDATE UNIQUE column (key = key + 1) interim conflict!

-- setup
     UPDATE UPUNIQ
          SET NUMKEY = NUMKEY + 1;
-- PASS:0124 If 6 rows updated?

      SELECT COUNT(*),SUM(NUMKEY)
           FROM UPUNIQ;
-- PASS:0124 If count = 6 and SUM(NUMKEY) = 30?

-- restore
     ROLLBACK WORK;

-- END TEST >>> 0124 <<< END TEST
-- ********************************************************

-- TEST:0125 UPDATE UNIQUE column (key = key + 1) no interim conflit!

-- setup
     UPDATE UPUNIQ
          SET NUMKEY = NUMKEY + 1
          WHERE NUMKEY >= 4;
-- PASS:0125 If 3 rows are updated?

      SELECT COUNT(*),SUM(NUMKEY)
           FROM UPUNIQ;
-- PASS:0125 If count = 6 and SUM(NUMKEY) = 27?

-- restore
     ROLLBACK WORK;

-- END TEST >>> 0125 <<< END TEST
-- *************************************************////END-OF-MODULE
