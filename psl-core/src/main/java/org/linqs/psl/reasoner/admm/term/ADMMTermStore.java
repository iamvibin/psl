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

import org.linqs.psl.config.Config;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.RandUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A TermStore specifically for ADMM terms.
 * The actually terms will just be held in a standard TermStore (a MemoryTermStore if not specified).
 * This class will focus on keeping track of the variables in the terms.
 */
public class ADMMTermStore implements TermStore<ADMMObjectiveTerm> {
	public static final String CONFIG_PREFIX = "admmmemorytermstore";

	/**
	 * Initial size for the memory store.
	 */
	public static final String INTERNAL_STORE_KEY = CONFIG_PREFIX + ".internalstore";
	public static final String INTERNAL_STORE_DEFAULT = "org.linqs.psl.reasoner.term.MemoryTermStore";

	// Keep an internal store to hold the terms while this class focus on variables.
	private TermStore<ADMMObjectiveTerm> store;

	// A mapping of atom variables to their global (consensus) variable identifier.
	private Map<AtomFunctionVariable, Integer> variableIndexes;

	/**
	 * The local variables used in each term.
	 * Indexed by the global associated with each local.
	 */
	private List<List<LocalVariable>> localVariables;

	/**
	 * The total number of all local variables (the sum of the sizes of each listin |localVariables|.
	 */
	private int numLocalVariables;

	@SuppressWarnings("unchecked")
	public ADMMTermStore() {
		this((TermStore<ADMMObjectiveTerm>)Config.getNewObject(INTERNAL_STORE_KEY, INTERNAL_STORE_DEFAULT));
	}

	public ADMMTermStore(TermStore<ADMMObjectiveTerm> store) {
		this.store = store;
		variableIndexes = new HashMap<AtomFunctionVariable, Integer>();
		localVariables = new ArrayList<List<LocalVariable>>();
		numLocalVariables = 0;
	}

	/**
	 * Create a local variable and ensure that a global copy is registered.
	 */
	public synchronized LocalVariable createLocalVariable(AtomFunctionVariable atomVariable) {
		numLocalVariables++;

		int globalId;
		// Check if the global copy has already been registered.
		if (variableIndexes.containsKey(atomVariable)) {
			globalId = variableIndexes.get(atomVariable).intValue();
		} else {
			// If the global copy has not been registered, register it and prep it's local copies.
			globalId = variableIndexes.size();
			variableIndexes.put(atomVariable, globalId);
			localVariables.add(new ArrayList<LocalVariable>());
		}

		LocalVariable localVariable = new LocalVariable(globalId, (float)atomVariable.getValue());
		localVariables.get(globalId).add(localVariable);

		return localVariable;
	}

	public int getNumLocalVariables() {
		return numLocalVariables;
	}

	public int getNumGlobalVariables() {
		return variableIndexes.size();
	}

	public List<LocalVariable> getLocalVariables(int globalId) {
		return localVariables.get(globalId);
	}

	/**
	 * Update the global variables (AtomFunctionVariables).
	 * The passed in values in indexed according to global id.
	 */
	public void updateVariables(float[] values) {
		for (Map.Entry<AtomFunctionVariable, Integer> entry : variableIndexes.entrySet()) {
			entry.getKey().setValue(values[entry.getValue().intValue()]);
		}
	}

	/**
	 * Get the values from the atoms corresponding to global (consensus)
	 * variables and put them in the output array.
	 */
	public void getAtomValues(float[] values) {
		for (Map.Entry<AtomFunctionVariable, Integer> entry : variableIndexes.entrySet()) {
			values[entry.getValue().intValue()] = (float)entry.getKey().getValue();
		}
	}

	public void resetLocalVairables() {
		resetLocalVairables(ADMMReasoner.InitialValue.RANDOM);
	}

