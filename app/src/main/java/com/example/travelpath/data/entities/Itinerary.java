package com.example.travelpath.data.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Entité Room représentant un itinéraire généré par le serveur.
 *
 * Champs sérialisés en JSON (fullStepsJson, poiCoordinatesJson) :
 * Room ne stocke pas les listes d'objets directement — on sérialise via Gson
 * à la frontière Remote→Entity dans RemoteMapper.
 *
 * TTL du cache local : {@link #CACHE_TTL_MS} — 7 jours.
 */
@Entity(tableName = "itineraries")
public class Itinerary implements Serializable {

    /** Durée de validité du cache Room : 7 jours. */
    @Ignore
    public static final long CACHE_TTL_MS = TimeUnit.DAYS.toMillis(7);

    // ── Clé primaire ──────────────────────────────────────────────────────────
    @PrimaryKey(autoGenerate = true)
    private int id;

    // ── Données métier ────────────────────────────────────────────────────────
    private String  name;
    private String  destinationCity;
    private String  description;
    private double  cost;
    private String  duration;
    private String  effort;
    private String  weather;
    private String  steps;          // Noms des étapes séparés par " → "
    private String  imageUrl;       // URL de la photo principale
    private String  routeType;      // ECONOMY | BALANCED | COMFORT

    // ── Données enrichies (sérialisées en JSON) ───────────────────────────────
    private String  poiCoordinatesJson;  // [{lat, lng}, ...]
    private String  fullStepsJson;       // Liste complète des PointOfInterest (photos, horaires)
    private String  encodedPolyline;     // Tracé Google Directions API

    // ── Métadonnées locales ───────────────────────────────────────────────────
    private long    cachedAt;   // Timestamp System.currentTimeMillis() à l'insertion
    private boolean isSaved;    // true = sauvegarde explicite par l'utilisateur

    public Itinerary() {}

    // ── Logique métier ────────────────────────────────────────────────────────

    /**
     * Indique si cet itinéraire est encore valide dans le cache local.
     * Un itinéraire sauvegardé explicitement n'expire jamais.
     */
    public boolean isCacheExpired() {
        if (isSaved) return false;
        return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int    getId()                            { return id; }
    public void   setId(int id)                      { this.id = id; }

    public String getName()                          { return name; }
    public void   setName(String v)                  { this.name = v; }

    public String getDestinationCity()               { return destinationCity; }
    public void   setDestinationCity(String v)       { this.destinationCity = v; }

    public String getDescription()                   { return description; }
    public void   setDescription(String v)           { this.description = v; }

    public double getCost()                          { return cost; }
    public void   setCost(double v)                  { this.cost = v; }

    public String getDuration()                      { return duration; }
    public void   setDuration(String v)              { this.duration = v; }

    public String getEffort()                        { return effort; }
    public void   setEffort(String v)                { this.effort = v; }

    public String getWeather()                       { return weather; }
    public void   setWeather(String v)               { this.weather = v; }

    public String getSteps()                         { return steps; }
    public void   setSteps(String v)                 { this.steps = v; }

    public String getImageUrl()                      { return imageUrl; }
    public void   setImageUrl(String v)              { this.imageUrl = v; }

    public String getRouteType()                     { return routeType; }
    public void   setRouteType(String v)             { this.routeType = v; }

    public String getPoiCoordinatesJson()            { return poiCoordinatesJson; }
    public void   setPoiCoordinatesJson(String v)    { this.poiCoordinatesJson = v; }

    public String getFullStepsJson()                 { return fullStepsJson; }
    public void   setFullStepsJson(String v)         { this.fullStepsJson = v; }

    public String getEncodedPolyline()               { return encodedPolyline; }
    public void   setEncodedPolyline(String v)       { this.encodedPolyline = v; }

    public long   getCachedAt()                      { return cachedAt; }
    public void   setCachedAt(long v)                { this.cachedAt = v; }

    public boolean isSaved()                         { return isSaved; }
    public void    setSaved(boolean v)               { this.isSaved = v; }

    @Override
    public String toString() {
        return "[" + routeType + "] " + name + " | " + duration + " | " + cost + "€";
    }
}
