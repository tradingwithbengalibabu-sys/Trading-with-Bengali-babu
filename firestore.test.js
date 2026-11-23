const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const ADMIN_EMAIL = "tradingwithbengalibabu@gmail.com";
const ALICE_UID = "student_alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "student_bob_456";
const BOB_EMAIL = "bob@example.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function studentContext(uid, email, verified = true) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: verified,
  });
}

function validUserPayload(uid, name, email, role = "STUDENT", status = "ACTIVE", ts = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    name,
    email,
    role,
    status,
    dailyProfitGoal: 250.0,
    dailyMaxLossLimit: 100.0,
    maxTradesPerDay: 5,
    maxRiskPerTrade: 50.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validTradePayload(uid, userName, ts = new Date(Date.now() - 5000)) {
  return {
    tradeCode: "TWBB-0001",
    userId: uid,
    userName,
    tradeDateMillis: Date.now() - 10000,
    tradeDateIso: "2026-11-23",
    instrument: "NIFTY 50",
    direction: "BUY",
    timeframe: "M15",
    entryPrice: 24100.0,
    exitPrice: 24200.0,
    stopLoss: 24050.0,
    takeProfit: 24250.0,
    lotSize: 1.0,
    riskAmount: 50.0,
    actualPnl: 150.0,
    result: "WIN",
    strategy: "Price Action",
    entryReason: "Support bounce",
    exitReason: "Target hit",
    mistakes: "None",
    lessonsLearned: "Patience pays",
    notes: "Clean setup",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validReviewPayload(uid, ts = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    periodType: "WEEKLY",
    periodKey: "2026-W47",
    emotionState: "CALM",
    psychologyNotes: "Followed plan",
    slAdherenceRating: 5,
    lessonsSummary: "Respect SL",
    nextActionPlan: "Keep risk fixed",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").get());
  await assertFails(unauth.collection("trades").get());
  await assertFails(unauth.collection("discipline_reviews").get());
  await assertFails(unauth.collection("student_invites").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL)));
  await assertFails(unauth.collection("trades").doc("t1").set(validTradePayload(ALICE_UID, "Alice")));
});

test("Students can create only STUDENT/ACTIVE own profile; cannot escalate to ADMIN or create with @DocumentId 'id'", async () => {
  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const ts = new Date(Date.now() - 5000);

  // Valid create
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL))
  );

  // Cannot create ADMIN role
  await assertFails(
    alice.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "FakeAdmin", ALICE_EMAIL, "ADMIN", "ACTIVE", ts))
  );

  // Cannot include @DocumentId "id" key
  await assertFails(
    alice.collection("users").doc("extra_user").set({
      id: "extra_user",
      ...validUserPayload("extra_user", "Extra", ALICE_EMAIL, "STUDENT", "ACTIVE", ts),
    })
  );

  // Future timestamp rejected
  const futureTs = new Date(Date.now() + 60000);
  await assertFails(
    studentContext("future_uid", "f@example.com").firestore().collection("users").doc("future_uid")
      .set(validUserPayload("future_uid", "Future", "f@example.com", "STUDENT", "ACTIVE", futureTs))
  );
});

test("Student can update own discipline limits but cannot mutate role, status, email, or createdAt", async () => {
  const ts = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await ctx.firestore().collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();

  // Valid partial update of discipline goals
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      dailyProfitGoal: 350.0,
      dailyMaxLossLimit: 120.0,
      maxTradesPerDay: 4,
      maxRiskPerTrade: 40.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cannot elevate own role to ADMIN
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cannot alter createdAt
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      dailyProfitGoal: 400.0,
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cannot read or list Bob's user doc
  await assertFails(alice.collection("users").doc(BOB_UID).get());
  await assertFails(alice.collection("users").get());
});

test("Bootstrapped Admin & spoofing prevention: verified email passes, unverified email fails", async () => {
  const realAdmin = adminContext().firestore();
  const spoofedAdmin = studentContext("spoof_uid", ADMIN_EMAIL, false).firestore();

  await assertSucceeds(
    realAdmin.collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: ADMIN_EMAIL,
      createdAt: new Date(Date.now() - 5000),
    })
  );

  await assertSucceeds(
    realAdmin.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Bangali Babu", ADMIN_EMAIL, "ADMIN", "ACTIVE"))
  );

  // Spoofed admin with email_verified: false fails
  await assertFails(
    spoofedAdmin.collection("users").get()
  );
  await assertFails(
    spoofedAdmin.collection("admins").doc("spoof_uid").set({
      uid: "spoof_uid",
      email: ADMIN_EMAIL,
      createdAt: new Date(Date.now() - 5000),
    })
  );

  // Real Admin can list all users & trades
  await assertSucceeds(realAdmin.collection("users").get());
  await assertSucceeds(realAdmin.collection("trades").get());
});

