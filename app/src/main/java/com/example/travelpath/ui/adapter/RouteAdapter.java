package com.example.travelpath.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.ItemRouteCardBinding;
import com.example.travelpath.databinding.ItemRouteCardCarouselBinding;
import java.util.List;

/**
 * Adaptateur polyvalent pour les itinéraires.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li>{@link #VIEW_TYPE_COMPACT} — liste verticale (SavedFragment)</li>
 *   <li>{@link #VIEW_TYPE_CAROUSEL} — carrousel plein écran (RoutesFragment)</li>
 * </ul>
 *
 * Chaque mode utilise son propre layout XML ({@code item_route_card} /
 * {@code item_route_card_carousel}) — on ne manipule plus les LayoutParams
 * à l'exécution, ce qui était fragile et non maintenable.
 *
 * <h2>Diff</h2>
 * Utilise {@link AsyncListDiffer} avec {@link ItineraryDiffCallback} :
 * uniquement les cellules modifiées sont redessinées (pas de flash global).
 */
public final class RouteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_COMPACT  = 0;
    public static final int VIEW_TYPE_CAROUSEL = 1;

    // ── Callback ─────────────────────────────────────────────────────────────

    public interface OnRouteActionListener {
        void onRouteClick(Itinerary itinerary);
        void onLikeClick(Itinerary itinerary);
    }

    // ── État ─────────────────────────────────────────────────────────────────

    private final int                   viewType;
    private final OnRouteActionListener listener;
    private final AsyncListDiffer<Itinerary> differ =
            new AsyncListDiffer<>(this, new ItineraryDiffCallback());

    public RouteAdapter(int viewType, @NonNull OnRouteActionListener listener) {
        this.viewType = viewType;
        this.listener = listener;
    }

    /** Met à jour la liste via un diff asynchrone — pas de flash, pas de perte de position. */
    public void submitList(@NonNull List<Itinerary> itineraries) {
        differ.submitList(itineraries);
    }

    /** 
     * Pré-charge les images dans le cache disque de Glide. 
     * @param context Contexte requis pour Glide
     * @param itineraries Liste à pré-charger
     */
    private void preloadImages(@NonNull android.content.Context context, @NonNull List<Itinerary> itineraries) {
        for (Itinerary it : itineraries) {
            if (it.getImageUrl() != null && !it.getImageUrl().isEmpty()) {
                Glide.with(context)
                     .load(it.getImageUrl())
                     .preload();
            }
        }
    }

    // =========================================================================
    // RecyclerView.Adapter
    // =========================================================================

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        // Déclencher le pré-chargement global une seule fois au premier affichage
        preloadImages(parent.getContext(), differ.getCurrentList());
        
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == VIEW_TYPE_CAROUSEL) {
            return new CarouselViewHolder(
                ItemRouteCardCarouselBinding.inflate(inflater, parent, false));
        }
        return new CompactViewHolder(
            ItemRouteCardBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Itinerary item = differ.getCurrentList().get(position);
        if (holder instanceof CarouselViewHolder) {
            ((CarouselViewHolder) holder).bind(item, listener);
        } else {
            ((CompactViewHolder) holder).bind(item, listener);
        }
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    // =========================================================================
    // ViewHolders
    // =========================================================================

    static final class CompactViewHolder extends RecyclerView.ViewHolder {

        private final ItemRouteCardBinding b;

        CompactViewHolder(@NonNull ItemRouteCardBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(@NonNull Itinerary it, @NonNull OnRouteActionListener listener) {
            b.tvRouteName.setText(it.getName());
            b.tvCost.setText(String.format("%s€", it.getCost()));
            b.tvDuration.setText(it.getDuration());
            b.tvEffort.setText(it.getEffort());
            b.tvWeather.setText(it.getWeather());

            loadThumbnail(it);
            refreshLikeIcon(it.isSaved());

            b.btnLike.setOnClickListener(v -> {
                b.btnLike.startAnimation(
                    AnimationUtils.loadAnimation(v.getContext(), R.anim.heart_pop));
                listener.onLikeClick(it);
            });

            b.btnSelectRoute.setOnClickListener(v -> listener.onRouteClick(it));
            b.getRoot().setOnClickListener(v -> listener.onRouteClick(it));
        }

        private void loadThumbnail(@NonNull Itinerary it) {
            if (it.getImageUrl() != null && !it.getImageUrl().isEmpty()) {
                Glide.with(itemView)
                     .load(it.getImageUrl())
                     .placeholder(R.drawable.bg_travel_mode)
                     .into(b.ivRouteThumbnail);
            }
        }

        private void refreshLikeIcon(boolean saved) {
            b.btnLike.setIconResource(saved
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        }
    }

    static final class CarouselViewHolder extends RecyclerView.ViewHolder {

        private final ItemRouteCardCarouselBinding b;

        CarouselViewHolder(@NonNull ItemRouteCardCarouselBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(@NonNull Itinerary it, @NonNull OnRouteActionListener listener) {
            b.tvRouteName.setText(it.getName());
            b.tvCost.setText(String.format("%s€", it.getCost()));
            b.tvDuration.setText(it.getDuration());
            b.tvEffort.setText(it.getEffort());
            b.tvWeather.setText(it.getWeather());

            if (it.getImageUrl() != null && !it.getImageUrl().isEmpty()) {
                Glide.with(itemView)
                     .load(it.getImageUrl())
                     .placeholder(R.drawable.bg_travel_mode)
                     .into(b.ivRouteThumbnail);
            }

            refreshLikeIcon(it.isSaved());

            b.btnLike.setOnClickListener(v -> {
                b.btnLike.startAnimation(
                    AnimationUtils.loadAnimation(v.getContext(), R.anim.heart_pop));
                listener.onLikeClick(it);
            });

            b.btnSelectRoute.setOnClickListener(v -> listener.onRouteClick(it));
            b.getRoot().setOnClickListener(v -> listener.onRouteClick(it));
        }

        private void refreshLikeIcon(boolean saved) {
            b.btnLike.setIconResource(saved
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        }
    }
}
