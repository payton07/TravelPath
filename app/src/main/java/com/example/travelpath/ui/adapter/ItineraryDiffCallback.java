package com.example.travelpath.ui.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import com.example.travelpath.data.entities.Itinerary;
import java.util.List;

/**
 * DiffUtil.Callback pour {@link RouteAdapter}.
 *
 * Remplace {@code notifyDataSetChanged()} par un diff calculé sur le thread IO
 * via {@code AsyncListDiffer} — seules les cellules réellement modifiées
 * sont redessinées, ce qui élimine le flash visuel et améliore les performances.
 */
public final class ItineraryDiffCallback extends DiffUtil.ItemCallback<Itinerary> {

    @Override
    public boolean areItemsTheSame(@NonNull Itinerary oldItem, @NonNull Itinerary newItem) {
        // Identité stable : l'ID Room (ou le nom si ID = 0 avant insertion)
        return oldItem.getId() != 0
                ? oldItem.getId() == newItem.getId()
                : oldItem.getName() != null && oldItem.getName().equals(newItem.getName());
    }

    @Override
    public boolean areContentsTheSame(@NonNull Itinerary oldItem, @NonNull Itinerary newItem) {
        // Contenu affiché dans la carte : on compare les champs visibles
        return oldItem.isSaved()   == newItem.isSaved()
            && oldItem.getCost()   == newItem.getCost()
            && eq(oldItem.getName(),     newItem.getName())
            && eq(oldItem.getDuration(), newItem.getDuration())
            && eq(oldItem.getEffort(),   newItem.getEffort())
            && eq(oldItem.getImageUrl(), newItem.getImageUrl());
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
