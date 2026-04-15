package com.example.travelpath.data.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsule tous les critères de recherche fournis par l'utilisateur.
 */
public class SearchCriteria implements Serializable {

    // ── Constantes Effort ────────────────────────────────────────────────────
    public static final String EFFORT_EASY     = "Easy";
    public static final String EFFORT_MODERATE = "Moderate";
    public static final String EFFORT_HIGH     = "High";

    // ── Champs ───────────────────────────────────────────────────────────────
    private String       destinationCity = "Paris"; // Ville par défaut
    private double       budgetMin;
    private double       budgetMax;
    private double       durationMinHours;
    private double       durationMaxHours;
    private List<String> interests;          // ex: ["Culture", "Food"]
    private String       effortLevel;        // EFFORT_EASY / MODERATE / HIGH
    private List<String> weatherPreferences; // ex: ["SUN", "CLOUD"]

    // ── Constructeur ─────────────────────────────────────────────────────────
    public SearchCriteria() {
        this.interests          = new ArrayList<>();
        this.weatherPreferences = new ArrayList<>();
        this.effortLevel        = EFFORT_EASY;
    }

    // ── Builder fluide ───────────────────────────────────────────────────────
    public SearchCriteria destination(String city) {
        this.destinationCity = city;
        return this;
    }

    public SearchCriteria budget(double min, double max) {
        this.budgetMin = min;
        this.budgetMax = max;
        return this;
    }

    public SearchCriteria duration(double minHours, double maxHours) {
        this.durationMinHours = minHours;
        this.durationMaxHours = maxHours;
        return this;
    }

    public SearchCriteria interests(List<String> interests) {
        this.interests = interests != null ? interests : new ArrayList<>();
        return this;
    }

    public SearchCriteria effort(String effortLevel) {
        this.effortLevel = effortLevel;
        return this;
    }

    public SearchCriteria weather(List<String> preferences) {
        this.weatherPreferences = preferences != null ? preferences : new ArrayList<>();
        return this;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public String       getDestinationCity()     { return destinationCity; }
    public double       getBudgetMin()           { return budgetMin; }
    public double       getBudgetMax()           { return budgetMax; }
    public double       getDurationMinHours()    { return durationMinHours; }
    public double       getDurationMaxHours()    { return durationMaxHours; }
    public List<String> getInterests()           { return interests; }
    public String       getEffortLevel()         { return effortLevel; }
    public List<String> getWeatherPreferences()  { return weatherPreferences; }

    public void setDestinationCity(String v)     { this.destinationCity = v; }
    public void setBudgetMin(double v)           { this.budgetMin = v; }
    public void setBudgetMax(double v)           { this.budgetMax = v; }
    public void setDurationMinHours(double v)    { this.durationMinHours = v; }
    public void setDurationMaxHours(double v)    { this.durationMaxHours = v; }
    public void setInterests(List<String> v)     { this.interests = v; }
    public void setEffortLevel(String v)         { this.effortLevel = v; }
    public void setWeatherPreferences(List<String> v) { this.weatherPreferences = v; }
}
