import { SearchCriteria } from '../models';

/**
 * Valide les critères de recherche fournis par le client Android.
 *
 * Séparé du service métier pour respecter SRP :
 * JourneyService génère — Validator vérifie.
 *
 * Retourne un tableau de messages d'erreur (vide = valide).
 */
export class SearchCriteriaValidator {

    validate(data: unknown): { valid: boolean; errors: string[] } {
        const errors: string[] = [];

        if (!data || typeof data !== 'object') {
            return { valid: false, errors: ['Le payload est vide ou invalide.'] };
        }

        const c = data as Partial<SearchCriteria>;

        if (!c.destinationCity || typeof c.destinationCity !== 'string' || c.destinationCity.trim() === '') {
            errors.push('destinationCity est requis.');
        }

        if (typeof c.budgetMin !== 'number' || typeof c.budgetMax !== 'number') {
            errors.push('budgetMin et budgetMax doivent être des nombres.');
        } else if (c.budgetMin < 0 || c.budgetMax < 0) {
            errors.push('Le budget ne peut pas être négatif.');
        } else if (c.budgetMin > c.budgetMax) {
            errors.push('budgetMin ne peut pas être supérieur à budgetMax.');
        }

        if (typeof c.durationMinHours !== 'number' || typeof c.durationMaxHours !== 'number') {
            errors.push('durationMinHours et durationMaxHours doivent être des nombres.');
        } else if (c.durationMinHours <= 0 || c.durationMaxHours <= 0) {
            errors.push('Les durées doivent être strictement positives.');
        } else if (c.durationMinHours > c.durationMaxHours) {
            errors.push('durationMinHours ne peut pas être supérieur à durationMaxHours.');
        }

        if (!Array.isArray(c.interests) || c.interests.length === 0) {
            errors.push('interests doit être un tableau non vide.');
        }

        return { valid: errors.length === 0, errors };
    }

    /**
     * Normalise et complète les valeurs optionnelles avec des défauts sûrs.
     * À appeler après une validation réussie.
     */
    normalize(data: unknown): SearchCriteria {
        const raw = data as Partial<SearchCriteria>;
        return {
            destinationCity:     (raw.destinationCity ?? '').trim(),
            destinationPlaceId:  raw.destinationPlaceId,
            mandatoryPois:       raw.mandatoryPois       ?? [],
            budgetMin:           raw.budgetMin           ?? 0,
            budgetMax:           raw.budgetMax           ?? 200,
            durationMinHours:    raw.durationMinHours    ?? 3,
            durationMaxHours:    raw.durationMaxHours    ?? 8,
            interests:           raw.interests           ?? [],
            effortLevel:         raw.effortLevel         ?? 'Easy',
            weatherPreferences:  raw.weatherPreferences  ?? ['ANY'],
        };
    }
}