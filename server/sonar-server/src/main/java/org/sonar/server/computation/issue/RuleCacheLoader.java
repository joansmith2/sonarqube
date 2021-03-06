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
package org.sonar.server.computation.issue;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.sonar.api.rule.RuleKey;
import org.sonar.db.DbSession;
import org.sonar.db.MyBatis;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.computation.batch.BatchReportReader;
import org.sonar.server.db.DbClient;
import org.sonar.server.util.cache.CacheLoader;

import static com.google.common.collect.FluentIterable.from;
import static org.sonar.core.rule.RuleKeyFunctions.stringToRuleKey;

public class RuleCacheLoader implements CacheLoader<RuleKey, Rule> {

  private final DbClient dbClient;
  private final Set<RuleKey> activatedKeys;

  public RuleCacheLoader(DbClient dbClient, BatchReportReader batchReportReader) {
    this.dbClient = dbClient;
    this.activatedKeys = from(batchReportReader.readMetadata().getActiveRuleKeyList()).transform(stringToRuleKey()).toSet();
  }

  @Override
  public Rule load(RuleKey key) {
    DbSession session = dbClient.openSession(false);
    try {
      RuleDto dto = dbClient.ruleDao().getNullableByKey(session, key);
      if (dto != null) {
        return new RuleImpl(dto, activatedKeys.contains(key));
      }
      return null;
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  @Override
  public Map<RuleKey, Rule> loadAll(Collection<? extends RuleKey> keys) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
