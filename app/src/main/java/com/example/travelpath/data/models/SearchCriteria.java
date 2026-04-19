package com.example.travelpath.data.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsule tous les critères de recherche de l'utilisateur.
 *
 * Usage via le builder fluide :
 * <pre>
 *   SearchCriteria c = new SearchCriteria.Builder()
 *       .destinationCity("Paris")
 *       .budget(20, 150)
 *       .duration(4, 8)
 *       .interests(Arrays.asList("Culture", "Food"))
 *       .effortLevel(SearchCriteria.EFFORT_EASY)
 *       .build();
 * </pre>
 */
public final class SearchCriteria implements Serializable {

    // ── Constantes effort ─────────────────────────────────────────────────────
    public static final String EFFORT_EASY     = "Easy";
    public static final String EFFORT_MODERATE = "Moderate";
    public static final String EFFORT_HIGH     = "High";

    // ── Champs ────────────────────────────────────────────────────────────────
    private final String       destinationCity;
    private final String       destinationPlaceId;
    private final List<String> mandatoryPois;
    private final List<String> excludeIds;
    private final double       budgetMin;
    private final double       budgetMax;
    private final double       durationMinHours;
    private final double       durationMaxHours;
    private final List<String> interests;
    private final String       effortLevel;
    private final List<String> weatherPreferences;

    private SearchCriteria(Builder b) {
        this.destinationCity    = b.destinationCity;
        this.destinationPlaceId = b.destinationPlaceId;
        this.mandatoryPois      = b.mandatoryPois;
        this.excludeIds         = b.excludeIds;
        this.budgetMin          = b.budgetMin;
        this.budgetMax          = b.budgetMax;
        this.durationMinHours   = b.durationMinHours;
        this.durationMaxHours   = b.durationMaxHours;
        this.interests          = b.interests;
        this.effortLevel        = b.effortLevel;
        this.weatherPreferences = b.weatherPreferences;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String       getDestinationCity()    { return destinationCity; }
    public String       getDestinationPlaceId() { return destinationPlaceId; }
    public List<String> getMandatoryPois()      { return mandatoryPois; }
    public List<String> getExcludeIds()         { return excludeIds; }
    public double       getBudgetMin()          { return budgetMin; }
    public double       getBudgetMax()          { return budgetMax; }
    public double       getDurationMinHours()   { return durationMinHours; }
    public double       getDurationMaxHours()   { return durationMaxHours; }
    public List<String> getInterests()          { return interests; }
    public String       getEffortLevel()        { return effortLevel; }
    public List<String> getWeatherPreferences() { return weatherPreferences; }

    /**
     * Retourne une copie de ces critères avec une liste d'IDs exclus mise à jour.
     * Utilisé pour la regénération après un délike.
     */
    public SearchCriteria withExcludeIds(List<String> ids) {
        return new Builder(this).excludeIds(ids).build();
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String       destinationCity    = "Paris";
        private String       destinationPlaceId = null;
        private List<String> mandatoryPois      = new ArrayList<>();
        private List<String> excludeIds         = new ArrayList<>();
        private double       budgetMin          = 0;
        private double       budgetMax          = 200;
        private double       durationMinHours   = 3;
        private double       durationMaxHours   = 8;
        private List<String> interests          = new ArrayList<>();
        private String       effortLevel        = EFFORT_EASY;
        private List<String> weatherPreferences = new ArrayList<>();

        public Builder() {}

        /** Constructeur de copie — pour withExcludeIds(). */
        Builder(SearchCriteria src) {
            this.destinationCity    = src.destinationCity;
            this.destinationPlaceId = src.destinationPlaceId;
            this.mandatoryPois      = src.mandatoryPois;
            this.excludeIds         = src.excludeIds;
            this.budgetMin          = src.budgetMin;
            this.budgetMax          = src.budgetMax;
            this.durationMinHours   = src.durationMinHours;
            this.durationMaxHours   = src.durationMaxHours;
            this.interests          = src.interests;
            this.effortLevel        = src.effortLevel;
            this.weatherPreferences = src.weatherPreferences;
        }

        public Builder destinationCity(String v)         { this.destinationCity = v; return this; }
        public Builder destinationPlaceId(String v)      { this.destinationPlaceId = v; return this; }
        public Builder mandatoryPois(List<String> v)     { this.mandatoryPois = v != null ? v : new ArrayList<>(); return this; }
        public Builder excludeIds(List<String> v)        { this.excludeIds = v != null ? v : new ArrayList<>(); return this; }
        public Builder budget(double min, double max)    { this.budgetMin = min; this.budgetMax = max; return this; }
        public Builder duration(double min, double max)  { this.durationMinHours = min; this.durationMaxHours = max; return this; }
        public Builder interests(List<String> v)         { this.interests = v != null ? v : new ArrayList<>(); return this; }
        public Builder effortLevel(String v)             { this.effortLevel = v; return this; }
        public Builder weatherPreferences(List<String> v){ this.weatherPreferences = v != null ? v : new ArrayList<>(); return this; }
        public SearchCriteria build()                    { return new SearchCriteria(this); }
    }
}
