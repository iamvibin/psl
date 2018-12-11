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
package org.linqs.psl.evaluation.statistics;

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.util.MathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Get the language pair precision at 1, 3, and 5.
 * This is super specialized for this project.
 */
public class WikiRankedPrecisionEvaluator extends Evaluator {
    private static final String TEXT_ID_MAPPING_PREDICATE = "TEXTID";

    private static final String[] LANGUAGES = new String[]{"ar", "en", "es", "fr", "ja", "ru"};

    // {LangFrom: {LangTo: LanguagePairStats(count, precision@1, precition@3, precision@5), ...}, ...}
    private Map<String, Map<String, LanguagePairStats>> stats;

    public WikiRankedPrecisionEvaluator() {
    }

    @Override
    public void compute(TrainingMap trainingMap) {
        compute(trainingMap, null);
    }

    @Override
    public void compute(TrainingMap trainingMap, StandardPredicate predicate) {
        if (predicate == null) {
            predicate = StandardPredicate.get("TRANSLATION");
        }

        if (predicate == null || !predicate.getName().equals("TRANSLATION")) {
            throw new IllegalArgumentException("Bad predicate " + predicate + ", need TRANSLATION.");
        }

        Map<String, Map<String, List<Prediction>>> rankedPredictions = computeRanks(trainingMap, predicate);
        stats = computeStats(rankedPredictions);
    }

    @Override
    public double getRepresentativeMetric() {
        return getNetScore(1);
    }

    @Override
    public boolean isHigherRepresentativeBetter() {
        return true;
    }

    @Override
    public String getAllStats() {
        StringBuilder builder = new StringBuilder();

        boolean first = true;

        for (String langFrom : LANGUAGES) {
            for (String langTo : LANGUAGES) {
                LanguagePairStats pairStats = stats.get(langFrom).get(langTo);

                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }

                builder.append(pairStats);
            }
        }

