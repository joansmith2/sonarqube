/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
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
package org.sonar.core.technicaldebt.functions;

import org.sonar.api.rules.Violation;
import org.sonar.core.technicaldebt.TechnicalDebtConverter;
import org.sonar.core.technicaldebt.TechnicalDebtRequirement;

import java.util.Collection;

public final class LinearWithThresholdFunction extends LinearFunction {

  public static final String FUNCTION_LINEAR_WITH_THRESHOLD = "linear_threshold";

  public LinearWithThresholdFunction(TechnicalDebtConverter converter) {
    super(converter);
  }

  public String getKey() {
    return FUNCTION_LINEAR_WITH_THRESHOLD;
  }

  public double costInHours(TechnicalDebtRequirement requirement, Collection<Violation> violations) {
    if (violations.isEmpty()) {
      return 0.0;
    }
    double thresholdCost = getConverter().toDays(requirement.getOffset());
    double violationsCost = super.costInHours(requirement, violations);
    return violationsCost > thresholdCost ? violationsCost : thresholdCost;
  }

}
