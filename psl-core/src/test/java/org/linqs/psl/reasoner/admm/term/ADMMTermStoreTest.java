/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
import static org.junit.Assert.fail;

import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ADMMTermStoreTest {
	@After
	public void cleanup() {
		Config.clear();
	}

	/**
	 * Check to make sure that sorting the term store is different than the non-sorted order,
	 * and that sorting generates the same order in multiple runs.
	 */
	@Test
	public void sortBaseTest() {
		Config.setProperty(InferenceApplication.SORT_TERMS_KEY, new Boolean(true));
		ADMMTermStore store1 = runInference();

		Config.setProperty(InferenceApplication.SORT_TERMS_KEY, new Boolean(false));
		ADMMTermStore store2 = runInference();

		Config.setProperty(InferenceApplication.SORT_TERMS_KEY, new Boolean(true));
		ADMMTermStore store3 = runInference();

		// Ensure that the first and second terms are not equal.
		checkStoreInequality(store1, store2);

		// First and third should be equal.
		checkStoreEquality(store1, store3);
	}

	private void checkStoreInequality(ADMMTermStore store1, ADMMTermStore store2) {
		// TEST
	}

	// TODO(eriq): This will be broken until hash codes are implemented for all ADMMObjectiveTerms.
	private void checkStoreEquality(ADMMTermStore store1, ADMMTermStore store2) {
		assertEquals(store1.size(), store2.size());

		for (int i = 0; i < store1.size(); i++) {
			// TEST
			System.out.println("" + store1.get(i) + " -- " + store2.get(i));

			assertEquals(store1.get(i).hashCode(), store2.get(i).hashCode());
		}

		assertEquals(store1.getNumLocalVariables(), store2.getNumLocalVariables());
		assertEquals(store1.getNumGlobalVariables(), store2.getNumGlobalVariables());

		for (int i = 0; i < store1.getNumGlobalVariables(); i++) {
			List<LocalVariable> locals1 = store1.getLocalVariables(i);
			List<LocalVariable> locals2 = store2.getLocalVariables(i);

			assertEquals(locals1.size(), locals2.size());

			for (int j = 0; j < locals1.size(); j++) {
				assertEquals(locals1.get(j), locals2.get(j));
			}
		}
	}

	private ADMMTermStore runInference() {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel();

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
		MPEInference mpe = new MPEInference(info.model, inferDB);

		ADMMTermStore store = (ADMMTermStore)mpe.getTermStore();

		mpe.inference();
		// TEST
		// mpe.close();
		inferDB.close();

		return store;
	}
}
