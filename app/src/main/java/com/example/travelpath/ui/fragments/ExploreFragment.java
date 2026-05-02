package com.example.travelpath.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentExploreBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import com.google.android.material.chip.Chip;
import timber.log.Timber;
import android.view.inputmethod.InputMethodManager;
import com.example.travelpath.databinding.LayoutInputDialogBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Explore screen — all-in-one preferences page.
 *
 * Sections (top→bottom, all on one scrollable page):
 *   Header · City picker · Mood card · Budget slider ·
 *   Interests · Pace · Time of day · Must-see places · Sticky CTA
 */
public final class ExploreFragment extends Fragment {

    // ── Mood card ─────────────────────────────────────────────────────────────

    private record Mood(String title, String sub, int gradientStart, int gradientEnd, String effort) {}

    private static final int MOOD_COUNT = 3;
    private int moodIndex = 0;

    private final Mood[] MOODS = {
        new Mood("Slow\n& sunlit",    "Long lunches, golden hour. Pas pressé.",
                 R.color.color_blush, R.color.color_butter, SearchCriteria.EFFORT_EASY),
        new Mood("Steady\n& curious", "A bit of everything, kept moving.",
                 R.color.color_sky,   R.color.color_mint,   SearchCriteria.EFFORT_MODERATE),
        new Mood("Brisk\n& nightly",  "See it all, end at a wine bar.",
                 R.color.color_lilac, R.color.color_blush,  SearchCriteria.EFFORT_HIGH),
    };

    // ── Time of day ───────────────────────────────────────────────────────────

    private final boolean[] timeSelected = {true, true, false, false}; // Morning, Afternoon, Evening, Late
    private static final String[][] TIME_RANGES = {
        {"06:00", "12:00"},  // Morning
        {"12:00", "17:00"},  // Afternoon
        {"17:00", "22:00"},  // Evening
        {"22:00", "02:00"},  // Late
    };

    // ── Must-see places ───────────────────────────────────────────────────────

    private record Place(String name, String tag, int iconColor, boolean pinnedByDefault) {}

    private static final Place[] PLACES = {
        new Place("Mosteiro dos Jerónimos", "Architecture", R.color.color_sky,    true),
        new Place("Pastéis de Belém",        "Food",         R.color.color_blush,  true),
        new Place("Miradouro da Graça",      "View",         R.color.color_lilac,  false),
    };
    private final boolean[] placePinned = {false, false, false};
    private final ArrayList<String> customPlaces = new ArrayList<>();

    // ── Pace ──────────────────────────────────────────────────────────────────

    private int paceIndex = 1; // 0=Strolling, 1=Steady, 2=Brisk

    // ── Mood touch tracking ───────────────────────────────────────────────────

    private float moodTouchStartX;
    private static final float MOOD_SWIPE_THRESHOLD_DP = 60f;
    private static final int   ANIM_DURATION_MS        = 220;

    // ── Binding & ViewModel ───────────────────────────────────────────────────

