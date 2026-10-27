package com.example.medinex;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RxNormResponse {

    @SerializedName("approximateGroup")
    public ApproximateGroup approximateGroup;

    public static class ApproximateGroup {

        @SerializedName("inputTerm")
        public String inputTerm;

        @SerializedName("candidate")
        public List<Candidate> candidate;
    }

    public static class Candidate {

        @SerializedName("rxcui")
        public String rxcui;

        @SerializedName("rxaui")
        public String rxaui;

        @SerializedName("name")
        public String name;

        @SerializedName("score")
        public String score;

        @SerializedName("rank")
        public String rank;

        @SerializedName("source")
        public String source;
    }

    /**
     * Returns the best candidate that contains a non-null, non-empty medicine name.
     * RxNorm returns candidates from multiple sources (MMSL, RXNORM, ATC, USP, DRUGBANK).
     * Candidates from MMSL or GS often leave the 'name' field null, while RXNORM or ATC
     * populate it.
     */
    public Candidate getBestCandidate() {
        if (approximateGroup == null ||
                approximateGroup.candidate == null ||
                approximateGroup.candidate.isEmpty()) {
            return null;
        }

        // 1. Look for highest ranked candidate with a valid name
        for (Candidate c : approximateGroup.candidate) {
            if (c != null && c.name != null && !c.name.trim().isEmpty()) {
                return c;
            }
        }

        // 2. Fallback to candidate 0 if no candidate provided a name directly
        return approximateGroup.candidate.get(0);
    }

    /**
     * Returns the best matching candidate whose name or RXCUI is relevant to the query term.
     */
    public Candidate getBestCandidateForTerm(String queryTerm) {
        if (approximateGroup == null ||
                approximateGroup.candidate == null ||
                approximateGroup.candidate.isEmpty() ||
                queryTerm == null || queryTerm.trim().isEmpty()) {
            return getBestCandidate();
        }

        String q = queryTerm.trim().toLowerCase();

        // Check if any candidate has a name containing or closely matching queryTerm
        for (Candidate c : approximateGroup.candidate) {
            if (c != null && c.name != null && !c.name.trim().isEmpty()) {
                String cName = c.name.toLowerCase();
                if (cName.contains(q) || q.contains(cName)) {
                    return c;
                }
            }
        }

        return getBestCandidate();
    }

    /**
     * Returns the best matching medicine name, or null if no valid name exists.
     */
    public String getBestMedicineName() {
        Candidate candidate = getBestCandidate();
        if (candidate == null || candidate.name == null || candidate.name.trim().isEmpty()) {
            return null;
        }
        return candidate.name.trim();
    }

    /**
     * Returns RxNorm CUI of the best match.
     */
    public String getBestRxCui() {
        Candidate candidate = getBestCandidate();
        if (candidate == null || candidate.rxcui == null || candidate.rxcui.trim().isEmpty()) {
            return null;
        }
        return candidate.rxcui.trim();
    }

    /**
     * Returns the match score as a String, or null if unavailable.
     */
    public String getBestScore() {
        Candidate candidate = getBestCandidate();
        if (candidate == null || candidate.score == null) {
            return null;
        }
        return candidate.score.trim();
    }
}