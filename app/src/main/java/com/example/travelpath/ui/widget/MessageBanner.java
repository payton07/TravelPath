package com.example.travelpath.ui.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.example.travelpath.R;
import com.example.travelpath.databinding.ActivityMainBinding;

/**
 * Manages a persistent dark banner anchored above the bottom nav.
 * Replaces all Toast-based feedback with a styled slide-up card.
 *
 * Usage (from a Fragment):
 *   ((MainActivity) requireActivity()).showMessage(MessageBanner.Type.SUCCESS, "Route saved");
 */
public final class MessageBanner {

    public enum Type { ERROR, SUCCESS, INFO }

    private static final int ANIM_IN_MS       = 320;
    private static final int ANIM_OUT_MS      = 200;
    private static final int DELAY_ERROR_MS   = 4000;
    private static final int DELAY_SUCCESS_MS = 2500;
    private static final int DELAY_INFO_MS    = 3000;

    private final View     banner;
    private final TextView bannerIcon;
    private final TextView tvLabel;
    private final TextView tvMessage;
    private final TextView btnDismiss;
    private final Context  context;
    private final Handler  handler = new Handler(Looper.getMainLooper());
    private       Runnable pendingDismiss;

    private MessageBanner(ActivityMainBinding binding) {
        this.context    = binding.getRoot().getContext();
        this.banner     = binding.messageBanner;
        this.bannerIcon = binding.bannerIcon;
        this.tvLabel    = binding.tvBannerLabel;
        this.tvMessage  = binding.tvBannerMessage;
        this.btnDismiss = binding.btnBannerDismiss;

        btnDismiss.setOnClickListener(v -> dismiss());
        banner.setTranslationY(300f);
    }

    public static MessageBanner attach(ActivityMainBinding binding) {
        return new MessageBanner(binding);
    }

    public void show(Type type, String message) {
        cancelPending();

        applyType(type);
        tvMessage.setText(message);

        banner.setVisibility(View.VISIBLE);
        banner.animate()
                .translationY(0f)
                .setDuration(ANIM_IN_MS)
                .setInterpolator(new OvershootInterpolator(0.6f))
                .start();

        pendingDismiss = this::dismiss;
        handler.postDelayed(pendingDismiss, resolveDelay(type));
    }

    public void dismiss() {
        cancelPending();
        banner.animate()
                .translationY(banner.getHeight() + 200f)
                .alpha(0f)
                .setDuration(ANIM_OUT_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    banner.setVisibility(View.GONE);
                    banner.setTranslationY(300f);
                    banner.setAlpha(1f);
                })
                .start();
    }

    private void applyType(Type type) {
        int colorRes  = resolveColor(type);
        int color     = ContextCompat.getColor(context, colorRes);

        // Icon circle background
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        bannerIcon.setBackground(circle);

        // Icon symbol + label
        switch (type) {
            case ERROR:
                bannerIcon.setText("✕");
                tvLabel.setText("Error");
                break;
            case SUCCESS:
                bannerIcon.setText("✓");
                tvLabel.setText("Success");
                break;
            case INFO:
                bannerIcon.setText("i");
                tvLabel.setText("Info");
                break;
        }
        tvLabel.setTextColor(color);
    }

    private int resolveColor(Type type) {
        switch (type) {
            case SUCCESS: return R.color.status_success;
            case INFO:    return R.color.color_ink_soft;
            default:      return R.color.color_accent;
        }
    }

    private int resolveDelay(Type type) {
        switch (type) {
            case SUCCESS: return DELAY_SUCCESS_MS;
            case INFO:    return DELAY_INFO_MS;
            default:      return DELAY_ERROR_MS;
        }
    }

    private void cancelPending() {
        if (pendingDismiss != null) {
            handler.removeCallbacks(pendingDismiss);
            pendingDismiss = null;
        }
    }
}
