package com.zenithfoods.android

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ZenithUiState(
    val authLoading: Boolean = true,
    val signingIn: Boolean = false,
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val userDisplayName: String? = null,
    val authError: String = "",
    val configError: String = "",
    val products: List<Product> = emptyList(),
    val query: String = "",
    val cart: Map<Int, CartLine> = emptyMap(),
    val favorites: Set<Int> = emptySet(),
    val delivery: DeliveryUi = DeliveryUi(),
    val cartSheetOpen: Boolean = false,
    val favoritesSheetOpen: Boolean = false,
)

class ZenithViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ZenithPreferences(application)
    private val deliverySocket = DeliverySocket(BuildConfig.API_BASE_URL.trimEnd('/'))
    private val api = ZenithApi(BuildConfig.API_BASE_URL.trimEnd('/')) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@ZenithApi null
        user.getIdToken(false).await().token
    }

    private val _state = MutableStateFlow(ZenithUiState())
    val state: StateFlow<ZenithUiState> = _state.asStateFlow()

    private val firebaseConfigured: Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank()

    private val productsById: Map<Int, Product>
        get() = _state.value.products.associateBy { it.id }

    init {
        if (!firebaseConfigured) {
            _state.update {
                it.copy(
                    authLoading = false,
                    configError = "Add ZENITH_FIREBASE_* and ZENITH_GOOGLE_WEB_CLIENT_ID to ZenithFoodsAndroid/local.properties.",
                )
            }
        } else {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user == null) {
                    deliverySocket.disconnect()
                    viewModelScope.launch { prefs.clearLocal() }
                    _state.update {
                        ZenithUiState(
                            authLoading = false,
                            products = it.products,
                            configError = it.configError,
                        )
                    }
                } else {
                    viewModelScope.launch {
                        onFirebaseUserReady(user.email, user.displayName)
                    }
                }
            }
        }
    }

    fun loadCatalogIfNeeded() {
        if (_state.value.products.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val list = CatalogLoader.load(getApplication())
                _state.update { it.copy(products = list) }
            } catch (e: Exception) {
                Log.e("Zenith", "catalog", e)
                _state.update { it.copy(configError = "Could not load catalog: ${e.message}") }
            }
        }
    }

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q) }
    }

    fun setCartSheet(open: Boolean) {
        _state.update { it.copy(cartSheetOpen = open) }
    }

    fun setFavoritesSheet(open: Boolean) {
        _state.update { it.copy(favoritesSheetOpen = open) }
    }

    fun beginGoogleSignIn() {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            _state.update {
                it.copy(authError = "Missing ZENITH_GOOGLE_WEB_CLIENT_ID in local.properties.")
            }
            return
        }
        _state.update { it.copy(signingIn = true, authError = "") }
    }

    fun onGoogleActivityResult(data: android.content.Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    _state.update { it.copy(authError = "Google did not return an ID token.", signingIn = false) }
                    return@launch
                }
                val cred = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(cred).await()
            } catch (e: ApiException) {
                val canceled = e.statusCode == 12501 // SIGN_IN_CANCELLED
                _state.update {
                    it.copy(
                        authError = if (canceled) "Sign-in cancelled." else (e.message ?: "Google sign-in failed"),
                        signingIn = false,
                    )
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.update { it.copy(authError = e.message ?: "Sign-in failed", signingIn = false) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .requestEmail()
                        .build()
                    GoogleSignIn.getClient(getApplication(), gso).signOut().await()
                }
            } catch (_: Exception) {
            }
            FirebaseAuth.getInstance().signOut()
        }
    }

    fun addToCart(product: Product) {
        _state.update { s ->
            val cur = s.cart[product.id]
            val nextQty = (cur?.quantity ?: 0) + 1
            s.copy(cart = s.cart + (product.id to product.toCartLine(nextQty)))
        }
        persistCart()
    }

    fun removeFromCart(productId: Int) {
        _state.update { s ->
            val cur = s.cart[productId] ?: return@update s
            val next = s.cart.toMutableMap()
            if (cur.quantity <= 1) next.remove(productId)
            else next[productId] = cur.copy(quantity = cur.quantity - 1)
            s.copy(cart = next)
        }
        persistCart()
    }

    fun toggleFavorite(product: Product) {
        val was = _state.value.favorites.contains(product.id)
        _state.update { s ->
            val m = s.favorites.toMutableSet()
            if (was) m.remove(product.id) else m.add(product.id)
            s.copy(favorites = m)
        }
        persistFavorites()
        viewModelScope.launch {
            if (FirebaseAuth.getInstance().currentUser != null) {
                api.toggleFavorite(product).onFailure { }
            }
        }
    }

    fun checkout() {
        val s = _state.value
        val lines = s.cart.values.toList()
        if (lines.isEmpty()) return
        val subtotal = lines.sumOf { it.price * it.quantity }
        val deliveryCharge = if (s.delivery.located) s.delivery.charge else 0
        val grandTotal = subtotal + deliveryCharge
        viewModelScope.launch {
            api.checkout(
                lines = lines,
                subtotal = subtotal,
                deliveryCharge = deliveryCharge,
                distanceKm = s.delivery.distanceKm,
                grandTotal = grandTotal,
            ).onFailure { }
            _state.update { it.copy(cart = emptyMap(), cartSheetOpen = false) }
            prefs.saveCart(emptyMap())
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            _state.update {
                it.copy(delivery = it.delivery.copy(error = "Location permission denied", located = false))
            }
            return
        }
        ensureDeliverySocketAndLocation()
    }

    /** Call when fine location is already granted (e.g. after sign-in). */
    fun ensureDeliverySocketAndLocation() {
        viewModelScope.launch { connectSocketAndSendLocation() }
    }

    private suspend fun onFirebaseUserReady(email: String?, displayName: String?) {
        if (_state.value.products.isEmpty()) {
            try {
                val list = CatalogLoader.load(getApplication())
                _state.update { it.copy(products = list) }
            } catch (e: Exception) {
                Log.e("Zenith", "catalog", e)
            }
        }
        _state.update {
            it.copy(
                authLoading = false,
                isSignedIn = true,
                userEmail = email,
                userDisplayName = displayName,
                authError = "",
                signingIn = false,
            )
        }
        val byId = productsById
        val localCart = prefs.loadCart(byId)
        val localFav = prefs.loadFavoriteIds()
        _state.update {
            it.copy(
                cart = localCart,
                favorites = localFav,
            )
        }
        api.postSession().onFailure { }
        api.getFavorites().onSuccess { remote ->
            _state.update { it.copy(favorites = remote) }
            prefs.saveFavoriteIds(remote)
        }
    }

    private suspend fun connectSocketAndSendLocation() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val token = try {
            user.getIdToken(false).await().token
        } catch (_: Exception) {
            null
        } ?: return

        deliverySocket.disconnect()
        deliverySocket.connect(token) { distance, charge ->
            _state.update {
                it.copy(
                    delivery = it.delivery.copy(
                        distanceKm = distance,
                        charge = charge,
                        located = true,
                        error = null,
                    ),
                )
            }
        }

        try {
            val fused = LocationServices.getFusedLocationProviderClient(getApplication())
            val cts = CancellationTokenSource()
            val loc = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            if (loc != null) {
                val lat = loc.latitude
                val lng = loc.longitude
                _state.update { it.copy(delivery = it.delivery.copy(lat = lat, lng = lng)) }
                deliverySocket.emitLocation(lat, lng)
            } else {
                _state.update { it.copy(delivery = it.delivery.copy(error = "Could not read location")) }
            }
        } catch (e: SecurityException) {
            _state.update { it.copy(delivery = it.delivery.copy(error = "Location permission required")) }
        } catch (e: Exception) {
            _state.update { it.copy(delivery = it.delivery.copy(error = e.message ?: "Location error")) }
        }
    }

    private fun persistCart() {
        val cart = _state.value.cart
        viewModelScope.launch { prefs.saveCart(cart) }
    }

    private fun persistFavorites() {
        val fav = _state.value.favorites
        viewModelScope.launch { prefs.saveFavoriteIds(fav) }
    }

    fun filteredProducts(): List<Product> {
        val q = _state.value.query.trim().lowercase()
        val all = _state.value.products
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
        }
    }
}