test("Trades: strict student isolation, filtered query alignment, optional fields, and partial updates", async () => {
  const ts = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("trades").doc("bob_t1").set(validTradePayload(BOB_UID, "Bob", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();

  // Create trade omitting optional null keys (mirrors Kotlin .filterValues { it != null })
  const aliceCreate = { ...validTradePayload(ALICE_UID, "Alice", ts) };
  delete aliceCreate.stopLoss;
  delete aliceCreate.takeProfit;
  await assertSucceeds(alice.collection("trades").doc("alice_t1").set(aliceCreate));

  // Partial update clearing optional fields with null passes
  await assertSucceeds(
    alice.collection("trades").doc("alice_t1").update({
      actualPnl: 220.0,
      stopLoss: null,
      takeProfit: null,
      screenshotData: null,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Student filtered query succeeds; unfiltered query fails
  await assertSucceeds(alice.collection("trades").where("userId", "==", ALICE_UID).get());
  await assertFails(alice.collection("trades").get());

  // Cross-user access denied
  await assertFails(alice.collection("trades").doc("bob_t1").get());
  await assertFails(alice.collection("trades").doc("bob_t1").update({ actualPnl: 999.0, updatedAt: new Date(Date.now() - 500) }));
  await assertFails(alice.collection("trades").doc("bob_t1").delete());
  await assertFails(alice.collection("trades").where("userId", "==", BOB_UID).get());
});

test("Suspended students are blocked from creating, updating, or deleting trades and reviews", async () => {
  const ts = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("trades").doc("alice_t1").set(validTradePayload(ALICE_UID, "Alice", ts));
    await db.collection("discipline_reviews").doc("alice_r1").set(validReviewPayload(ALICE_UID, ts));
  });

  const admin = adminContext().firestore();
  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();

  // Admin suspends Alice
  await assertSucceeds(
    admin.collection("users").doc(ALICE_UID).update({
      status: "SUSPENDED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Suspended Alice cannot create/update/delete trades or reviews
  await assertFails(alice.collection("trades").doc("alice_t2").set(validTradePayload(ALICE_UID, "Alice")));
  await assertFails(alice.collection("trades").doc("alice_t1").update({ notes: "edit", updatedAt: new Date(Date.now() - 500) }));
  await assertFails(alice.collection("trades").doc("alice_t1").delete());
  await assertFails(alice.collection("discipline_reviews").doc("alice_r2").set(validReviewPayload(ALICE_UID)));
  await assertFails(alice.collection("discipline_reviews").doc("alice_r1").update({ psychologyNotes: "edit", updatedAt: new Date(Date.now() - 500) }));
});

test("Student invites: Admin manages all; student can only query own verified email", async () => {
  const admin = adminContext().firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    admin.collection("student_invites").doc("inv_alice").set({
      name: "Alice Trader",
      email: ALICE_EMAIL,
      status: "ACTIVE",
      dailyProfitGoal: 300.0,
      dailyMaxLossLimit: 90.0,
      maxTradesPerDay: 4,
      maxRiskPerTrade: 45.0,
      createdByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const alice = studentContext(ALICE_UID, ALICE_EMAIL, true).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL, true).firestore();

  // Alice can query her own invite
  await assertSucceeds(alice.collection("student_invites").where("email", "==", ALICE_EMAIL).get());
  // Bob cannot query Alice's invite or list all invites
  await assertFails(bob.collection("student_invites").where("email", "==", ALICE_EMAIL).get());
  await assertFails(bob.collection("student_invites").get());
  // Students cannot create invites
  await assertFails(
    alice.collection("student_invites").doc("inv_hack").set({
      name: "Hack",
      email: BOB_EMAIL,
      status: "ACTIVE",
      dailyProfitGoal: 100.0,
      dailyMaxLossLimit: 50.0,
      maxTradesPerDay: 5,
      maxRiskPerTrade: 25.0,
      createdByUid: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Discipline reviews: isolated by userId and Admin can inspect", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(
      validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts)
    );
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();

  await assertSucceeds(
    alice.collection("discipline_reviews").doc("rev_alice").set(validReviewPayload(ALICE_UID, ts))
  );
  await assertSucceeds(
    alice.collection("discipline_reviews").where("userId", "==", ALICE_UID).get()
  );

  await assertFails(bob.collection("discipline_reviews").doc("rev_alice").get());
  await assertFails(bob.collection("discipline_reviews").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(admin.collection("discipline_reviews").where("userId", "==", ALICE_UID).get());
});
