/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.jpa.test;

import org.apache.commons.io.IOUtils;
import org.dbunit.Assertion;
import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseUnitException;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.sonar.api.database.DatabaseSession;
import org.sonar.core.persistence.Database;
import org.sonar.core.persistence.DatabaseCommands;
import org.sonar.core.persistence.H2Database;
import org.sonar.jpa.session.DatabaseSessionFactory;
import org.sonar.jpa.session.DefaultDatabaseConnector;
import org.sonar.jpa.session.JpaDatabaseSession;
import org.sonar.jpa.session.MemoryDatabaseConnector;

import java.io.InputStream;
import java.sql.SQLException;

import static org.junit.Assert.fail;

/**
 * Heavily duplicates DaoTestCase as long as Hibernate is in use.
 */
public abstract class AbstractDbUnitTestCase {
  private static Database database;
  private static DefaultDatabaseConnector dbConnector;
  private static DatabaseCommands databaseCommands;

  private JpaDatabaseSession session;
  private IDatabaseTester databaseTester;
  private IDatabaseConnection connection;

  @BeforeClass
  public static void startDatabase() throws Exception {
    database = new H2Database();
    database.start();

    dbConnector = new MemoryDatabaseConnector(database);
    dbConnector.start();

    databaseCommands = DatabaseCommands.forDialect(database.getDialect());
  }

  @Before
  public void startConnection() throws Exception {
    databaseCommands.truncateDatabase(database.getDataSource().getConnection());
    databaseTester = new DataSourceDatabaseTester(database.getDataSource());

    session = new JpaDatabaseSession(dbConnector);
    session.start();
  }

  @After
  public void stopConnection() throws Exception {
    if (databaseTester != null) {
      databaseTester.onTearDown();
    }
    if (connection != null) {
      connection.close();
    }
    if (session != null) {
      session.stop();
    }
  }

  public DatabaseSession getSession() {
    return session;
  }

  public DatabaseSessionFactory getSessionFactory() {
    return new DatabaseSessionFactory() {

      public DatabaseSession getSession() {
        return session;
      }

      public void clear() {
      }
    };
  }

  protected final void setupData(String... testNames) {
    InputStream[] streams = new InputStream[testNames.length];
    try {
      for (int i = 0; i < testNames.length; i++) {
        String className = getClass().getName();
        className = String.format("/%s/%s.xml", className.replace(".", "/"), testNames[i]);
        streams[i] = getClass().getResourceAsStream(className);
        if (streams[i] == null) {
          throw new RuntimeException("Test not found :" + className);
        }
      }

      setupData(streams);

    } finally {
      for (InputStream stream : streams) {
        IOUtils.closeQuietly(stream);
      }
    }
  }

  private final void setupData(InputStream... dataSetStream) {
    try {
      IDataSet[] dataSets = new IDataSet[dataSetStream.length];
      for (int i = 0; i < dataSetStream.length; i++) {
        ReplacementDataSet dataSet = new ReplacementDataSet(new FlatXmlDataSet(dataSetStream[i]));
        dataSet.addReplacementObject("[null]", null);
        dataSets[i] = dataSet;
      }
      CompositeDataSet compositeDataSet = new CompositeDataSet(dataSets);

      databaseTester.setDataSet(compositeDataSet);
      connection = databaseTester.getConnection();

      connection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new H2DataTypeFactory());
      DatabaseOperation.CLEAN_INSERT.execute(connection, databaseTester.getDataSet());

    } catch (Exception e) {
      throw translateException("Could not setup DBUnit data", e);
    }
  }

  protected final void checkTables(String testName, String... tables) {
    checkTables(testName, new String[0], tables);
  }

  protected final void checkTables(String testName, String[] excludedColumnNames, String... tables) {
    getSession().commit();
    try {
      IDataSet dataSet = getCurrentDataSet();
      IDataSet expectedDataSet = getExpectedData(testName);
      for (String table : tables) {
        ITable filteredTable = DefaultColumnFilter.excludedColumnsTable(dataSet.getTable(table), excludedColumnNames);
        ITable filteredExpectedTable = DefaultColumnFilter.excludedColumnsTable(expectedDataSet.getTable(table), excludedColumnNames);
        Assertion.assertEquals(filteredExpectedTable, filteredTable);
      }
    } catch (DataSetException e) {
      throw translateException("Error while checking results", e);
    } catch (DatabaseUnitException e) {
      fail(e.getMessage());
    }
  }

  private final IDataSet getExpectedData(String testName) {
    String className = getClass().getName();
    className = String.format("/%s/%s-result.xml", className.replace(".", "/"), testName);

    InputStream in = getClass().getResourceAsStream(className);
    try {
      return getData(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
  }

  private final IDataSet getData(InputStream stream) {
    try {
      ReplacementDataSet dataSet = new ReplacementDataSet(new FlatXmlDataSet(stream));
      dataSet.addReplacementObject("[null]", null);
      return dataSet;
    } catch (Exception e) {
      throw translateException("Could not read the dataset stream", e);
    }
  }

  private final IDataSet getCurrentDataSet() {
    try {
      return connection.createDataSet();
    } catch (SQLException e) {
      throw translateException("Could not create the current dataset", e);
    }
  }

  private static RuntimeException translateException(String msg, Exception cause) {
    RuntimeException runtimeException = new RuntimeException(String.format("%s: [%s] %s", msg, cause.getClass().getName(), cause.getMessage()));
    runtimeException.setStackTrace(cause.getStackTrace());
    return runtimeException;
  }

  protected Long getHQLCount(Class<?> hqlClass) {
    String hqlCount = "SELECT count(o) from " + hqlClass.getSimpleName() + " o";
    return (Long) getSession().createQuery(hqlCount).getSingleResult();
  }
}
