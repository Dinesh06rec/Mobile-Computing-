package com.zenithfoods.android

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun zenithColorScheme() = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF166534),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
)

@Composable
fun ZenithRoot(
    state: ZenithUiState,
    vm: ZenithViewModel,
    onGoogleSignIn: () -> Unit,
) {
    when {
        state.configError.isNotBlank() && !state.isSignedIn && !state.authLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Zenith Foods", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                Spacer(Modifier.height(12.dp))
                Text(state.configError, color = MaterialTheme.colorScheme.error)
            }
        }

        state.authLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color(0xFF16A34A))
                Spacer(Modifier.height(12.dp))
                Text("Checking your session...", color = Color(0xFF64748B))
            }
        }

        !state.isSignedIn -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Welcome to Zenith Foods", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                Spacer(Modifier.height(8.dp))
                Text("Please sign in with Google to continue", color = Color(0xFF64748B))
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onGoogleSignIn,
                    enabled = !state.signingIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (state.signingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(22.dp)
                                .width(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Sign in with Google", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    state.authError.ifBlank { " " },
                    color = Color(0xFFDC2626),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        else -> {
            ShopScreen(state = state, vm = vm)
        }
    }

    if (state.cartSheetOpen) {
        CartDialog(state = state, vm = vm, onDismiss = { vm.setCartSheet(false) })
    }
    if (state.favoritesSheetOpen) {
        FavoritesDialog(state = state, vm = vm, onDismiss = { vm.setFavoritesSheet(false) })
    }
}

@Composable
private fun ShopScreen(state: ZenithUiState, vm: ZenithViewModel) {
    val filtered = vm.filteredProducts()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ZenithFoods", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF166534))
                Text(
                    state.userDisplayName ?: state.userEmail.orEmpty(),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Cart: ${state.cart.values.sumOf { it.quantity }}", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { vm.logout() }) {
                    Text("Logout", fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::updateQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Search products...") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )
        val deliveryText = when {
            state.delivery.located ->
                "Distance ${state.delivery.distanceKm} km | Delivery Rs.${state.delivery.charge} (Rs.$DELIVERY_RATE_PER_KM/km)"
            state.delivery.error != null -> state.delivery.error!!
            else -> "Detecting your location..."
        }
        Text(
            deliveryText,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                .padding(10.dp),
            color = Color(0xFF166534),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.setFavoritesSheet(true) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Favorites (${state.favorites.size})")
            }
            Button(
                onClick = { vm.setCartSheet(true) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Open Cart")
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            for (cat in SHOP_CATEGORIES) {
                val products = filtered.filter { it.category == cat.id }
                if (products.isEmpty()) continue
                key(cat.id) {
                    val rowScroll = rememberScrollState()
                    Text(
                        "${cat.emoji} ${cat.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(Modifier.horizontalScroll(rowScroll)) {
                        for (p in products) {
                            ProductCard(p, state, vm)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProductCard(p: Product, state: ZenithUiState, vm: ZenithViewModel) {
    val inCart = state.cart[p.id]
    Card(
        modifier = Modifier
            .width(170.dp)
            .padding(end = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(10.dp)) {
            TextButton(
                onClick = { vm.toggleFavorite(p) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (state.favorites.contains(p.id)) "❤️" else "🤍", fontSize = 18.sp)
            }
            Text(p.emoji, fontSize = 36.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(p.unit, fontSize = 12.sp, color = Color(0xFF64748B))
            Text(p.name, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 3, lineHeight = 18.sp)
            Text("Rs.${p.price}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
            if (inCart != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { vm.removeFromCart(p.id) }) {
                        Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                    Text("${inCart.quantity}")
                    TextButton(onClick = { vm.addToCart(p) }) {
                        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }
            } else {
                Button(
                    onClick = { vm.addToCart(p) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCFCE7), contentColor = Color(0xFF166534)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("ADD", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CartDialog(state: ZenithUiState, vm: ZenithViewModel, onDismiss: () -> Unit) {
    val lines = state.cart.values.toList()
    val subtotal = lines.sumOf { it.price * it.quantity }
    val delivery = if (state.delivery.located) state.delivery.charge else 0
    val total = subtotal + delivery
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Your Cart", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (lines.isEmpty()) {
                        Text("Cart is empty")
                    } else {
                        for (item in lines) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.emoji, fontSize = 28.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name)
                                    Text(item.unit, color = Color(0xFF64748B), fontSize = 12.sp)
                                }
                                Text("Rs.${item.price * item.quantity}")
                            }
                        }
                    }
                }
                Text("Subtotal: Rs.$subtotal", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Delivery: Rs.$delivery", fontWeight = FontWeight.SemiBold)
                Text("Total: Rs.$total", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { vm.checkout() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Place Order", color = Color.White, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun FavoritesDialog(state: ZenithUiState, vm: ZenithViewModel, onDismiss: () -> Unit) {
    val favProducts = state.products.filter { state.favorites.contains(it.id) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Favorites", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (favProducts.isEmpty()) {
                        Text("No favorites yet")
                    } else {
                        for (item in favProducts) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.emoji, fontSize = 28.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name)
                                    Text("Rs.${item.price}")
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    TextButton(onClick = { vm.addToCart(item) }) {
                                        Text("ADD", fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                    }
                                    TextButton(onClick = { vm.toggleFavorite(item) }) {
                                        Text("REMOVE", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}
