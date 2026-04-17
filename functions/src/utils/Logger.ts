/**
 * Logger injectable minimal — adapté Firebase Functions.
 *
 * En production (NODE_ENV=production), seuls les niveaux warn et error
 * sont émis pour éviter de polluer les logs Cloud avec du bruit.
 * En développement, tous les niveaux s'affichent.
 *
 * Usage :
 *   const logger = new Logger('MonService');
 *   logger.info('message');   // affiché en dev seulement
 *   logger.warn('attention'); // toujours affiché
 *   logger.error('erreur');   // toujours affiché
 */
export class Logger {
    private readonly prefix:   string;
    private readonly isVerbose: boolean;

    constructor(context: string) {
        this.prefix    = `[${context}]`;
        this.isVerbose = process.env.NODE_ENV !== 'production';
    }

    info(message: string, ...args: unknown[]): void {
        if (this.isVerbose) {
            console.log(`${this.prefix} ℹ️  ${message}`, ...args);
        }
    }

    warn(message: string, ...args: unknown[]): void {
        console.warn(`${this.prefix} ⚠️  ${message}`, ...args);
    }

    error(message: string, ...args: unknown[]): void {
        console.error(`${this.prefix} ❌ ${message}`, ...args);
    }
}
