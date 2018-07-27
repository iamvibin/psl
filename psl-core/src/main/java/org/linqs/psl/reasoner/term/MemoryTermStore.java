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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.Config;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.list.UnmodifiableList;

public class MemoryTermStore<E extends Term> implements TermStore<E> {
	public static final String CONFIG_PREFIX = "memorytermstore";

	/**
	 * Initial size for the memory store.
	 */
	public static final String INITIAL_SIZE_KEY = CONFIG_PREFIX + ".initialsize";
	public static final int INITIAL_SIZE_DEFAULT = 5000;

	private ArrayList<E> store;

	/**
	 * A mapping of ground rule to the term indexes associated with
	 * that ground rule.
	 * This is used for updating weights, so we only track weighted
	 * ground rules and terms.
	 * Note that it could be possible to generate multiple terms
	 * for a single ground rule.
	 */
	private Map<WeightedGroundRule, List<Integer>> ruleMapping;

	public MemoryTermStore() {
		this(Config.getInt(INITIAL_SIZE_KEY, INITIAL_SIZE_DEFAULT));
	}

	public MemoryTermStore(int initialSize) {
		store = new ArrayList<E>(initialSize);
		ruleMapping = new HashMap<WeightedGroundRule, List<Integer>>(initialSize);
	}

	@Override
	public synchronized void add(GroundRule rule, E term) {
		if (rule instanceof WeightedGroundRule && term instanceof WeightedTerm) {
			if (!ruleMapping.containsKey((WeightedGroundRule)rule)) {
				ruleMapping.put((WeightedGroundRule)rule, new LinkedList<Integer>());
			}

			ruleMapping.get((WeightedGroundRule)rule).add(new Integer(store.size()));
		}

		store.add(term);
	}

	@Override
	public void clear() {
		if (store != null) {
			store.clear();
		}

		if (ruleMapping != null) {
			ruleMapping.clear();
		}
	}

	@Override
	public void close() {
		clear();

		store = null;
		ruleMapping = null;
	}

	@Override
	public E get(int index) {
		return store.get(index);
	}

	@Override
	public int size() {
		return store.size();
	}

	@Override
	public void ensureCapacity(int capacity) {
		assert(capacity >= 0);

		if (capacity == 0) {
			return;
		}

		store.ensureCapacity(capacity);

		// If the map is empty, then just reallocate it
		// (since we can't add capacity).
		if (ruleMapping.size() == 0) {
			// The default load factor for Java HashMaps is 0.75.
			ruleMapping = new HashMap<WeightedGroundRule, List<Integer>>((int)(capacity / 0.75));
		}
	}

	@Override
	public Iterator<E> iterator() {
		return store.iterator();
	}

	@Override
	public void updateWeight(WeightedGroundRule rule) {
		List<Integer> indexes = ruleMapping.get(rule);
		float weight = (float)rule.getWeight();

		for (int i = 0; i < indexes.size(); i++) {
			((WeightedTerm)store.get(indexes.get(i).intValue())).setWeight(weight);
		}
	}

	@Override
	public List<Integer> getTermIndices(WeightedGroundRule rule) {
		return new UnmodifiableList<Integer>(ruleMapping.get(rule));
	}

	@Override
	public Map<Integer, Integer> sort() {
		// Because there can be multiple terms that have the same atoms,
		// we also need to use the rule's hash.
		final Map<E, Integer> termToHash = new HashMap<E, Integer>();
		for (Map.Entry<WeightedGroundRule, List<Integer>> entry : ruleMapping.entrySet()) {
			WeightedGroundRule rule = entry.getKey();
			for (Integer termIndex : entry.getValue()) {
				E term = store.get(termIndex.intValue());
				termToHash.put(term, HashCode.build(rule.hashCode(), term));
			}
		}

		// Sore the new terms by augmented hash.
		ArrayList<E> newStore = new ArrayList<E>(store);
		Collections.sort(newStore, new Comparator<E>() {
			@Override
			public int compare(E a, E b) {
				return MathUtils.compare(termToHash.get(a).intValue(), termToHash.get(b).intValue());
			}

			@Override
			public boolean equals(Object other) {
				return other != null && this == other;
			}
		});

		// Get a mapping of the new term indexes.
		Map<Integer, Integer> indexRemap = new HashMap<Integer, Integer>();
		for (int oldIndex = 0; oldIndex < store.size(); oldIndex++) {
			E oldTerm = store.get(oldIndex);

			for (int newIndex = 0; newIndex < store.size(); newIndex++) {
				// Note reference equality.
				if (oldTerm ==  newStore.get(newIndex)) {
					indexRemap.put(new Integer(oldIndex), new Integer(newIndex));
					break;
				}
			}
		}

		// Reindex the local variables.
		for (List<Integer> oldIndexes : ruleMapping.values()) {
			for (int i = 0; i < oldIndexes.size(); i++) {
				oldIndexes.set(i, indexRemap.get(oldIndexes.get(i)));
			}
		}

		return indexRemap;
	}
}
