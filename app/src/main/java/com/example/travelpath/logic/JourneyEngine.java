package com.example.travelpath.logic;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JourneyEngine {

    private static final int MAX_STEPS = 6;
    private static final double DIVERSITY_PENALTY = 0.55;

    private static final double[] W_ECONOMY  = {0.70, 0.20, 0.10};
    private static final double[] W_BALANCED = {0.30, 0.50, 0.20};
    private static final double[] W_COMFORT  = {0.10, 0.30, 0.60};

    private List<PointOfInterest> pois;
    private final RoutingService routingService;

    public JourneyEngine() {
        this.pois = PointOfInterest.getMockedPOIs();
        this.routingService = new RoutingService();
    }

    public void setPois(List<PointOfInterest> pois) {
        if (pois != null && !pois.isEmpty()) this.pois = pois;
    }

    public List<Itinerary> generateRoutes(SearchCriteria criteria) {
        if (criteria == null) return Collections.emptyList();

        List<PointOfInterest> pool = filterPOIs(criteria);
        if (pool.isEmpty()) return Collections.emptyList();

        List<Itinerary> results = new ArrayList<>();
        results.add(buildChronologicalItinerary(pool, criteria, RouteMode.ECONOMY));
        results.add(buildChronologicalItinerary(pool, criteria, RouteMode.BALANCED));
        results.add(buildChronologicalItinerary(pool, criteria, RouteMode.COMFORT));

        results.removeIf(it -> it == null);
        return results;
    }

    private List<PointOfInterest> filterPOIs(SearchCriteria criteria) {
        List<PointOfInterest> result = new ArrayList<>();
        for (PointOfInterest poi : pois) {
            if (!poi.isWeatherCompatible(criteria.getWeatherPreferences())) continue;
            if (!poi.matchesInterests(criteria.getInterests())) continue;
            result.add(poi);
        }
        return result;
    }

    private Itinerary buildChronologicalItinerary(List<PointOfInterest> pool, SearchCriteria criteria, RouteMode mode) {
        List<PointOfInterest> selected = new ArrayList<>();
        Set<String> usedCategories = new HashSet<>();
        Set<String> usedIds = new HashSet<>();

        double totalCost = 0.0;
        double totalDuration = 0.0; // Inclut visite + trajet
        PointOfInterest lastPoi = null;

        // ── Chronologie : Matin -> Après-midi -> Soir ──────────────────────────
        String[] slots = {PointOfInterest.SLOT_MORNING, PointOfInterest.SLOT_AFTERNOON, PointOfInterest.SLOT_EVENING};

        for (String slot : slots) {
            PointOfInterest best = findBestForSlot(pool, slot, mode, usedIds, usedCategories, criteria, lastPoi, totalCost, totalDuration);
            if (best != null) {
                if (lastPoi != null) {
                    double dist = routingService.calculateDistance(lastPoi.getLatitude(), lastPoi.getLongitude(), best.getLatitude(), best.getLongitude());
                    totalDuration += routingService.estimateTravelTimeHours(dist);
                }
                
                selected.add(best);
                usedIds.add(best.getId());
                usedCategories.add(best.getCategory());
                totalCost += best.getBaseCost();
                totalDuration += best.getAverageDurationHours();
                lastPoi = best;
            }
            
            if (totalDuration >= criteria.getDurationMaxHours() || selected.size() >= MAX_STEPS) break;
        }

        if (selected.isEmpty()) return null;

        return assembleItinerary(selected, totalCost, totalDuration, mode, criteria);
    }

    private PointOfInterest findBestForSlot(List<PointOfInterest> pool, String slot, RouteMode mode, 
                                           Set<String> usedIds, Set<String> usedCats, 
                                           SearchCriteria criteria, PointOfInterest lastPoi,
                                           double currentCost, double currentDuration) {
        PointOfInterest best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (PointOfInterest poi : pool) {
            if (usedIds.contains(poi.getId())) continue;
            if (!poi.getPreferredTimeSlot().equals(slot)) continue;

            double travelTime = 0;
            if (lastPoi != null) {
                double dist = routingService.calculateDistance(lastPoi.getLatitude(), lastPoi.getLongitude(), poi.getLatitude(), poi.getLongitude());
                travelTime = routingService.estimateTravelTimeHours(dist);
            }

            if (currentCost + poi.getBaseCost() > criteria.getBudgetMax()) continue;
            if (currentDuration + travelTime + poi.getAverageDurationHours() > criteria.getDurationMaxHours()) continue;

            double score = scorePOI(poi, mode, usedCats, criteria);
            // Bonus de proximité
            if (lastPoi != null) {
                double dist = routingService.calculateDistance(lastPoi.getLatitude(), lastPoi.getLongitude(), poi.getLatitude(), poi.getLongitude());
                score += (1.0 / (1.0 + dist)) * 0.2; 
            }

            if (score > bestScore) {
                bestScore = score;
                best = poi;
            }
        }
        return best;
    }

    private double scorePOI(PointOfInterest poi, RouteMode mode, Set<String> categoriesUsed, SearchCriteria criteria) {
        double[] w = weightsFor(mode);
        double costScore = (poi.getBaseCost() <= 0) ? 1.0 : 1.0 / (1.0 + poi.getBaseCost());
        double ratingScore = poi.getRating() / 5.0;
        double comfortScore = poi.getComfortLevel() / 5.0;

        double score = w[0] * costScore + w[1] * ratingScore + w[2] * comfortScore;
        if (categoriesUsed.contains(poi.getCategory())) score *= DIVERSITY_PENALTY;
        return score;
    }

    private Itinerary assembleItinerary(List<PointOfInterest> steps, double totalCost, double totalDuration, RouteMode mode, SearchCriteria criteria) {
        Itinerary itinerary = new Itinerary();
        itinerary.setName(generateTitle(steps, mode));
        itinerary.setDescription(generateDescription(steps, mode, criteria));
        itinerary.setCost(Math.round(totalCost * 100.0) / 100.0);
        itinerary.setDuration(String.format(Locale.US, "%.1fh", totalDuration));
        itinerary.setEffort(computeDominantEffort(steps));
        itinerary.setWeather(computeWeatherSummary(steps));
        itinerary.setSteps(formatSteps(steps));
        itinerary.setRouteType(mode.name());
        return itinerary;
    }

    private String generateTitle(List<PointOfInterest> steps, RouteMode mode) {
        String anchor = steps.get(0).getName();
        switch (mode) {
            case ECONOMY:  return "Budget: " + anchor + " & others";
            case COMFORT:  return "Premium: " + anchor + " experience";
            default:       return "The " + anchor + " loop";
        }
    }

    private String generateDescription(List<PointOfInterest> steps, RouteMode mode, SearchCriteria criteria) {
        return String.format("A %s journey with %d stops across %s.", mode.name().toLowerCase(), steps.size(), criteria.getDestinationCity());
    }

    private String formatSteps(List<PointOfInterest> steps) {
        List<String> names = new ArrayList<>();
        for (PointOfInterest p : steps) names.add(p.getName());
        return String.join(" → ", names);
    }

    private String computeDominantEffort(List<PointOfInterest> steps) {
        int total = 0;
        for (PointOfInterest p : steps) total += p.getEffortScore();
        double avg = (double) total / steps.size();
        return (avg <= 1.5) ? "Easy" : (avg <= 2.5) ? "Moderate" : "High";
    }

    private String computeWeatherSummary(List<PointOfInterest> steps) {
        return "Optimal in SUN";
    }

    private double[] weightsFor(RouteMode mode) {
        switch (mode) {
            case ECONOMY: return W_ECONOMY;
            case COMFORT: return W_COMFORT;
            default:      return W_BALANCED;
        }
    }

    public enum RouteMode { ECONOMY, BALANCED, COMFORT }
}
