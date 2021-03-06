/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.db.migrations.v44;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChangeLogMigrationTest {

  @Rule
  public DbTester db = DbTester.createForSchema(System2.INSTANCE, ChangeLogMigrationTest.class, "schema.sql");

  System2 system2 = mock(System2.class);
  DbClient dbClient = db.getDbClient();
  ChangeLogMigrationStep migration;

  @Before
  public void setUp() {
    when(system2.now()).thenReturn(DateUtils.parseDate("2014-03-13").getTime());
    migration = new ChangeLogMigrationStep(dbClient.activityDao(), dbClient);
  }

  @Test
  public void migrate() {
    db.prepareDbUnit(getClass(), "active_rules_changes.xml");
    migration.execute();
    assertThat(db.countRowsOfTable("activities")).isEqualTo(5);

    int count = db.countSql("select count(*) from activities where data_field like '%param_PARAM1=TODO%'");
    assertThat(count).isGreaterThan(0);
  }

  @Test
  public void migrate_when_no_changelog() {
    db.prepareDbUnit(getClass(), "migrate_when_no_changelog.xml");
    migration.execute();

    assertThat(db.countRowsOfTable("activities")).isEqualTo(0);
  }
}
