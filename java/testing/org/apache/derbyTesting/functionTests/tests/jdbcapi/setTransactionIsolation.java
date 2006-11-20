/*

   Derby - Class org.apache.derbyTesting.functionTests.tests.jdbcapi.setTransactionIsolation.java

   Copyright 2004, 2005 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derbyTesting.functionTests.tests.jdbcapi;

import java.lang.reflect.*;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.sql.*;
import java.io.*;

import org.apache.derby.tools.ij;
import org.apache.derby.tools.JDBCDisplayUtil;


public class setTransactionIsolation{

	static String conntype = null;
	static boolean shortTest = true;

  public static void main (String args[])
  {

    try {
		// use the ij utility to read the property file and
		// make the initial connection.
		ij.getPropertyArg(args);
		Connection conn = ij.startJBMS();
		
		createAndPopulateTable(conn);
		runTests(conn);
		conn.rollback();
		conn.close();
    } catch (Throwable  e) {
		e.printStackTrace();
    }
  }

	private static void dropTable(Statement stmt,String tab)
	{
		try {
		stmt.executeUpdate("drop table " + tab);
		}
		catch (SQLException se)
		{
		}
	}

  //create table and insert couple of rows
	private static void createAndPopulateTable(Connection conn) throws SQLException {
    Statement stmt = conn.createStatement();


	String[] tabsToDrop = {"tab1", "t1", "t1copy", "t2", "t3"};
	for (int i = 0; i < tabsToDrop.length; i++)
		dropTable(stmt,tabsToDrop[i]);


    System.out.println("Creating table...");
    stmt.executeUpdate( "CREATE TABLE TAB1 (c11 int, c12 int)" );
    stmt.executeUpdate("INSERT INTO TAB1 VALUES(1,1)");
    stmt.executeUpdate("INSERT INTO TAB1 VALUES(2,1)");
	stmt.execute("create table t1(I int, B char(15))");
	stmt.execute("create table t1copy(I int, B char(15))");
	
	stmt.executeUpdate("INSERT INTO T1 VALUES(1,'First Hello')");
	stmt.executeUpdate("INSERT INTO T1 VALUES(2,'Second Hello')");
	stmt.executeUpdate("INSERT INTO T1 VALUES(3,'Third Hello')");


    stmt.executeUpdate("create table t3 (i integer)");

    System.out.println("done creating table and inserting data.");

    stmt.close();
  }


	
    public static void runTests( Connection conn) throws Throwable
    {
		try {
			// make new statements after we set the isolation level
		   testIsolation(conn, true);
		   // reuse old statements.  setTransaction isolation has no effect 
		   // on already prepared statements for network server
		   conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		   testIsolation(conn, false);
		   testSetTransactionIsolationInHoldCursor(conn);
           testSetTransactionIsolationCommits(conn);
		} catch (SQLException sqle) {
			System.out.print("FAIL:");
			org.apache.derby.tools.JDBCDisplayUtil.ShowSQLException(System.out, sqle);
			sqle.printStackTrace(System.out);
		}
		
	}





	public static int[] isoLevels = {
		Connection.TRANSACTION_READ_UNCOMMITTED,		
		Connection.TRANSACTION_REPEATABLE_READ,
		Connection.TRANSACTION_READ_COMMITTED,
		Connection.TRANSACTION_SERIALIZABLE};

	
	private static void testIsolation(Connection conn, boolean makeNewStatements) throws SQLException 
	{

		Connection conn2 = null;
		try {
			conn2 = ij.startJBMS();

		}
		catch (Exception e){
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		Statement stmt = conn.createStatement();
		Statement stmt2 = conn2.createStatement();
		System.out.println("*** testIsolation. makeNewStatements =" + makeNewStatements); 

		conn.setAutoCommit(false);
		
		conn2.setAutoCommit(false);

		stmt2.executeUpdate("call SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(1)");
		String[]  sql = { "select * from t1",
			              "insert into t1copy (select * from t1)"};

		PreparedStatement ps = null;
		System.out.println("*** Test with no lock timeouts ***");
		for (int s = 0; s < sql.length; s++)
			testLevelsAndPrintStatistics(conn2,sql[s],makeNewStatements);
		// Now do an insert to create lock timeout
		System.out.println("*** Test with lock timeouts on everything but read uncommitted***");
		System.out.println("conn :insert into t1 values(4,'Forth Hello') (no commit)");
		stmt.executeUpdate("insert into t1 values(4,'Fourth Hello')");
		for (int s = 0 ; s < sql.length;s++)
			testLevelsAndPrintStatistics(conn2,sql[s],makeNewStatements);
		stmt.close();
		stmt2.close();
		// rollback to cleanup locks from insert
		conn.rollback();

	}
	
	/**
	 *   Call setTransactionIsolation with holdable cursor open?
	 */
	public static void testSetTransactionIsolationInHoldCursor(Connection conn) 
	{
		try {
			
			PreparedStatement ps = conn.prepareStatement("SELECT * from TAB1");
			ResultSet rs = ps.executeQuery();
			rs.next();
			// setTransactionIsolation should fail because we have 
			// a holdable cursor open
			conn.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
			rs.next(); // to fix DERBY-1108. Else the GC for ibm15 will clean up the ResultSet Object
		} catch (SQLException se)
		{
			System.out.println("EXPECTED EXCEPTION SQLSTATE:" + 
							   se.getSQLState() + " " +
							   se.getMessage());
			return;
		}
		System.out.println("FAIL: setTransactionIsolation() did not throw exception with open hold cursor");
	}
	
	/**
	 *   setTransactionIsolation commits?
	 */
	public static void testSetTransactionIsolationCommits(Connection conn) 
	{
        // In the current client implementation, the transaction will
        // commit when setTransactionIsolation is called, while the
        // embedded driver will not commit. See
        // http://issues.apache.org/jira/browse/DERBY-2064
		try {
            conn.rollback();
            conn.setAutoCommit(false);
			conn.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
            Statement s = conn.createStatement();
            s.executeUpdate("delete from t3");
            s.executeUpdate("insert into t3 values(1)");
            conn.commit();
            s.executeUpdate("insert into t3 values(2)");
			conn.setTransactionIsolation(java.sql.Connection.TRANSACTION_SERIALIZABLE);
            conn.rollback();
            ResultSet rs = s.executeQuery("select count(*) from t3");
            rs.next();
            int count = rs.getInt(1);
            switch (count) {
            case 1:
                System.out.println("count="+ count + 
                                   ", setTransactionIsolation() does not commit");
                break;
            case 2:
                System.out.println("count="+ count + 
                                   ", setTransactionIsolation() commits");
                break;
            default:
                System.out.println("FAIL: count="+ count + 
                                   ", unexepected behaviour from testSetTransactionIsolationCommits");
                break;
            }
            rs.close();
            s.close();
		} catch (SQLException se) {
			System.out.println(se.getMessage());
		}
	}
	
	public static void testLevelsAndPrintStatistics(Connection con, String sql,
													boolean makeNewStatements)
		throws SQLException
		{
			System.out.println("***testLevelsAndPrintStatistics sql:" + sql +
							   " makenewStatements:" + makeNewStatements);
			PreparedStatement ps = con.prepareStatement(sql);
			Statement stmt = con.createStatement();

			System.out.println("con.prepareStatement(" +sql +")");
			for (int i = 0; i < isoLevels.length; i++)
			{
				
				try {
					System.out.println("con.setTransactionIsolation(" + 
									   getIsoLevelName(isoLevels[i]) +")");
					con.setTransactionIsolation(isoLevels[i]);
					
					System.out.println("con.getTransactionIsolation() =" +
									   getIsoLevelName(con.getTransactionIsolation()));
					if (makeNewStatements)
					{
						ps.close();
						ps = con.prepareStatement(sql);
						System.out.println("con.prepareStatement(" +sql +")");
					}

					System.out.println(sql);					
					ps.execute();
					ResultSet rs = ps.getResultSet();
					showScanStatistics(rs,con);

					// Now execute again and look at the locks
					/*
					  // can't do the locks right now because of prefetch
					ps.execute();
					rs = ps.getResultSet();
					if (rs != null)
					{
						rs.next();
						ResultSet lockrs = stmt.executeQuery("Select * from new LockTable() l where l.tableType <> 'S'");
						JDBCDisplayUtil.DisplayResults(System.out,lockrs,con);
						lockrs.close();
						rs.close();
					}
					*/
				} catch (Exception e)
				{
					System.out.println(e.getMessage());
					//e.printStackTrace();
				}
				con.commit();
				stmt.executeUpdate("call SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(1)");
			}
			
		ps.close();
		stmt.close();
		System.out.println("\n\n");
		}

	public static String getIsoLevelName(int level)
	{
		switch (level) {
			case java.sql.Connection.TRANSACTION_REPEATABLE_READ:
				return "TRANSACTION_REAPEATABLE_READ:" + level;
					
			case java.sql.Connection.TRANSACTION_READ_COMMITTED:
				return "TRANSACTION_READ_COMMITTED:" + level;
			case java.sql.Connection.TRANSACTION_SERIALIZABLE:
				return "TRANSACTION_SERIALIZABLE:" + level;
			case java.sql.Connection.TRANSACTION_READ_UNCOMMITTED:
				return "TRANSACTION_READ_UNCOMMITTED:" + level;

		}
		return "UNEXPECTED_ISO_LEVEL";
	}


	private static void statementExceptionExpected(Statement s, String sql) {
		System.out.println(sql);
		try {
			s.execute(sql);
			System.out.println("FAIL - SQL expected to throw exception");
		} catch (SQLException sqle) {
			System.out.println("EXPECTED " + sqle.toString());
		}
	}

	public static void showResultsAndStatistics(Statement s, Connection conn,
												boolean expectException)
	{
		ResultSet rs = null;
		try {
			rs = s.getResultSet();
			if (rs == null)
			{
				System.out.println("UPDATE COUNT " + s.getUpdateCount());
				return;
			}
			else 
			   showResultsAndStatistics(rs,conn,expectException);
		}
		catch (SQLException se)
		{
			// assume the getResultSet should go well 
			// expectException is for the scan
			System.out.print("FAIL: UNEXPECTED EXCEPTION:");
			JDBCDisplayUtil.ShowSQLException(System.out,se);
		}
	}
	public static void showResultsAndStatistics(ResultSet rs,Connection conn,
												boolean  expectException)
	{
		

		try {
			System.out.println("CursorName:" + rs.getCursorName());
			JDBCDisplayUtil.DisplayResults(System.out,rs,conn);
			showScanStatistics(rs,conn);
		}catch (SQLException se)
		{
			if(expectException )
				System.out.print("EXPECTED SQL EXCEPTION:");
			else
				System.out.print("FAIL: UNEXPECTED EXCEPTION:");
			JDBCDisplayUtil.ShowSQLException(System.out,se);
		}

		
	}

	public static void showScanStatistics(ResultSet rs, Connection conn)
	{
		Statement s = null;
		ResultSet infors = null;

		
		try {
			rs.close(); // need to close to get statistics
			s =conn.createStatement();
			infors = s.executeQuery("values SYSCS_UTIL.SYSCS_GET_RUNTIMESTATISTICS()");
			JDBCDisplayUtil.setMaxDisplayWidth(2000);
			JDBCDisplayUtil.DisplayResults(System.out,infors,conn);
			infors.close();
		}
		catch (SQLException se)
		{
			System.out.print("FAIL:");
			JDBCDisplayUtil.ShowSQLException(System.out,se);
		}			
	}

}



