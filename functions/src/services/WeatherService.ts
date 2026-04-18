import axios, { AxiosInstance } from 'axios';
import { Env } from '../config/AppConfig';
import { Logger } from '../utils/Logger';

const OWM_API_URL = 'https://api.openweathermap.org/data/2.5/weather';

/**
 * Mappe les codes météo OpenWeatherMap vers nos constantes simplifiées.
 */
const CONDITION_MAP: Record<string, string> = {
    'Clear': 'SUN',
    'Clouds': 'CLOUD',
    'Rain': 'RAIN',
    'Drizzle': 'RAIN',
    'Thunderstorm': 'RAIN',
    'Snow': 'SNOW',
    'Mist': 'CLOUD',
    'Fog': 'CLOUD',
    'Haze': 'CLOUD',
};

/**
 * Service de récupération de la météo en temps réel via OpenWeatherMap.
 */
export class WeatherService {
    private readonly http: AxiosInstance;
    private readonly log: Logger;

    constructor(
        log: Logger = new Logger('WeatherService'),
        httpClient: AxiosInstance = axios.create({ timeout: 5000 })
    ) {
        this.http = httpClient;
        this.log = log;
    }

    /**
     * Retourne la condition météo actuelle d'une ville (SUN, RAIN, etc.)
     * @param city Nom de la ville
     */
    async getCurrentCondition(city: string): Promise<string> {
        if (!Env.OPENWEATHER_API_KEY) {
            this.log.warn('OPENWEATHER_API_KEY absente — fallback sur SUN.');
            return 'SUN';
        }

        try {
            const response = await this.http.get(OWM_API_URL, {
                params: {
                    q: city,
                    appid: Env.OPENWEATHER_API_KEY,
                    units: 'metric'
                }
            });

            const main = response.data.weather[0].main;
            const condition = CONDITION_MAP[main] ?? 'ANY';
            this.log.info(`Météo pour ${city} : ${main} -> ${condition}`);
            return condition;
        } catch (err) {
            this.log.error(`Échec appel OpenWeatherMap pour ${city}`, err);
            return 'ANY';
        }
    }
}
