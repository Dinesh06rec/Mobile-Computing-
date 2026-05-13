const admin = require("firebase-admin");

let firebaseReady = false;

function initFirebaseAdmin() {
  if (admin.apps.length > 0) {
    firebaseReady = true;
    return;
  }

  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY
    ? process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n")
    : null;

  if (!projectId || !clientEmail || !privateKey) {
    firebaseReady = false;
    return;
  }

  admin.initializeApp({
    credential: admin.credential.cert({
      projectId,
      clientEmail,
      privateKey,
    }),
  });
  firebaseReady = true;
}

function isFirebaseReady() {
  return firebaseReady;
}

async function verifyIdToken(idToken) {
  if (!firebaseReady) {
    throw new Error("Firebase Admin not configured");
  }
  return admin.auth().verifyIdToken(idToken, true);
}

module.exports = {
  initFirebaseAdmin,
  isFirebaseReady,
  verifyIdToken,
};