	public void resetLocalVairables(ADMMReasoner.InitialValue initialValue) {
		for (Map.Entry<AtomFunctionVariable, Integer> entry : variableIndexes.entrySet()) {
			for (LocalVariable local : localVariables.get(entry.getValue().intValue())) {
				if (initialValue == ADMMReasoner.InitialValue.ZERO) {
					local.setValue(0.0f);
				} else if (initialValue == ADMMReasoner.InitialValue.RANDOM) {
					local.setValue(RandUtils.nextFloat());
				} else if (initialValue == ADMMReasoner.InitialValue.ATOM) {
					local.setValue((float)(entry.getKey().getValue()));
				} else {
					throw new IllegalStateException("Unknown initial consensus value: " + initialValue);
				}

				local.setLagrange(0.0f);
			}
		}
	}

	@Override
	public void add(GroundRule rule, ADMMObjectiveTerm term) {
		store.add(rule, term);
	}

	@Override
	public void clear() {
		if (store != null) {
			store.clear();
		}

		if (variableIndexes != null) {
			variableIndexes.clear();
		}

		if (localVariables != null) {
			localVariables.clear();
		}

		numLocalVariables = 0;
	}

	@Override
	public void close() {
		clear();

		if (store != null) {
			store.close();
			store = null;
		}

		variableIndexes = null;
		localVariables = null;
	}

	@Override
	public ADMMObjectiveTerm get(int index) {
		return store.get(index);
	}

	@Override
	public int size() {
		return store.size();
	}

	@Override
	public void ensureCapacity(int capacity) {
		store.ensureCapacity(capacity);
	}

	@Override
	public Iterator<ADMMObjectiveTerm> iterator() {
		return store.iterator();
	}

	@Override
	public void updateWeight(WeightedGroundRule rule) {
		store.updateWeight(rule);
	}

	@Override
	public List<Integer> getTermIndices(WeightedGroundRule rule) {
		return store.getTermIndices(rule);
	}

	@Override
	public Map<Integer, Integer> sort() {
		Map<Integer, Integer> termIndexMapping = store.sort();

		// Sort atom variables.
		// Use the new index as the gloabal variable index.
		List<AtomFunctionVariable> sortedAtomVariables = new ArrayList<AtomFunctionVariable>(variableIndexes.keySet());
		Collections.sort(sortedAtomVariables, new Comparator<AtomFunctionVariable>() {
			@Override
			public int compare(AtomFunctionVariable a, AtomFunctionVariable b) {
				return MathUtils.compare(a.hashCode(), b.hashCode());
			}

			@Override
			public boolean equals(Object other) {
				return other != null && this == other;
			}
		});

		// Build mapping of new to old atom variable index.
		Map<Integer, Integer> atomVariableIndexMap = new HashMap<Integer, Integer>();
		for (int newIndex = 0; newIndex < variableIndexes.size(); newIndex++) {
			atomVariableIndexMap.put(variableIndexes.get(sortedAtomVariables.get(newIndex)), new Integer(newIndex));
		}

      // TEST
      System.out.println("Store   Size: " + store.size());
      System.out.println("Locals  Size: " + localVariables.size());
      System.out.println("Mapping Size: " + termIndexMapping.size());
      System.out.println("Atom Variable Size: " + termIndexMapping.size());

		// Reindex local variables.
		List<List<LocalVariable>> newLocalVariables = new ArrayList<List<LocalVariable>>(localVariables.size());
		for (int oldIndex = 0; oldIndex < localVariables.size(); oldIndex++) {
         newLocalVariables.add(null);
      }

		for (int oldIndex = 0; oldIndex < localVariables.size(); oldIndex++) {
			int newIndex = atomVariableIndexMap.get(new Integer(oldIndex)).intValue();

         // TEST
         System.out.println("" + oldIndex + " -> " + newIndex);

			newLocalVariables.set(newIndex, localVariables.get(oldIndex));
		}
		localVariables = newLocalVariables;

		// Re-map atom variables.
		variableIndexes.clear();
		for (int i = 0; i < sortedAtomVariables.size(); i++) {
			variableIndexes.put(sortedAtomVariables.get(i), new Integer(i));
		}

		// Change the global index of each local variable.
		for (List<LocalVariable> locals : localVariables) {
			for (LocalVariable variable : locals) {
				Integer oldId = new Integer(variable.getGlobalId());
				variable.setGlobalId(atomVariableIndexMap.get(oldId).intValue());
			}
		}

		return termIndexMapping;
	}
}