    private FragmentExploreBinding binding;
    private MainViewModel          viewModel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupHeader();
        setupCitySection();
        setupMoodCard();
        setupBudgetSlider();
        setupInterestChips();
        setupPaceControl();
        setupTimeOfDay();
        setupMustSeePlaces();
        setupCTA();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void setupHeader() {
        // "Compose a day" — italic accent on "day" via SpannableStringBuilder
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder("Compose a day");
        int start = ssb.toString().indexOf("day");
        ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                start, start + 3, 0);
        ssb.setSpan(new android.text.style.ForegroundColorSpan(
                ContextCompat.getColor(requireContext(), R.color.color_accent)),
                start, start + 3, 0);
        binding.tvHeaderHeadline.setText(ssb);
        binding.tvAvatar.setOnClickListener(v ->
            ((MainActivity) requireActivity()).switchToProfileTab());
    }

    // ── City section ──────────────────────────────────────────────────────────

    private void setupCitySection() {
        // Restore saved city
        String city = viewModel.getDestinationCity().getValue();
        if (city != null && !city.isEmpty()) {
            binding.tvCityName.setText(extractCityName(city));
        }

        // "Change" tap → show autocomplete dialog (simple input dialog for demo)
        binding.layoutCityPill.setOnClickListener(v -> showCityDialog());

        // Recent chips
        String[] recents = {"Paris", "Tokyo", "Marrakech", "Reykjavík"};
        for (String c : recents) {
            Chip chip = new Chip(requireContext());
            chip.setText(c);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(),
                    R.color.chip_text_selector));
            chip.setChipStrokeColorResource(R.color.color_line);
            chip.setChipStrokeWidth(1f);
            chip.setChipCornerRadius(getResources().getDimension(R.dimen.chip_corner_radius));
            chip.setTextSize(11f);
            chip.setOnClickListener(v -> {
                viewModel.setDestination(c + ", France", null);
                binding.tvCityName.setText(c);
            });
            binding.chipGroupRecentCities.addView(chip);
        }
    }

    private void showCityDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        LayoutInputDialogBinding d = LayoutInputDialogBinding.inflate(getLayoutInflater());
        sheet.setContentView(d.getRoot());

        d.tvDialogTitle.setText(R.string.dialog_city_title);
        d.tvDialogSubtitle.setText(R.string.dialog_city_subtitle);
        d.tvDialogSubtitle.setVisibility(android.view.View.VISIBLE);
        d.etDialogInput.setHint(R.string.dialog_city_hint);

        d.btnDialogCancel.setOnClickListener(v -> sheet.dismiss());
        d.btnDialogConfirm.setOnClickListener(v -> {
            String city = d.etDialogInput.getText().toString().trim();
            if (!city.isEmpty()) {
                viewModel.setDestination(city, null);
                binding.tvCityName.setText(city);
                sheet.dismiss();
            }
        });

        sheet.setOnShowListener(dlg -> {
            d.etDialogInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(d.etDialogInput, InputMethodManager.SHOW_IMPLICIT);
        });

        sheet.show();
    }

    // ── Mood card ─────────────────────────────────────────────────────────────

    @SuppressWarnings("ClickableViewAccessibility")
    private void setupMoodCard() {
        applyMoodCard(moodIndex, false);

        binding.moodFrontCard.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    moodTouchStartX = event.getRawX();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
                case MotionEvent.ACTION_MOVE -> {
                    float dx = event.getRawX() - moodTouchStartX;
                    binding.moodFrontCard.setTranslationX(dx * 0.3f);
                    float tilt = dx / 30f;
                    binding.moodFrontCard.setRotation(Math.max(-10f, Math.min(10f, tilt)));
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    float dx = event.getRawX() - moodTouchStartX;
                    float thresholdPx = MOOD_SWIPE_THRESHOLD_DP *
                            requireContext().getResources().getDisplayMetrics().density;
                    if (Math.abs(dx) > thresholdPx) {
                        int next = dx > 0
                                ? (moodIndex + 1) % MOOD_COUNT
                                : (moodIndex - 1 + MOOD_COUNT) % MOOD_COUNT;
                        binding.moodFrontCard.animate()
                                .translationX(dx > 0 ? 80f : -80f)
                                .rotation(dx > 0 ? 8f : -8f)
                                .alpha(0f)
                                .setDuration(ANIM_DURATION_MS)
                                .withEndAction(() -> {
                                    moodIndex = next;
                                    applyMoodCard(moodIndex, true);
                                })
                                .start();
                    } else {
                        binding.moodFrontCard.animate()
                                .translationX(0f).rotation(0f).alpha(1f)
                                .setDuration(150)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return true;
        });
    }

    private void applyMoodCard(int index, boolean animate) {
        Mood mood = MOODS[index];

        // Gradient background
        int start = ContextCompat.getColor(requireContext(), mood.gradientStart());
        int end   = ContextCompat.getColor(requireContext(), mood.gradientEnd());
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        gd.setCornerRadius(20 * requireContext().getResources().getDisplayMetrics().density);
        binding.moodFrontCard.setBackground(gd);

        binding.tvMoodTitle.setText(mood.title());
        binding.tvMoodSub.setText(mood.sub());
        binding.tvMoodCounter.setText(String.format("%d of %d", index + 1, MOOD_COUNT));

        updateMoodDots(index);
        viewModel.setEffortLevel(mood.effort());

        if (animate) {
            binding.moodFrontCard.setTranslationX(0f);
            binding.moodFrontCard.setRotation(0f);
            binding.moodFrontCard.setAlpha(0f);
            binding.moodFrontCard.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void updateMoodDots(int active) {
        View[] dots = {binding.moodDot1, binding.moodDot2, binding.moodDot3};
        int inkColor  = ContextCompat.getColor(requireContext(), R.color.color_ink);
        int lineColor = ContextCompat.getColor(requireContext(), R.color.color_line);
        for (int i = 0; i < dots.length; i++) {
            android.view.ViewGroup.LayoutParams lp = dots[i].getLayoutParams();
            lp.width = (int) ((i == active ? 16 : 4) *
                    requireContext().getResources().getDisplayMetrics().density);
            dots[i].setLayoutParams(lp);
            dots[i].setBackgroundColor(i == active ? inkColor : lineColor);
        }
    }

    // ── Budget slider ─────────────────────────────────────────────────────────

    private void setupBudgetSlider() {
        binding.sliderBudgetRange.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            if (values.size() >= 2) {
                int min = values.get(0).intValue();
                int max = values.get(1).intValue();
                binding.tvBudgetValue.setText(String.format("€%d — €%d", min, max));
                viewModel.setBudgetRange(min, max);
            }
        });
    }

    // ── Interest chips ────────────────────────────────────────────────────────

    private void setupInterestChips() {
        Chip[] chips = {
            binding.chipCulture, binding.chipFood, binding.chipArchitecture,
            binding.chipNature,  binding.chipShopping, binding.chipNightlife,
        };
        for (Chip chip : chips) {
            if (chip.isChecked()) viewModel.toggleInterest(chip.getText().toString());

            chip.setOnCheckedChangeListener((btn, isChecked) -> {
                viewModel.toggleInterest(chip.getText().toString());
                boolean anyChecked = false;
                for (Chip c : chips) anyChecked |= c.isChecked();
                binding.tvInterestsError.setVisibility(anyChecked ? View.GONE : View.VISIBLE);
            });
        }
    }

    // ── Pace segmented control ────────────────────────────────────────────────

    private void setupPaceControl() {
        applyPaceSelection(paceIndex);

        binding.btnPaceStrolling.setOnClickListener(v -> selectPace(0));
        binding.btnPaceSteady.setOnClickListener(v   -> selectPace(1));
        binding.btnPaceBrisk.setOnClickListener(v    -> selectPace(2));
    }

    private void selectPace(int index) {
        paceIndex = index;
        applyPaceSelection(index);
        String[] efforts = {SearchCriteria.EFFORT_EASY, SearchCriteria.EFFORT_MODERATE, SearchCriteria.EFFORT_HIGH};
        viewModel.setEffortLevel(efforts[index]);
    }

    private void applyPaceSelection(int index) {
        com.google.android.material.button.MaterialButton[] btns = {
            binding.btnPaceStrolling, binding.btnPaceSteady, binding.btnPaceBrisk
        };
        int inkColor   = ContextCompat.getColor(requireContext(), R.color.color_ink);
        int whiteColor = ContextCompat.getColor(requireContext(), R.color.white);
        int transparent = android.graphics.Color.TRANSPARENT;

        for (int i = 0; i < btns.length; i++) {
            boolean active = i == index;
            btns[i].setBackgroundTintList(ColorStateList.valueOf(active ? inkColor : transparent));
            btns[i].setTextColor(active ? whiteColor : inkColor);
        }
    }

    // ── Time of day ───────────────────────────────────────────────────────────

    private void setupTimeOfDay() {
        LinearLayout[] cards = {binding.cardMorning, binding.cardAfternoon,
                                binding.cardEvening, binding.cardLate};
        for (int i = 0; i < cards.length; i++) {
            final int idx = i;
            cards[i].setOnClickListener(v -> toggleTimeSlot(idx));
        }
        updateTimeRange();
    }

    private void toggleTimeSlot(int index) {
        timeSelected[index] = !timeSelected[index];
        LinearLayout[] cards = {binding.cardMorning, binding.cardAfternoon,
                                binding.cardEvening, binding.cardLate};
        cards[index].setBackgroundResource(
                timeSelected[index] ? R.drawable.bg_time_card_selected
                                    : R.drawable.bg_time_card_unselected);
        // Update child text colors
        updateTimeCardTextColor(cards[index], timeSelected[index]);
        updateTimeRange();
    }

    private void updateTimeCardTextColor(LinearLayout card, boolean selected) {
        int primary  = ContextCompat.getColor(requireContext(), R.color.color_ink);
        int secondary = ContextCompat.getColor(requireContext(), R.color.color_ink_soft);
        // Children: View (marker), TextView (label), TextView (hours)
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (child instanceof TextView tv) {
                tv.setTextColor(i == 1 ? (selected ? primary : secondary) : secondary);
            }
        }
    }

    private void updateTimeRange() {
        // Find earliest start and latest end among selected slots
        String start = null, end = null;
        for (int i = 0; i < 4; i++) {
            if (timeSelected[i]) {
                if (start == null) start = TIME_RANGES[i][0];
                end = TIME_RANGES[i][1];
            }
        }
        if (start == null) {
            binding.tvTimeRange.setText("—");
            viewModel.setDurationRange(0, 0);
            return;
        }
        binding.tvTimeRange.setText(start + " — " + end);
        // Compute duration hours from time range
        int startH = Integer.parseInt(start.split(":")[0]);
        int endH   = Integer.parseInt(end.split(":")[0]);
        int hours  = endH > startH ? endH - startH : (24 - startH + endH);
        viewModel.setDurationRange(Math.max(1, hours / 2), hours);
    }

    // ── Must-see places ───────────────────────────────────────────────────────

    private void setupMustSeePlaces() {
        binding.layoutMustSee.removeAllViews();
        for (int i = 0; i < PLACES.length; i++) {
            binding.layoutMustSee.addView(buildPlaceRow(i));
        }

        // Seed initial pinned places into ViewModel
        for (int i = 0; i < PLACES.length; i++) {
            if (placePinned[i]) viewModel.addMandatoryPoi(PLACES[i].name());
        }

        binding.tvAddPlace.setOnClickListener(v -> showAddPlaceDialog());
    }

    private void showAddPlaceDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        LayoutInputDialogBinding d = LayoutInputDialogBinding.inflate(getLayoutInflater());
        sheet.setContentView(d.getRoot());

        d.tvDialogTitle.setText(R.string.dialog_place_title);
        d.tvDialogSubtitle.setText(R.string.dialog_place_subtitle);
        d.tvDialogSubtitle.setVisibility(android.view.View.VISIBLE);
        d.etDialogInput.setHint(R.string.dialog_place_hint);

        d.btnDialogCancel.setOnClickListener(v -> sheet.dismiss());
        d.btnDialogConfirm.setOnClickListener(v -> {
            String name = d.etDialogInput.getText().toString().trim();
            if (!name.isEmpty()) {
                addCustomMustSeePlace(name);
                sheet.dismiss();
            }
        });

        sheet.setOnShowListener(dlg -> {
            d.etDialogInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(d.etDialogInput, InputMethodManager.SHOW_IMPLICIT);
        });

        sheet.show();
    }

    private void addCustomMustSeePlace(String name) {
        if (name.isEmpty() || customPlaces.contains(name)) return;
        customPlaces.add(name);
        viewModel.addMandatoryPoi(name);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        float density = requireContext().getResources().getDisplayMetrics().density;
        int paddingH = (int) (14 * density);
        int paddingV = (int) (10 * density);
        row.setPadding(paddingH, paddingV, paddingH, paddingV);
        row.setBackgroundResource(R.drawable.bg_must_see_row);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) (8 * density);
        row.setLayoutParams(rowLp);

        // Icon square — accent color
        TextView icon = new TextView(requireContext());
        int iconSize = (int) (32 * density);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(ContextCompat.getColor(requireContext(), R.color.color_accent));
        iconBg.setCornerRadius(8 * density);
        icon.setBackground(iconBg);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setText(String.valueOf(name.charAt(0)).toUpperCase());
        icon.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        icon.setTextSize(14f);
        row.addView(icon);

        // Name + tag column
        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart((int) (10 * density));
        textCol.setLayoutParams(textLp);

        TextView nameView = new TextView(requireContext());
        nameView.setText(name);
        nameView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ink));
        nameView.setTextSize(14.5f);
        try { nameView.setTypeface(android.graphics.Typeface.create("fraunces", android.graphics.Typeface.NORMAL)); }
        catch (Exception ignored) {}
        textCol.addView(nameView);

        TextView tagView = new TextView(requireContext());
        tagView.setText("CUSTOM · pinned");
        tagView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ink_soft));
        tagView.setTextSize(10f);
        tagView.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = (int) (2 * density);
        tagView.setLayoutParams(tagLp);
        textCol.addView(tagView);
        row.addView(textCol);

        // Pin button — always selected; tap removes the row
        TextView pinBtn = new TextView(requireContext());
        int pinSize = (int) (22 * density);
        pinBtn.setLayoutParams(new LinearLayout.LayoutParams(pinSize, pinSize));
        pinBtn.setGravity(android.view.Gravity.CENTER);
        pinBtn.setBackgroundResource(R.drawable.bg_pin_selected);
        pinBtn.setText("✓");
        pinBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        pinBtn.setTextSize(10f);
        pinBtn.setClickable(true);
        pinBtn.setFocusable(true);
        pinBtn.setOnClickListener(v -> {
            customPlaces.remove(name);
            viewModel.removeMandatoryPoi(name);
            binding.layoutMustSee.removeView(row);
        });
        row.addView(pinBtn);

        binding.layoutMustSee.addView(row);
    }

    private View buildPlaceRow(int index) {
        Place place = PLACES[index];

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        int paddingH = (int) (14 * requireContext().getResources().getDisplayMetrics().density);
        int paddingV = (int) (10 * requireContext().getResources().getDisplayMetrics().density);
        row.setPadding(paddingH, paddingV, paddingH, paddingV);
        row.setBackgroundResource(placePinned[index]
                ? R.drawable.bg_must_see_row : R.drawable.bg_must_see_row_unselected);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int marginBottom = (int) (8 * requireContext().getResources().getDisplayMetrics().density);
        rowLp.bottomMargin = marginBottom;
        row.setLayoutParams(rowLp);

        // Colored icon square
        TextView icon = new TextView(requireContext());
        int iconSize = (int) (32 * requireContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        icon.setLayoutParams(iconLp);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(ContextCompat.getColor(requireContext(), place.iconColor()));
        iconBg.setCornerRadius(8 * requireContext().getResources().getDisplayMetrics().density);
        icon.setBackground(iconBg);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setText(String.valueOf(place.name().charAt(0)));
        icon.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ink));
        icon.setTextSize(14f);
        row.addView(icon);

        // Name + tag column
        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        int marginStart = (int) (10 * requireContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(marginStart);
        textCol.setLayoutParams(textLp);

        TextView nameView = new TextView(requireContext());
        nameView.setText(place.name());
        nameView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ink));
        nameView.setTextSize(14.5f);
        // Fraunces via typeface
        try { nameView.setTypeface(android.graphics.Typeface.create("fraunces", android.graphics.Typeface.NORMAL)); }
        catch (Exception ignored) {}
        textCol.addView(nameView);

        TextView tagView = new TextView(requireContext());
        tagView.setText(place.tag().toUpperCase() + " · pinned");
        tagView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ink_soft));
        tagView.setTextSize(10f);
        tagView.setLetterSpacing(0.06f);
        int marginTop2 = (int) (2 * requireContext().getResources().getDisplayMetrics().density);
        ((LinearLayout.LayoutParams) tagView.getLayoutParams() == null
                ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                : (LinearLayout.LayoutParams) tagView.getLayoutParams())
                .topMargin = marginTop2;
        textCol.addView(tagView);
        row.addView(textCol);

        // Pin toggle button
        TextView pinBtn = new TextView(requireContext());
        int pinSize = (int) (22 * requireContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(pinSize, pinSize);
        pinBtn.setLayoutParams(pinLp);
        pinBtn.setGravity(android.view.Gravity.CENTER);
        updatePinButton(pinBtn, placePinned[index]);
        pinBtn.setClickable(true);
        pinBtn.setFocusable(true);
        pinBtn.setOnClickListener(v -> {
            placePinned[index] = !placePinned[index];
            updatePinButton(pinBtn, placePinned[index]);
            row.setBackgroundResource(placePinned[index]
                    ? R.drawable.bg_must_see_row : R.drawable.bg_must_see_row_unselected);
            if (placePinned[index]) viewModel.addMandatoryPoi(PLACES[index].name());
            else                    viewModel.removeMandatoryPoi(PLACES[index].name());
        });
        row.addView(pinBtn);

        return row;
    }

    private void updatePinButton(TextView btn, boolean pinned) {
        btn.setBackgroundResource(pinned ? R.drawable.bg_pin_selected : R.drawable.bg_pin_unselected);
        btn.setText(pinned ? "✓" : "+");
        btn.setTextColor(ContextCompat.getColor(requireContext(),
                pinned ? R.color.white : R.color.color_ink_soft));
        btn.setTextSize(10f);
    }

    // ── CTA ───────────────────────────────────────────────────────────────────

    private void setupCTA() {
        binding.btnFindMyDay.setOnClickListener(v -> {
            String city = viewModel.getDestinationCity().getValue();
            if (city == null || city.isEmpty()) {
                ((MainActivity) requireActivity()).showMessage(
                        com.example.travelpath.ui.widget.MessageBanner.Type.ERROR,
                        getString(R.string.error_enter_destination));
                return;
            }
            SearchCriteria criteria = viewModel.buildCriteria();
            if (criteria == null) {
                String err = viewModel.getLastValidationError().getValue();
                boolean isInterestError = err != null && err.toLowerCase().contains("interest");
                if (isInterestError) {
                    binding.tvInterestsError.setVisibility(View.VISIBLE);
                    binding.scrollView.post(() ->
                            binding.scrollView.smoothScrollTo(0, binding.tvInterestsError.getTop()));
                } else {
                    ((MainActivity) requireActivity()).showMessage(
                            com.example.travelpath.ui.widget.MessageBanner.Type.ERROR,
                            err != null ? err : getString(R.string.error_select_interest));
                }
                return;
            }
            binding.tvInterestsError.setVisibility(View.GONE);
            Timber.d("Find my day — %s", city);
            ((MainActivity) requireActivity()).navigateTo(
                    RoutesFragment.newInstance(criteria), "routes");
        });
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getDestinationCity().observe(getViewLifecycleOwner(), city -> {
            if (city != null) binding.tvCityName.setText(extractCityName(city));
        });
        viewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            if (name != null && !name.isEmpty()) {
                binding.tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractCityName(String full) {
        if (full == null || full.isEmpty()) return "Lisbon";
        int comma = full.indexOf(',');
        return comma > 0 ? full.substring(0, comma).trim() : full;
    }
}
