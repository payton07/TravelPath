package com.example.travelpath.ui.fragments;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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

            binding.btnSelectRoute.setOnClickListener(v -> listener.onRouteClick(itinerary));
            binding.getRoot().setOnClickListener(v -> listener.onRouteClick(itinerary));
        }
    }
}
