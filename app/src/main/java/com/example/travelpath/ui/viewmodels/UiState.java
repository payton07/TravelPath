package com.example.travelpath.ui.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Modélise l'état de l'UI de façon uniforme pour tous les ViewModels.
 *
 * Remplace les trois LiveData séparés ({@code isLoading}, {@code errorMessage},
 * {@code data}) par un seul flux typé. Les fragments font un simple
 * {@code if (state instanceof UiState.Success)} au lieu de coordonner
 * des booléens interdépendants.
 *
 * Utilisation dans un ViewModel :
 * <pre>
 *   MutableLiveData<UiState<List<Itinerary>>> uiState = new MutableLiveData<>();
 *   uiState.setValue(UiState.loading());
 *   // ... appel async ...
 *   uiState.postValue(UiState.success(result));
 * </pre>
 *
 * Utilisation dans un Fragment :
 * <pre>
 *   viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
 *       if      (state instanceof UiState.Loading) showSpinner();
 *       else if (state instanceof UiState.Success) showData(((UiState.Success<T>) state).getData());
 *       else if (state instanceof UiState.Empty)   showEmptyView();
 *       else if (state instanceof UiState.Error)   showError(((UiState.Error) state).getMessage());
 *   });
 * </pre>
 *
 * @param <T> Type de la donnée portée par l'état Success.
 */
public abstract class UiState<T> {

    private UiState() {}

    // ── Factories ─────────────────────────────────────────────────────────────

    public static <T> UiState<T> loading()                    { return new Loading<>(); }
    public static <T> UiState<T> success(@NonNull T data)     { return new Success<>(data); }
    public static <T> UiState<T> empty()                      { return new Empty<>(); }
    public static <T> UiState<T> error(@NonNull String msg)   { return new Error<>(msg); }

    // ── Sous-types ────────────────────────────────────────────────────────────

    /** Opération en cours — afficher un indicateur de chargement. */
    public static final class Loading<T> extends UiState<T> {}

    /** Opération réussie avec données disponibles. */
    public static final class Success<T> extends UiState<T> {
        private final T data;
        private Success(@NonNull T data) { this.data = data; }

        @NonNull public T getData() { return data; }
    }

    /** Opération réussie mais sans résultat (liste vide, etc.). */
    public static final class Empty<T> extends UiState<T> {}

    /** Opération échouée avec message d'erreur. */
    public static final class Error<T> extends UiState<T> {
        private final String message;
        private Error(@NonNull String message) { this.message = message; }

        @NonNull public String getMessage() { return message; }
    }
}