        return builder.toString();
    }

    private double getNetScore(int level) {
        if (level != 1 && level != 3 && level != 5) {
            throw new IllegalArgumentException("Level must be 1, 3, or 5.");
        }

        double delta = 0.0;

        for (String langFrom : LANGUAGES) {
            for (String langTo : LANGUAGES) {
                LanguagePairStats pairStats = stats.get(langFrom).get(langTo);

                if (level == 1) {
                    delta += pairStats.precisionAt1;
                } else if (level == 3) {
                    delta += pairStats.precisionAt3;
                } else {
                    delta += pairStats.precisionAt5;
                }
            }
        }

        return delta;
    }

    private String getLang(String text) {
        return text.substring(0, 2);
    }

    private Map<String, Map<String, LanguagePairStats>> computeStats(Map<String, Map<String, List<Prediction>>> rankedPredictions) {
        Map<String, Map<String, LanguagePairStats>> results = new HashMap<String, Map<String, LanguagePairStats>>();

        for (String langFrom : LANGUAGES) {
            for (String langTo : LANGUAGES) {
                LanguagePairStats pairStats = new LanguagePairStats(langFrom, langTo);
                if (!results.containsKey(langFrom)) {
                    results.put(langFrom, new HashMap<String, LanguagePairStats>());
                }
                results.get(langFrom).put(langTo, pairStats);

                if (langFrom.equals(langTo)) {
                    continue;
                }

                for (String textFrom : rankedPredictions.keySet()) {
                    if (!langFrom.equals(getLang(textFrom))) {
                        continue;
                    }

                    List<Prediction> predictions = rankedPredictions.get(textFrom).get(langTo);
                    if (predictions == null) {
                        continue;
                    }

                    // TODO(eriq): Do we need indovidual counts for each precision.
                    //  If we do, the loop below will throw an index ecetpion.
                    pairStats.count++;

                    // for (int i = 0; i < predictions.size(); i++) {
                    for (int i = 0; i < 5; i++) {
                        if (!predictions.get(i).truth) {
                            continue;
                        }

                        if (i < 1) {
                            pairStats.precisionAt1++;
                            pairStats.precisionAt3++;
                            pairStats.precisionAt5++;
                        } else if (i < 3) {
                            pairStats.precisionAt3++;
                            pairStats.precisionAt5++;
                        } else if (i < 5) {
                            pairStats.precisionAt5++;
                        }

                        break;
                    }
                }

                pairStats.precisionAt1 = pairStats.precisionAt1 / pairStats.count;
                pairStats.precisionAt3 = pairStats.precisionAt3 / pairStats.count;
                pairStats.precisionAt5 = pairStats.precisionAt5 / pairStats.count;
            }
        }

        return results;
    }

    // Returns: {TextFrom: {lengTo: [(textTo, score), ... ranked for score DESC], ...}, ...}
    private Map<String, Map<String, List<Prediction>>> computeRanks(TrainingMap trainingMap, StandardPredicate predicate) {
        Map<String, Map<String, List<Prediction>>> results = new HashMap<String, Map<String, List<Prediction>>>();

        Map<UniqueIntID, String> idTextMapping = loadTextIDMap();

        for (Map.Entry<GroundAtom, GroundAtom> entry : trainingMap.getFullMap()) {
            if (entry.getKey().getPredicate() != predicate) {
                continue;
            }

            GroundAtom truth = entry.getValue();
            GroundAtom predicted = entry.getKey();

            UniqueIntID idFrom = (UniqueIntID)predicted.getArguments()[0];
            UniqueIntID idTo = (UniqueIntID)predicted.getArguments()[1];

            String textFrom = idTextMapping.get(idFrom);
            String textTo = idTextMapping.get(idTo);

            String langTo = getLang(textTo);

            // The truth is binary, so we just need to discriminate between 0.0 and 1.0.
            boolean booleanTruth = (truth.getValue() > 0.5f);

            if (!results.containsKey(textFrom)) {
                results.put(textFrom, new HashMap<String, List<Prediction>>());
            }

            if (!results.get(textFrom).containsKey(langTo)) {
                results.get(textFrom).put(langTo, new ArrayList<Prediction>());
            }

            results.get(textFrom).get(langTo).add(new Prediction(textTo, predicted.getValue(), booleanTruth));
        }

        for (Map<String, List<Prediction>> textPredictions : results.values()) {
            for (List<Prediction> langPredictions : textPredictions.values()) {
                Collections.sort(langPredictions);
            }
        }

        return results;
    }

    private Map<UniqueIntID, String> loadTextIDMap() {
        StandardPredicate predicate = StandardPredicate.get(TEXT_ID_MAPPING_PREDICATE);
        if (predicate == null) {
            throw new IllegalStateException("Could not find the text-id mapping predicate: " + TEXT_ID_MAPPING_PREDICATE);
        }

        // There better be exactly one datastore open.
        DataStore dataStore = RDBMSDataStore.getOpenDataStores().iterator().next();

        ReadableDatabase database = null;
        for (Database potentialDatabase : dataStore.getOpenDatabases()) {
            // The database we are looking for has read partitions.
            // (The truth database only has one write partition.)
            if (potentialDatabase.getReadPartitions().size() != 0) {
                database = (ReadableDatabase)potentialDatabase;
                break;
            }
        }

        Map<UniqueIntID, String> mapping = new HashMap<UniqueIntID, String>();

        for (GroundAtom atom : database.getAllGroundAtoms(predicate)) {
            mapping.put((UniqueIntID)atom.getArguments()[1], ((StringAttribute)atom.getArguments()[0]).getValue());
        }

        return mapping;
    }

    private static class Prediction implements Comparable<Prediction> {
        public String text;
        public float score;
        public boolean truth;

        public Prediction(String text, float score, boolean truth) {
            this.text = text;
            this.score = score;
            this.truth = truth;
        }

        @Override
        public int compareTo(Prediction other) {
            if (score > other.score) {
                return -1;
            }

            if (score < other.score) {
                return 1;
            }

            return 0;
        }
    }

    private static class LanguagePairStats implements Comparable<LanguagePairStats> {
        public String langFrom;
        public String langTo;
        public int count;

        public double precisionAt1;
        public double precisionAt3;
        public double precisionAt5;

        public LanguagePairStats(String langFrom, String langTo) {
            this.langFrom = langFrom;
            this.langTo = langTo;

            count = 0;

            precisionAt1 = 0.0;
            precisionAt3 = 0.0;
            precisionAt5 = 0.0;
        }

        @Override
        public int compareTo(LanguagePairStats other) {
            int result = langFrom.compareTo(other.langFrom);
            if (result != 0) {
                return result;
            }

            return langTo.compareTo(other.langTo);
        }

        @Override
        public String toString() {
            String id = langFrom + "-" + langTo;
 
            if (count == 0) {
                return String.format("%s_1: -1, %s_3: -1, %s_5: -1", id, id, id);
            }

            return String.format("%s_1: %f, %s_3: %f, %s_5: %f", id, id, id, precisionAt1, precisionAt3, precisionAt5);
        }
    }
}
