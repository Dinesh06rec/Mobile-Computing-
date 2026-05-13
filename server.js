/* ============================================================
   ZENITH FOODS — Node.js Backend
   Express + Socket.IO + MongoDB
   Live location tracking & delivery charge calculation
   ============================================================ */

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const { MongoClient } = require('mongodb');
const cors = require('cors');
const { initFirebaseAdmin, isFirebaseReady, verifyIdToken } = require('./firebaseAdmin');

// ═══════════════════════════════════════════════════════════════
// CONFIGURATION
// ═══════════════════════════════════════════════════════════════

const PORT = 5000;
const MONGO_URI = 'mongodb://localhost:27017';
const DB_NAME = 'zenith_foods';

// Shop coordinates (Zenith Foods store location)
const SHOP_LOCATION = {
    lat: 12.835801014358045,
    lng: 79.70630872618754
};

// Delivery pricing
const DELIVERY_RATE = 4; // Rs. per km


// ═══════════════════════════════════════════════════════════════
// EXPRESS + SOCKET.IO SETUP
// ═══════════════════════════════════════════════════════════════

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: '*', methods: ['GET', 'POST'] }
});

// Middleware
app.use(cors());
app.use(express.json());


// ═══════════════════════════════════════════════════════════════
// MONGODB CONNECTION
// ═══════════════════════════════════════════════════════════════

let db = null;
let mongoAvailable = false;

async function connectDB() {
    try {
        const client = new MongoClient(MONGO_URI, {
            serverSelectionTimeoutMS: 3000,
        });
        await client.connect();
        await client.db('admin').command({ ping: 1 });
        db = client.db(DB_NAME);
        mongoAvailable = true;
        await db.collection('favorites').createIndex({ user_id: 1, product_id: 1 }, { unique: true });
        await db.collection('orders').createIndex({ user_id: 1, created_at: -1 });
        await db.collection('users').createIndex({ user_id: 1 }, { unique: true });
        console.log('[OK] Connected to MongoDB');
    } catch (err) {
        console.log(`[WARN] MongoDB not available: ${err.message}`);
        console.log('       App will run with localStorage only');
    }
}

async function requireAuth(req, res, next) {
    if (!isFirebaseReady()) {
        return res.status(503).json({ success: false, message: 'Auth service not configured' });
    }
    const authHeader = req.headers.authorization || '';
    const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;
    if (!token) {
        return res.status(401).json({ success: false, message: 'Missing authorization token' });
    }
    try {
        const decoded = await verifyIdToken(token);
        req.user = {
            uid: decoded.uid,
            email: decoded.email || '',
            name: decoded.name || '',
            picture: decoded.picture || '',
        };
        next();
    } catch (err) {
        return res.status(401).json({ success: false, message: 'Invalid or expired token' });
    }
}


// ═══════════════════════════════════════════════════════════════
// HAVERSINE DISTANCE CALCULATION
// ═══════════════════════════════════════════════════════════════

function toRad(deg) {
    return deg * (Math.PI / 180);
}

/**
 * Calculate the great-circle distance between two points
 * using the Haversine formula.
 * @returns {number} Distance in kilometers
 */
function calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371; // Earth's radius in km
    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
        Math.sin(dLon / 2) ** 2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

/**
 * Get delivery info for a given customer location.
 */
function getDeliveryInfo(lat, lng) {
    const distance = calculateDistance(SHOP_LOCATION.lat, SHOP_LOCATION.lng, lat, lng);
    const roundedDistance = Math.round(distance * 100) / 100;
    const deliveryCharge = Math.ceil(distance * DELIVERY_RATE);
    return {
        distance: roundedDistance,
        deliveryCharge,
        shopLocation: SHOP_LOCATION,
        deliveryRate: DELIVERY_RATE,
    };
}


// ═══════════════════════════════════════════════════════════════
// PAGE ROUTES
// ═══════════════════════════════════════════════════════════════

app.get('/', (req, res) => {
    res.json({ success: true, message: 'Zenith Foods API server is running' });
});

// ═══════════════════════════════════════════════════════════════
// AUTH API
// ═══════════════════════════════════════════════════════════════

