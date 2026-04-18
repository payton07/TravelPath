package com.example.travelpath.ui.fragments;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.ItemRouteCardBinding;
import java.util.ArrayList;
import java.util.List;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {

    private List<Itinerary> itineraries = new ArrayList<>();
    private final OnRouteClickListener listener;

    public interface OnRouteClickListener {
        void onRouteClick(Itinerary itinerary);
        void onLikeClick(Itinerary itinerary);
    }

    public RouteAdapter(OnRouteClickListener listener) {
        this.listener = listener;
    }

    public void setItineraries(List<Itinerary> itineraries) {
        this.itineraries = itineraries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRouteCardBinding binding = ItemRouteCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RouteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        holder.bind(itineraries.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return itineraries.size();
    }

    static class RouteViewHolder extends RecyclerView.ViewHolder {
        private final ItemRouteCardBinding binding;

        public RouteViewHolder(ItemRouteCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Itinerary itinerary, OnRouteClickListener listener) {
            binding.tvRouteName.setText(itinerary.getName());
            binding.tvCost.setText(itinerary.getCost() + "€");
            binding.tvDuration.setText(itinerary.getDuration());
            binding.tvEffort.setText(itinerary.getEffort());
            binding.tvWeather.setText(itinerary.getWeather());

            // Image Thumbnail
            if (itinerary.getImageUrl() != null && !itinerary.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(itinerary.getImageUrl())
                        .placeholder(R.drawable.bg_travel_mode)
                        .into(binding.ivRouteThumbnail);
            }

            // Etat du bouton Like
            updateLikeIcon(itinerary.isSaved());

            binding.btnLike.setOnClickListener(v -> {
                // Animation
                Animation anim = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.heart_pop);
                binding.btnLike.startAnimation(anim);
                
                listener.onLikeClick(itinerary);
                updateLikeIcon(itinerary.isSaved());
            });

            binding.btnSelectRoute.setOnClickListener(v -> listener.onRouteClick(itinerary));
            binding.getRoot().setOnClickListener(v -> listener.onRouteClick(itinerary));
        }

        private void updateLikeIcon(boolean isLiked) {
            if (isLiked) {
                binding.btnLike.setIconResource(android.R.drawable.btn_star_big_on);
            } else {
                binding.btnLike.setIconResource(android.R.drawable.btn_star_big_off);
            }
        }
    }
}
