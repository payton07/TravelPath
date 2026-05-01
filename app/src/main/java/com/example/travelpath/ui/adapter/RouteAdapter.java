package com.example.travelpath.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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

    public Itinerary getItemAt(int position) {
        return differ.getCurrentList().get(position);
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
            b.tvCost.setText(String.format("~%s€", it.getCost()));
            b.tvDuration.setText(it.getDuration());
            b.tvEffort.setText(it.getEffort());
            b.tvWeather.setText(formatWeather(it.getWeather()));

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
            b.tvCost.setText(String.format("~%s€", it.getCost()));
            b.tvDuration.setText(it.getDuration());
            b.tvEffort.setText(it.getEffort());
            b.tvWeather.setText(formatWeather(it.getWeather()));

            String type = it.getRouteType();
            if ("BALANCED".equalsIgnoreCase(type))      b.tvBadge.setText(R.string.badge_balanced);
            else if ("COMFORT".equalsIgnoreCase(type))  b.tvBadge.setText(R.string.badge_comfort);
            else                                         b.tvBadge.setText(R.string.badge_economy);

            applyTierStyle(type);

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

        private void applyTierStyle(@Nullable String routeType) {
            Context ctx = itemView.getContext();
            int dp2 = Math.round(ctx.getResources().getDisplayMetrics().density) * 2;

            if ("COMFORT".equalsIgnoreCase(routeType)) {
                // Carte indigo profond — premium
                b.cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.color_comfort_bg));
                b.cardRoot.setStrokeWidth(0);
                b.viewHeroTint.setBackgroundResource(R.drawable.bg_card_header_comfort);
                b.viewHeroTint.setAlpha(0.75f);
                b.tvRouteName.setTextColor(0xFFFFFFFF);
                b.btnLike.setIconTint(ColorStateList.valueOf(0xFFFFFFFF));
                b.btnLike.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
                b.btnLike.setStrokeColor(ColorStateList.valueOf(0x44FFFFFF));
                b.btnSelectRoute.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_butter)));
                b.btnSelectRoute.setTextColor(ContextCompat.getColor(ctx, R.color.color_ink));
                b.tvBadgePopular.setVisibility(View.GONE);

            } else if ("BALANCED".equalsIgnoreCase(routeType)) {
                // Carte sky — populaire
                b.cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.color_sky));
                b.cardRoot.setStrokeColor(ContextCompat.getColor(ctx, R.color.color_sky));
                b.cardRoot.setStrokeWidth(dp2);
                b.viewHeroTint.setBackgroundResource(R.drawable.bg_card_header_balanced);
                b.viewHeroTint.setAlpha(0.65f);
                b.tvRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.color_ink));
                b.btnLike.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_ink)));
                b.btnLike.setBackgroundTintList(ColorStateList.valueOf(0xCCFFFFFF));
                b.btnLike.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_line)));
                b.btnSelectRoute.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_ink)));
                b.btnSelectRoute.setTextColor(ContextCompat.getColor(ctx, R.color.color_surface));
                b.tvBadgePopular.setVisibility(View.VISIBLE);

            } else {
                // ECONOMY — carte butter chaude
                b.cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.color_butter));
                b.cardRoot.setStrokeWidth(0);
                b.viewHeroTint.setBackgroundResource(R.drawable.bg_card_header_economy);
                b.viewHeroTint.setAlpha(0.60f);
                b.tvRouteName.setTextColor(ContextCompat.getColor(ctx, R.color.color_ink));
                b.btnLike.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_ink)));
                b.btnLike.setBackgroundTintList(ColorStateList.valueOf(0xCCFFFFFF));
                b.btnLike.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_line)));
                b.btnSelectRoute.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color_ink)));
                b.btnSelectRoute.setTextColor(ContextCompat.getColor(ctx, R.color.color_surface));
                b.tvBadgePopular.setVisibility(View.GONE);
            }
        }

        private void refreshLikeIcon(boolean saved) {
            b.btnLike.setIconResource(saved
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public static String formatWeather(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        String lower = raw.toLowerCase().trim();
        if (lower.equals("any") || lower.contains("sun") && lower.contains("cloud") && lower.contains("rain")) {
            return "Toutes météos";
        }
        if (lower.contains("sun") && lower.contains("cloud")) return "Hors pluie";
        if (lower.equals("sun"))   return "Beau temps";
        if (lower.equals("cloud")) return "Couvert";
        if (lower.equals("rain"))  return "Pluie";
        if (lower.equals("varies")) return "Variable";
        return raw;
    }
}