app.post('/api/auth/session', requireAuth, async (req, res) => {
    if (!mongoAvailable) {
        return res.status(503).json({ success: false, message: 'Database not available' });
    }
    try {
        const userDoc = {
            user_id: req.user.uid,
            email: req.user.email,
            name: req.user.name,
            picture: req.user.picture,
            last_login_at: new Date(),
        };
        await db.collection('users').updateOne(
            { user_id: req.user.uid },
            { $set: userDoc, $setOnInsert: { created_at: new Date() } },
            { upsert: true }
        );
        res.json({ success: true, user: userDoc });
    } catch (err) {
        console.error('Auth session error:', err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});

app.get('/api/auth/me', requireAuth, (req, res) => {
    res.json({ success: true, user: req.user });
});


// ═══════════════════════════════════════════════════════════════
// CART / CHECKOUT API
// ═══════════════════════════════════════════════════════════════

app.post('/api/cart/checkout', requireAuth, async (req, res) => {
    if (!mongoAvailable) {
        return res.status(503).json({ success: false, message: 'Database not available' });
    }

    try {
        const { items, subtotal, deliveryCharge, distance, grandTotal } = req.body;

        if (!items || items.length === 0) {
            return res.status(400).json({ success: false, message: 'No items in cart' });
        }

        // Create order document
        const order = {
            user_id: req.user.uid,
            user_email: req.user.email,
            user_name: req.user.name,
            subtotal: subtotal || 0,
            delivery_charge: deliveryCharge || 0,
            distance_km: distance || 0,
            grand_total: grandTotal || 0,
            item_count: items.length,
            status: 'confirmed',
            created_at: new Date(),
        };

        const result = await db.collection('orders').insertOne(order);

        // Save order items in bulk
        const orderItems = items.map(item => ({
            order_id: result.insertedId,
            user_id: req.user.uid,
            product_id: item.productId,
            product_name: item.productName,
            price: item.price,
            quantity: item.quantity,
            total: item.total,
        }));
        await db.collection('order_items').insertMany(orderItems);

        res.json({
            success: true,
            message: 'Order placed successfully!',
            orderId: result.insertedId.toString(),
        });
    } catch (err) {
        console.error('Checkout error:', err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});


// ═══════════════════════════════════════════════════════════════
// FAVORITES API
// ═══════════════════════════════════════════════════════════════

app.get('/api/favorites', requireAuth, async (req, res) => {
    if (!mongoAvailable) {
        return res.json({ success: true, favorites: [] });
    }
    try {
        const favorites = await db.collection('favorites')
            .find({ user_id: req.user.uid }, { projection: { _id: 0, product_id: 1 } })
            .toArray();
        res.json({ success: true, favorites: favorites.map(f => f.product_id) });
    } catch (err) {
        console.error('Get favorites error:', err);
        res.status(500).json({ success: false, favorites: [] });
    }
});

app.post('/api/favorites/toggle', requireAuth, async (req, res) => {
    if (!mongoAvailable) {
        return res.status(503).json({ success: false, message: 'Database not available' });
    }
    try {
        const { productId, productName, price, category } = req.body;

        if (productId == null) {
            return res.status(400).json({ success: false, message: 'Product ID required' });
        }

        const col = db.collection('favorites');
        const favoriteFilter = { user_id: req.user.uid, product_id: productId };
        const existing = await col.findOne(favoriteFilter);

        if (existing) {
            await col.deleteOne(favoriteFilter);
            return res.json({ success: true, action: 'removed' });
        }

        await col.insertOne({
            user_id: req.user.uid,
            product_id: productId,
            product_name: productName || '',
            price: price || 0,
            category: category || '',
            added_at: new Date(),
        });
        res.json({ success: true, action: 'added' });
    } catch (err) {
        console.error('Favorites toggle error:', err);
        res.status(500).json({ success: false, message: 'Server error' });
    }
});


// ═══════════════════════════════════════════════════════════════
// DELIVERY CHARGE API (REST FALLBACK)
// ═══════════════════════════════════════════════════════════════

app.post('/api/delivery-charge', (req, res) => {
    const { lat, lng } = req.body;
    if (!lat || !lng) {
        return res.status(400).json({ success: false, message: 'Location coordinates required' });
    }
    const info = getDeliveryInfo(lat, lng);
    res.json({ success: true, ...info });
});


// ═══════════════════════════════════════════════════════════════
// SOCKET.IO — LIVE LOCATION TRACKING
// ═══════════════════════════════════════════════════════════════

io.on('connection', (socket) => {
    console.log(`[SOCKET] Client connected: ${socket.id}`);

    // Send shop location on connect
    socket.emit('shop-info', {
        shopLocation: SHOP_LOCATION,
        deliveryRate: DELIVERY_RATE,
    });

    // Handle live location updates from customer
    socket.on('location-update', (data) => {
        const { lat, lng } = data;

        if (lat != null && lng != null) {
            const info = getDeliveryInfo(lat, lng);

            // Emit delivery info back to this customer
            socket.emit('delivery-info', {
                ...info,
                customerLocation: { lat, lng },
            });

            console.log(
                `[SOCKET] ${socket.id} | Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)} ` +
                `| Distance: ${info.distance} km | Delivery: Rs.${info.deliveryCharge}`
            );
        }
    });

    socket.on('disconnect', () => {
        console.log(`[SOCKET] Client disconnected: ${socket.id}`);
    });
});


// ═══════════════════════════════════════════════════════════════
// START SERVER
// ═══════════════════════════════════════════════════════════════

connectDB().then(() => {
    initFirebaseAdmin();
    server.listen(PORT, () => {
        console.log('');
        console.log('='.repeat(50));
        console.log('  ZENITH FOODS SERVER');
        console.log('='.repeat(50));
        console.log(`  URL:            http://localhost:${PORT}`);
        console.log(`  Shop Location:  ${SHOP_LOCATION.lat}, ${SHOP_LOCATION.lng}`);
        console.log(`  Delivery Rate:  Rs.${DELIVERY_RATE}/km`);
        console.log(`  MongoDB:        ${mongoAvailable ? 'Connected' : 'Unavailable'}`);
        console.log(`  Firebase Auth:  ${isFirebaseReady() ? 'Configured' : 'Missing env config'}`);
        console.log(`  Socket.IO:      Enabled`);
        console.log('='.repeat(50));
        console.log('');
    });
});
