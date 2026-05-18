package com.example.travelpath.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import com.example.travelpath.databinding.FragmentOnboardingBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public final class OnboardingFragment extends Fragment {

    private static final int CARD_COUNT = 5;
    private static final float SWIPE_THRESHOLD_DP = 100f;
    private static final float ROTATION_MAX_DEG   = 15f;

    private static final int[] QUESTION_RES = {
        R.string.onboard_q1, R.string.onboard_q2, R.string.onboard_q3,
        R.string.onboard_q4, R.string.onboard_q5
    };
    private static final int[] HINT_RES = {
        R.string.onboard_q1_hint, R.string.onboard_q2_hint, R.string.onboard_q3_hint,
        R.string.onboard_q4_hint, R.string.onboard_q5_hint
    };
    private static final String[] ICONS = { "🌅", "🐢", "🎨", "💰", "👥" };
    private static final int[] CARD_COLORS = {
        R.color.color_butter, R.color.color_sky, R.color.color_blush,
        R.color.color_mint,   R.color.color_lilac
    };

    private FragmentOnboardingBinding binding;
    private final boolean[] decisions    = new boolean[CARD_COUNT];
    private int   currentCardIndex       = 0;
    private float swipeStartX            = 0f;
    private float swipeThresholdPx       = 0f;
    private boolean isAnimating          = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        float density    = requireContext().getResources().getDisplayMetrics().density;
        swipeThresholdPx = SWIPE_THRESHOLD_DP * density;

        setupDots();
        loadCard(0);
        setupTouchListener();

        binding.tvSkip.setOnClickListener(v -> finishOnboarding());
        binding.btnCta.setOnClickListener(v -> finishOnboarding());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ── Dots ──────────────────────────────────────────────────────────────────

    private void setupDots() {
        binding.dotsContainer.removeAllViews();
        float density = requireContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < CARD_COUNT; i++) {
            View dot = new View(requireContext());
            int sz = (int) (8 * density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
            dot.setLayoutParams(lp);
            dot.setBackground(buildDotDrawable(R.color.color_line));
            binding.dotsContainer.addView(dot);
        }
        updateDots();
    }

    private void updateDots() {
        float density = requireContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < binding.dotsContainer.getChildCount(); i++) {
            View dot = binding.dotsContainer.getChildAt(i);
            boolean active = i == currentCardIndex;
            dot.setBackground(buildDotDrawable(active ? R.color.color_accent : R.color.color_line));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.width = (int) ((active ? 20 : 8) * density);
            dot.setLayoutParams(lp);
        }
    }

    private GradientDrawable buildDotDrawable(int colorRes) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ContextCompat.getColor(requireContext(), colorRes));
        return d;
    }

    // ── Card ──────────────────────────────────────────────────────────────────

    private void loadCard(int index) {
        binding.tvQuestion.setText(QUESTION_RES[index]);
        binding.tvHint.setText(HINT_RES[index]);
        binding.tvIcon.setText(ICONS[index]);
        int color = ContextCompat.getColor(requireContext(), CARD_COLORS[index]);
        binding.tvIcon.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    // ── Touch / Swipe ─────────────────────────────────────────────────────────

    @SuppressWarnings("ClickableViewAccessibility")
    private void setupTouchListener() {
        binding.cardOnboarding.setOnTouchListener((v, event) -> {
            if (isAnimating) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    swipeStartX = event.getRawX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - swipeStartX;
                    binding.cardOnboarding.setTranslationX(dx);
                    binding.cardOnboarding.setRotation(dx * ROTATION_MAX_DEG / swipeThresholdPx);
                    float ratio = Math.min(1f, Math.abs(dx) / swipeThresholdPx);
                    if (dx > 0) {
                        binding.tvOverlayYes.setAlpha(ratio);
                        binding.tvOverlayNo.setAlpha(0f);
                    } else {
                        binding.tvOverlayNo.setAlpha(ratio);
                        binding.tvOverlayYes.setAlpha(0f);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float totalDx = event.getRawX() - swipeStartX;
                    if (Math.abs(totalDx) >= swipeThresholdPx) {
                        commitSwipe(totalDx > 0);
                    } else {
                        springBack();
                    }
                    return true;
            }
            return false;
        });
    }

    private void commitSwipe(boolean isYes) {
        decisions[currentCardIndex] = isYes;
        isAnimating = true;

        float screenWidth = requireView().getWidth();
        float targetX   = isYes ?  screenWidth * 1.5f : -screenWidth * 1.5f;
        float targetRot = isYes ? 30f : -30f;

        binding.cardOnboarding.animate()
                .translationX(targetX)
                .rotation(targetRot)
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    currentCardIndex++;
                    if (currentCardIndex < CARD_COUNT) {
                        loadCard(currentCardIndex);
                        updateDots();
                        revealNextCard();
                    } else {
                        showSuccessState();
                    }
                    isAnimating = false;
                })
                .start();
    }

    private void revealNextCard() {
        binding.cardOnboarding.setTranslationX(150f);
        binding.cardOnboarding.setRotation(0f);
        binding.cardOnboarding.setAlpha(0f);
        binding.tvOverlayYes.setAlpha(0f);
        binding.tvOverlayNo.setAlpha(0f);
        binding.cardOnboarding.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(220)
                .start();
    }

    private void springBack() {
        binding.cardOnboarding.animate()
                .translationX(0f)
                .rotation(0f)
                .setDuration(200)
                .start();
        binding.tvOverlayYes.animate().alpha(0f).setDuration(150).start();
        binding.tvOverlayNo.animate().alpha(0f).setDuration(150).start();
    }

    // ── Success state ─────────────────────────────────────────────────────────

    private void showSuccessState() {
        binding.contentOnboarding.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    if (binding == null) return;
                    binding.contentOnboarding.setVisibility(View.GONE);
                    binding.successOverlay.setVisibility(View.VISIBLE);
                    binding.successOverlay.setAlpha(0f);
                    binding.successOverlay.animate().alpha(1f).setDuration(300).start();
                })
                .start();
    }

    // ── Completion ────────────────────────────────────────────────────────────

    private void finishOnboarding() {
        if (getActivity() == null) return;
        new ViewModelProvider(requireActivity())
                .get(MainViewModel.class)
                .applySwipeDecisions(decisions);

        UserPreferencesManager.getInstance(requireContext())
                .setOnboardingComplete()
                .subscribeOn(Schedulers.io())
                .subscribe(ignored -> {}, err ->
                    Timber.w("onboarding flag persist error: %s", err.getMessage()));

        ((MainActivity) requireActivity()).onOnboardingComplete();
    }
}
