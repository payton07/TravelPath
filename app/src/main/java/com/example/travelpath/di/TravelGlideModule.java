package com.example.travelpath.di;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

/**
 * Configuration personnalisée de Glide pour TravelPath.
 * 
 * Objectifs :
 * 1. Augmenter la taille du cache disque pour le mode hors-ligne (100 Mo).
 * 2. Augmenter la taille du cache mémoire pour une navigation fluide (20 Mo).
 */
@GlideModule
public final class TravelGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Cache mémoire (LRU)
        int memoryCacheSizeBytes = 1024 * 1024 * 20; // 20 Mo
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));

        // Cache disque (interne à l'app)
        int diskCacheSizeBytes = 1024 * 1024 * 100; // 100 Mo
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, diskCacheSizeBytes));
    }

    // Désactive le scan des métadonnées du Manifest pour un démarrage plus rapide
    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
