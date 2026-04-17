import { Env } from '../config/AppConfig';

/**
 * Logger injectable adapté Firebase Functions.
 *
 * - En production  : seuls warn et error sont émis (pas de bruit Cloud Logging)
 * - En développement : tous les niveaux s'affichent
 *
 * @example
 *   const log = new Logger('MyService');
 *   log.info('Démarrage...');
 *   log.warn('Clé manquante');
 *   log.error('Erreur critique', err);
 */
export class Logger {
    private readonly prefix:     string;
    private readonly isVerbose:  boolean;

    constructor(context: string) {
        this.prefix    = `[${context}]`;
        this.isVerbose = Env.NODE_ENV !== 'production';
    }

    info(message: string, ...args: unknown[]): void {
        if (this.isVerbose) console.log(`${this.prefix} ℹ️  ${message}`, ...args);
    }

    warn(message: string, ...args: unknown[]): void {
        console.warn(`${this.prefix} ⚠️  ${message}`, ...args);
    }

    error(message: string, ...args: unknown[]): void {
        console.error(`${this.prefix} ❌ ${message}`, ...args);
    }
}