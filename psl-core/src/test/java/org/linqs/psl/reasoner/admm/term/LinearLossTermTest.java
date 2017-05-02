/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.reasoner.admm.term;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LinearLossTermTest {
	private ConfigBundle config;
	
	@Before
	public final void setUp() throws ConfigurationException {
		config = ConfigManager.getManager().getBundle("dummy");
	}

	@Test
	public void testMinimize() {
		/*
		 * Problem 1
		 */
		double[] z = {0.4, 0.5};
		double[] y = {0.0, 0.0};
		double[] coeffs = {0.3, -1.0};
		double weight = 1.0;
		double stepSize = 1.0;
		double[] expected = {0.1, 1.5};
		testProblem(z, y, coeffs, weight, stepSize, expected);
	}
	
	private void testProblem(double[] z, double[] y, double[] coeffs, double weight,
			final double stepSize, double[] expected) {
		List<LocalVariable> variables = new ArrayList<LocalVariable>(z.length);
		List<Double> coeffsList = new ArrayList<Double>(z.length);

		for (int i = 0; i < z.length; i++) {
			variables.add(new LocalVariable(i, z[i]));
			variables.get(i).setLagrange(y[i]);

			coeffsList.add(new Double(coeffs[i]));
		}
		
		LinearLossTerm term = new LinearLossTerm(variables, coeffsList, weight);
		term.minimize(stepSize, z);
		
		for (int i = 0; i < z.length; i++) {
			assertEquals(expected[i], variables.get(i).getValue(), 5e-5);
		}
	}
}