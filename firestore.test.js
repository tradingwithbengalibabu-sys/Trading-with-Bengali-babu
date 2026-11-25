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

test("Multi-currency fields (INR/USD, usdToInrRate, preferredCurrency) validate strictly while preserving legacy compatibility", async () => {
  const ts = new Date(Date.now() - 5000);
  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();

  // Student creates profile with preferredCurrency = INR
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      ...validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts),
      preferredCurrency: "INR",
    })
  );

  // Student can update preferredCurrency to USD
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      preferredCurrency: "USD",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid preferredCurrency is rejected
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      preferredCurrency: "EUR",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // INR trade with saved historical rate succeeds
  await assertSucceeds(
    alice.collection("trades").doc("alice_inr").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      currency: "INR",
      usdToInrRate: 84.35,
      actualPnl: 8435.0,
    })
  );

  // USD trade with saved historical rate succeeds
  await assertSucceeds(
    alice.collection("trades").doc("alice_usd").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      instrument: "XAUUSD",
      currency: "USD",
      usdToInrRate: 85.10,
      actualPnl: 120.0,
    })
  );

  // Invalid currency rejected
  await assertFails(
    alice.collection("trades").doc("alice_bad_curr").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      currency: "GBP",
      usdToInrRate: 84.5,
    })
  );

  // Invalid exchange rate rejected (<= 0 or > 1000)
  await assertFails(
    alice.collection("trades").doc("alice_bad_rate_zero").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      currency: "INR",
      usdToInrRate: 0,
    })
  );
  await assertFails(
    alice.collection("trades").doc("alice_bad_rate_high").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      currency: "INR",
      usdToInrRate: 1500,
    })
  );
});

test("Communication System: strict 1:1 thread isolation, warnings privacy, and admin-only announcements", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Bangali Babu", ADMIN_EMAIL, "ADMIN", "ACTIVE", ts));
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();

  // 1. Alice sends a message to Admin in her own conversation
  await assertSucceeds(
    alice.collection("conversations").doc(ALICE_UID).set({
      studentId: ALICE_UID,
      studentName: "Alice",
      studentEmail: ALICE_EMAIL,
      lastMessageText: "Sir please review my NIFTY trade.",
      lastMessageSenderRole: "STUDENT",
      lastMessageAtMillis: Date.now() - 3000,
      unreadByAdminCount: 1,
      unreadByStudentCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    alice.collection("conversations").doc(ALICE_UID).collection("messages").doc("msg_1").set({
      studentId: ALICE_UID,
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "STUDENT",
      body: "Sir please review my NIFTY trade.",
      sentAtMillis: Date.now() - 3000,
      createdAt: ts,
    })
  );

  // 2. Bob MUST NOT read or write Alice's conversation or messages
  await assertFails(bob.collection("conversations").doc(ALICE_UID).get());
  await assertFails(bob.collection("conversations").doc(ALICE_UID).collection("messages").get());
  await assertFails(bob.collection("conversations").get());
  await assertFails(
    bob.collection("conversations").doc(ALICE_UID).collection("messages").doc("hack_msg").set({
      studentId: ALICE_UID,
      senderId: BOB_UID,
      senderName: "Bob",
      senderRole: "STUDENT",
      body: "Sneaking in",
      sentAtMillis: Date.now() - 2000,
      createdAt: ts,
    })
  );

  // 3. Student cannot impersonate ADMIN senderRole
  await assertFails(
    alice.collection("conversations").doc(ALICE_UID).collection("messages").doc("msg_spoof").set({
      studentId: ALICE_UID,
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "ADMIN",
      body: "Fake admin",
      sentAtMillis: Date.now() - 2000,
      createdAt: ts,
    })
  );

  // 4. Admin can read inbox, reply to Alice, and Alice can mark unreadByStudentCount = 0
  await assertSucceeds(admin.collection("conversations").get());
  await assertSucceeds(
    admin.collection("conversations").doc(ALICE_UID).collection("messages").doc("msg_2").set({
      studentId: ALICE_UID,
      senderId: ADMIN_UID,
      senderName: "Bangali Babu",
      senderRole: "ADMIN",
      body: "Good discipline on SL!",
      sentAtMillis: Date.now() - 1000,
      createdAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("conversations").doc(ALICE_UID).update({
      lastMessageText: "Good discipline on SL!",
      lastMessageSenderRole: "ADMIN",
      lastMessageAtMillis: Date.now() - 1000,
      unreadByAdminCount: 0,
      unreadByStudentCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    alice.collection("conversations").doc(ALICE_UID).update({
      unreadByStudentCount: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // 5. Warnings: Admin sends personal warning to Alice; Bob cannot read/update it; Alice can mark readByStudent = true
  await assertSucceeds(
    admin.collection("warnings").doc("warn_alice_1").set({
      studentId: ALICE_UID,
      studentName: "Alice",
      senderAdminId: ADMIN_UID,
      senderAdminName: "Bangali Babu",
      severity: "CRITICAL",
      title: "Overtrading Risk Alert",
      message: "You exceeded 5 trades today. Pause trading for the day.",
      readByStudent: false,
      sentAtMillis: Date.now() - 1500,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    alice.collection("warnings").doc("warn_self").set({
      studentId: ALICE_UID,
      studentName: "Alice",
      senderAdminId: ALICE_UID,
      senderAdminName: "Alice",
      severity: "IMPORTANT",
      title: "Fake warning",
      message: "Test",
      readByStudent: false,
      sentAtMillis: Date.now() - 1500,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bob.collection("warnings").doc("warn_alice_1").get());
  await assertFails(bob.collection("warnings").where("studentId", "==", ALICE_UID).get());
  await assertSucceeds(alice.collection("warnings").where("studentId", "==", ALICE_UID).get());
  await assertSucceeds(
    alice.collection("warnings").doc("warn_alice_1").update({
      readByStudent: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    alice.collection("warnings").doc("warn_alice_1").update({
      title: "Edited title",
      readByStudent: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // 6. Announcements: Only Admin creates/deletes; active students read & track read millis privately
  await assertSucceeds(
    admin.collection("announcements").doc("ann_1").set({
      title: "Weekly Live Market Session",
      message: "Join NIFTY & Gold review tonight at 8 PM IST.",
      priority: "IMPORTANT",
      authorId: ADMIN_UID,
      authorName: "Bangali Babu",
      publishedAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    alice.collection("announcements").doc("ann_fake").set({
      title: "Spam",
      message: "Hello",
      priority: "NORMAL",
      authorId: ALICE_UID,
      authorName: "Alice",
      publishedAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(alice.collection("announcements").get());
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      lastAnnouncementReadMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Smart Discipline System: thresholds, SL/strategy alerts, weekly reports, strict student isolation & Admin access", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Bangali Babu", ADMIN_EMAIL, "ADMIN", "ACTIVE", ts));
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();

  // 1. Alice updates configurable smart alert thresholds and break cooldown
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      slAlertLookbackCount: 10,
      slAlertThreshold: 10,
      strategySlThreshold: 5,
      breakRecommendedUntilMillis: Date.now() + 3 * 86400000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid thresholds rejected (e.g., lookback < 2)
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      slAlertLookbackCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 2. Alice creates her own Stop Loss Streak Smart Alert & acknowledges it
  await assertSucceeds(
    alice.collection("smart_discipline_alerts").doc("sl_alice_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      alertType: "STOP_LOSS_STREAK",
      triggerKey: "SL_WIN_10_10_t1_t10",
      strategyName: "",
      stopLossCount: 10,
      lookbackCount: 10,
      recommendedBreakDays: 5,
      title: "Critical Alert: 10/10 Stop Losses Hit",
      message: "Take a 2-7 day break.",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      triggeredAtMillis: Date.now() - 2000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    alice.collection("smart_discipline_alerts").doc("sl_alice_1").update({
      acknowledged: true,
      acknowledgedAtMillis: Date.now() - 100,
      recommendedBreakDays: 4,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // 3. Alice creates her Weekly Report
  await assertSucceeds(
    alice.collection("weekly_reports").doc("alice_2026_W46").set({
      userId: ALICE_UID,
      userName: "Alice",
      weekKey: "2026-W46",
      weekStartIso: "2026-11-09",
      weekEndIso: "2026-11-15",
      currencyCode: "USD",
      totalTrades: 8,
      winningTrades: 3,
      losingTrades: 5,
      breakEvenTrades: 0,
      stopLossHits: 5,
      netPnl: -120.0,
      winRatePercent: 37.5,
      disciplineScore: 68,
      overtradingDays: 1,
      revengeTradingCount: 1,
      noStopLossCount: 0,
      ruleViolationsCount: 1,
      repeatedMistakes: ["FOMO (2x - Repeated)"],
      poorStrategies: ["Breakout - -$120 (5W SL)"],
      ruleViolationDetails: ["TWBB-101 (XAUUSD): Missing Entry Reason"],
      improvementAreas: ["Overtrading & Emotional Patterns"],
      personalizedSuggestions: ["Take a 30-min cooldown after SL."],
      summaryText: "2026-W46 summary",
      readByUser: false,
      generatedAtMillis: Date.now() - 1500,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 4. Bob MUST NOT read/list/create/update Alice's smart alerts or weekly reports
  await assertFails(bob.collection("smart_discipline_alerts").doc("sl_alice_1").get());
  await assertFails(bob.collection("smart_discipline_alerts").where("userId", "==", ALICE_UID).get());
  await assertFails(bob.collection("smart_discipline_alerts").get());
  await assertFails(bob.collection("weekly_reports").doc("alice_2026_W46").get());
  await assertFails(bob.collection("weekly_reports").where("userId", "==", ALICE_UID).get());
  await assertFails(bob.collection("weekly_reports").get());

  // Bob cannot spoof Alice's alert/report
  await assertFails(
    bob.collection("smart_discipline_alerts").doc("spoof_alert").set({
      userId: ALICE_UID,
      userName: "Alice",
      alertType: "STRATEGY_LOSS_LIMIT",
      triggerKey: "STRAT_TEST",
      strategyName: "Breakout",
      stopLossCount: 6,
      lookbackCount: 10,
      recommendedBreakDays: 0,
      title: "Fake",
      message: "Fake",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      triggeredAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 5. Alice can read her own smart alerts & weekly reports; Admin can monitor all
  await assertSucceeds(alice.collection("smart_discipline_alerts").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(alice.collection("weekly_reports").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(admin.collection("smart_discipline_alerts").get());
  await assertSucceeds(admin.collection("weekly_reports").get());

  // Admin can also create own alerts & weekly reports
  await assertSucceeds(
    admin.collection("smart_discipline_alerts").doc("admin_strat_1").set({
      userId: ADMIN_UID,
      userName: "Bangali Babu",
      alertType: "STRATEGY_LOSS_LIMIT",
      triggerKey: "STRAT_SCALP_C6",
      strategyName: "Scalping",
      stopLossCount: 6,
      lookbackCount: 12,
      recommendedBreakDays: 0,
      title: "Strategy Warning: Scalping (6 Stop Losses)",
      message: "Review & backtest Scalping.",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      triggeredAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Student Profile: students update own profile & photo metadata; Admin can view & edit; cross-student & invalid fields blocked", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Bangali Babu", ADMIN_EMAIL, "ADMIN", "ACTIVE", ts));
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();

  // 1. Alice completes her profile & photo URL/path
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      name: "Alice Chatterjee",
      mobileNumber: "+91 9876543210",
      whatsappNumber: "+91 9876543210",
      dateOfBirth: "2000-08-15",
      gender: "Female",
      fullAddress: "Salt Lake Sector V, Kolkata, West Bengal 700091",
      profilePhotoUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/student_profiles%2Fstudent_alice_123%2Favatar.jpg",
      profilePhotoPath: `student_profiles/${ALICE_UID}/avatar.jpg`,
      profileCompleted: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 2. Alice can delete/clear her own profile photo fields
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).update({
      profilePhotoUrl: "",
      profilePhotoPath: "",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // 3. Student cannot spoof email, role, or invalid gender
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      email: "hacked@example.com",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      gender: "InvalidGenderValue",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 4. Bob cannot read or edit Alice's profile
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).update({
      mobileNumber: "+91 9999999999",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 5. Admin can view and edit Alice's personal profile details
  await assertSucceeds(admin.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    admin.collection("users").doc(ALICE_UID).update({
      name: "Alice C.",
      mobileNumber: "+91 9830012345",
      whatsappNumber: "+91 9830012345",
      dateOfBirth: "2000-08-15",
      gender: "Female",
      fullAddress: "Park Street, Kolkata, West Bengal 700016",
      profileCompleted: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Trades Date-Time Tracking: validates entryTimeMillis, exitTimeMillis, session, exitClosureType & timezoneId while preserving legacy writes", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const entryMillis = Date.now() - 3600_000;
  const exitMillis = entryMillis + 25 * 60_000;

  // Valid trade with full entry/exit timestamps, session, closure type & Asia/Kolkata timezone
  await assertSucceeds(
    alice.collection("trades").doc("alice_timed_1").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      entryTimeMillis: entryMillis,
      exitDateIso: "2026-11-23",
      exitTimeMillis: exitMillis,
      tradingSession: "India Morning",
      exitClosureType: "AUTO_TP",
      timezoneId: "Asia/Kolkata",
    })
  );

  // Partial update modifying exit time, session & closure type succeeds
  await assertSucceeds(
    alice.collection("trades").doc("alice_timed_1").update({
      exitTimeMillis: exitMillis + 60_000,
      tradingSession: "India Mid-Day",
      exitClosureType: "MANUAL",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Invalid: exitTimeMillis before entryTimeMillis rejected
  await assertFails(
    alice.collection("trades").doc("alice_bad_duration").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      entryTimeMillis: entryMillis,
      exitDateIso: "2026-11-23",
      exitTimeMillis: entryMillis - 60_000,
      tradingSession: "India Morning",
      exitClosureType: "AUTO_SL",
      timezoneId: "Asia/Kolkata",
    })
  );

  // Invalid exitClosureType rejected
  await assertFails(
    alice.collection("trades").doc("alice_bad_closure").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      exitClosureType: "FORCED_HACK",
    })
  );

  // Cross-user isolation still blocks Bob
  await assertFails(bob.collection("trades").doc("alice_timed_1").get());
});

test("Three Automatic Risk Warning Systems: Weekly Loss >$200 eq., Fees/Open tradeStatus, and Alert Recalculation Rules", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Nirab Sarkar", ADMIN_EMAIL, "ADMIN", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();

  // 1. Alice saves a CLOSED trade with fees and an OPEN trade
  await assertSucceeds(
    alice.collection("trades").doc("alice_closed_fee").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      tradeStatus: "CLOSED",
      fees: 15.5,
      actualPnl: -210.0,
      result: "LOSS",
    })
  );
  await assertSucceeds(
    alice.collection("trades").doc("alice_open_trade").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      tradeStatus: "OPEN",
      fees: 0.0,
      actualPnl: -500.0,
      result: "BREAK_EVEN",
    })
  );

  // Invalid tradeStatus or negative fees rejected
  await assertFails(
    alice.collection("trades").doc("alice_bad_status").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      tradeStatus: "PENDING",
    })
  );
  await assertFails(
    alice.collection("trades").doc("alice_neg_fee").set({
      ...validTradePayload(ALICE_UID, "Alice", ts),
      fees: -5.0,
    })
  );

  // 2. Alice creates WEEKLY_LOSS_LIMIT alert with USD/INR loss amounts & resolved flag
  await assertSucceeds(
    alice.collection("smart_discipline_alerts").doc("wk_alice_2026_W47").set({
      userId: ALICE_UID,
      userName: "Alice",
      alertType: "WEEKLY_LOSS_LIMIT",
      triggerKey: "WKLOSS_2026-W47",
      strategyName: "",
      stopLossCount: 0,
      lookbackCount: 0,
      recommendedBreakDays: 0,
      lossAmountUsd: 225.5,
      lossAmountInr: 19054.75,
      exchangeRateUsed: 84.5,
      triggerDateIso: "2026-11-24",
      resolved: false,
      title: "🚨 WEEKLY LOSS LIMIT EXCEEDED! 🚨",
      message: "Your trading losses this week have exceeded your $200 equivalent risk limit.",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      triggeredAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 3. Alice can mark alert resolved on trade edit/delete and can acknowledge with "I Understand"
  await assertSucceeds(
    alice.collection("smart_discipline_alerts").doc("wk_alice_2026_W47").update({
      resolved: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    alice.collection("smart_discipline_alerts").doc("wk_alice_2026_W47").update({
      resolved: false,
      lossAmountUsd: 240.0,
      lossAmountInr: 20280.0,
      acknowledged: true,
      acknowledgedAtMillis: Date.now() - 100,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // 4. Negative lossAmountUsd rejected and Bob blocked from Alice's alert
  await assertFails(
    alice.collection("smart_discipline_alerts").doc("wk_bad_loss").set({
      userId: ALICE_UID,
      userName: "Alice",
      alertType: "WEEKLY_LOSS_LIMIT",
      triggerKey: "WKLOSS_BAD",
      strategyName: "",
      stopLossCount: 0,
      lookbackCount: 0,
      recommendedBreakDays: 0,
      lossAmountUsd: -50.0,
      lossAmountInr: 0.0,
      exchangeRateUsed: 84.5,
      triggerDateIso: "2026-11-24",
      resolved: false,
      title: "Invalid",
      message: "Invalid",
      acknowledged: false,
      acknowledgedAtMillis: 0,
      triggeredAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(bob.collection("smart_discipline_alerts").doc("wk_alice_2026_W47").get());
  await assertSucceeds(admin.collection("smart_discipline_alerts").doc("wk_alice_2026_W47").get());
});

function validTradeSignalPayload(authorId = ADMIN_UID, ts = new Date(Date.now() - 5000), overrides = {}) {
  return {
    instrument: "NIFTY",
    direction: "BUY",
    entryPrice: 24150.0,
    stopLoss: 24080.0,
    target1: 24250.0,
    target2: 24340.0,
    riskRewardRatio: "1:1.43",
    strategyName: "Breakout Reversal",
    instructions: "Wait for 5m candle close above VWAP.",
    status: "ACTIVE",
    audienceType: "ALL_STUDENTS",
    selectedStudentIds: [],
    screenshotUrls: ["https://firebasestorage.googleapis.com/v0/b/test/o/sig1.jpg"],
    screenshotPaths: ["trade_signals/sig_1/screenshots/0.jpg"],
    voiceNoteUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/voice1.m4a",
    voiceNotePath: "trade_signals/sig_1/voice/voice_1.m4a",
    voiceDurationSec: 18,
    authorId,
    authorName: "Bangali Babu",
    createdAtMillis: Date.now() - 2000,
    updatedAtMillis: Date.now() - 1000,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

test("Admin Trade Desk: signals, audience targeting, reactions, and independent Signal-Only Blocking Matrix", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Bangali Babu", ADMIN_EMAIL, "ADMIN", "ACTIVE", ts));
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "ACTIVE", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();

  // 1. Admin publishes ALL_STUDENTS signal and SELECTED_STUDENTS (Alice-only) signal
  await assertSucceeds(
    admin.collection("trade_signals").doc("sig_all").set(validTradeSignalPayload(ADMIN_UID, ts))
  );
  await assertSucceeds(
    admin.collection("trade_signals").doc("sig_alice_only").set(
      validTradeSignalPayload(ADMIN_UID, ts, {
        instrument: "GOLD",
        direction: "SELL",
        audienceType: "SELECTED_STUDENTS",
        selectedStudentIds: [ALICE_UID],
      })
    )
  );

  // Student cannot publish, update, or delete trade signals
  await assertFails(
    alice.collection("trade_signals").doc("sig_student_hack").set(validTradeSignalPayload(ALICE_UID, ts))
  );
  await assertFails(
    alice.collection("trade_signals").doc("sig_all").update({ status: "CANCELLED", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(alice.collection("trade_signals").doc("sig_all").delete());

  // 2. Active + Allowed: Alice reads both signals via filtered queries; Bob reads ALL_STUDENTS only
  await assertSucceeds(alice.collection("trade_signals").doc("sig_all").get());
  await assertSucceeds(alice.collection("trade_signals").doc("sig_alice_only").get());
  await assertSucceeds(alice.collection("trade_signals").where("audienceType", "==", "ALL_STUDENTS").get());
  await assertSucceeds(alice.collection("trade_signals").where("selectedStudentIds", "array-contains", ALICE_UID).get());

  await assertSucceeds(bob.collection("trade_signals").doc("sig_all").get());
  await assertFails(bob.collection("trade_signals").doc("sig_alice_only").get());
  await assertSucceeds(bob.collection("trade_signals").where("audienceType", "==", "ALL_STUDENTS").get());
  await assertSucceeds(bob.collection("trade_signals").where("selectedStudentIds", "array-contains", BOB_UID).get());

  // 3. Emoji reactions: Alice can react on sig_all and sig_alice_only; Bob CANNOT react on sig_alice_only or spoof Alice
  await assertSucceeds(
    alice.collection("trade_signals").doc("sig_all").collection("reactions").doc(ALICE_UID).set({
      signalId: "sig_all",
      userId: ALICE_UID,
      userName: "Alice",
      emoji: "🔥",
      reactedAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    alice.collection("trade_signals").doc("sig_all").collection("reactions").doc(ALICE_UID).update({
      emoji: "🚀",
      reactedAtMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Invalid emoji rejected
  await assertFails(
    bob.collection("trade_signals").doc("sig_all").collection("reactions").doc(BOB_UID).set({
      signalId: "sig_all",
      userId: BOB_UID,
      userName: "Bob",
      emoji: "🤡",
      reactedAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Bob cannot react to Alice-only signal
  await assertFails(
    bob.collection("trade_signals").doc("sig_alice_only").collection("reactions").doc(BOB_UID).set({
      signalId: "sig_alice_only",
      userId: BOB_UID,
      userName: "Bob",
      emoji: "👍",
      reactedAtMillis: Date.now() - 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 4. Student CANNOT unblock/modify own signalAccess
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      signalAccess: "BLOCKED",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    alice.collection("signal_access").doc(ALICE_UID).set({
      userId: ALICE_UID,
      studentName: "Alice",
      studentEmail: ALICE_EMAIL,
      signalAccess: "ALLOWED",
      updatedByUid: ALICE_UID,
      updatedAtMillis: Date.now() - 300,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 5. SIGNAL-ONLY BLOCKING: Admin blocks Alice from signals ONLY (status stays ACTIVE)
  await assertSucceeds(
    admin.collection("users").doc(ALICE_UID).update({
      signalAccess: "BLOCKED",
      updatedAt: new Date(Date.now() - 250),
    })
  );
  await assertSucceeds(
    admin.collection("signal_access").doc(ALICE_UID).set({
      userId: ALICE_UID,
      studentName: "Alice",
      studentEmail: ALICE_EMAIL,
      signalAccess: "BLOCKED",
      updatedByUid: ADMIN_UID,
      updatedAtMillis: Date.now() - 250,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Blocked Alice loses ALL signal, reaction reading & writing access
  await assertFails(alice.collection("trade_signals").doc("sig_all").get());
  await assertFails(alice.collection("trade_signals").doc("sig_alice_only").get());
  await assertFails(alice.collection("trade_signals").where("audienceType", "==", "ALL_STUDENTS").get());
  await assertFails(alice.collection("trade_signals").where("selectedStudentIds", "array-contains", ALICE_UID).get());
  await assertFails(alice.collection("trade_signals").doc("sig_all").collection("reactions").get());
  await assertFails(
    alice.collection("trade_signals").doc("sig_all").collection("reactions").doc(ALICE_UID).update({
      emoji: "👍",
      reactedAtMillis: Date.now() - 200,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Blocked Alice STILL has 100% access to her own journal, profile, reviews, announcements, and own signal_access badge
  await assertSucceeds(alice.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(alice.collection("signal_access").doc(ALICE_UID).get());
  await assertSucceeds(alice.collection("trades").doc("alice_still_works").set(validTradePayload(ALICE_UID, "Alice", ts)));
  await assertSucceeds(alice.collection("trades").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(alice.collection("discipline_reviews").doc("alice_rev_ok").set(validReviewPayload(ALICE_UID, ts)));

  // 6. UNBLOCKING: Admin unblocks Alice's signals without altering account status
  await assertSucceeds(
    admin.collection("users").doc(ALICE_UID).update({
      signalAccess: "ALLOWED",
      updatedAt: new Date(Date.now() - 150),
    })
  );
  await assertSucceeds(
    admin.collection("signal_access").doc(ALICE_UID).update({
      signalAccess: "ALLOWED",
      updatedByUid: ADMIN_UID,
      updatedAtMillis: Date.now() - 150,
      updatedAt: new Date(Date.now() - 150),
    })
  );
  await assertSucceeds(alice.collection("trade_signals").doc("sig_all").get());
  await assertSucceeds(alice.collection("trade_signals").where("audienceType", "==", "ALL_STUDENTS").get());

  // 7. SUSPENDED ACCOUNT + ALLOWED SIGNALS: Suspended Bob cannot access signals OR trades
  await assertSucceeds(
    admin.collection("users").doc(BOB_UID).update({
      status: "SUSPENDED",
      signalAccess: "ALLOWED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(bob.collection("trade_signals").doc("sig_all").get());
  await assertFails(bob.collection("trade_signals").where("audienceType", "==", "ALL_STUDENTS").get());
  await assertFails(bob.collection("trades").doc("bob_suspended_fail").set(validTradePayload(BOB_UID, "Bob", ts)));
});

test("Live Classroom: Admin starts/moderates/ends session; active students join, raise hand & chat without self-approving voice", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set(validUserPayload(ADMIN_UID, "Bangali Babu", ADMIN_EMAIL, "ADMIN", "ACTIVE", ts));
    await db.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "Alice", ALICE_EMAIL, "STUDENT", "ACTIVE", ts));
    await db.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "Bob", BOB_EMAIL, "STUDENT", "SUSPENDED", ts));
  });

  const alice = studentContext(ALICE_UID, ALICE_EMAIL).firestore();
  const bob = studentContext(BOB_UID, BOB_EMAIL).firestore();
  const admin = adminContext().firestore();
  const roomRef = "twbb_live_classroom_main";

  // 1. Student cannot create/start a live classroom; Admin can
  await assertFails(
    alice.collection("live_classrooms").doc(roomRef).set({
      title: "Fake Class",
      roomName: roomRef,
      isLive: true,
      isScreenSharing: false,
      hostUid: ALICE_UID,
      hostName: "Alice",
      startedAtMillis: Date.now() - 1000,
      endedAtMillis: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("live_classrooms").doc(roomRef).set({
      title: "NIFTY & Gold Live Price Action",
      roomName: roomRef,
      isLive: true,
      isScreenSharing: true,
      hostUid: ADMIN_UID,
      hostName: "Bangali Babu",
      startedAtMillis: Date.now() - 1000,
      endedAtMillis: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 2. Active student Alice joins classroom with safe defaults (voiceApproved=false, isMutedByTeacher=true)
  await assertSucceeds(
    alice.collection("live_classrooms").doc(roomRef).collection("participants").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice",
      role: "STUDENT",
      profilePhotoUrl: "",
      joinedAtMillis: Date.now() - 800,
      handRaised: false,
      voiceApproved: false,
      isMutedByTeacher: true,
      isSelfMuted: true,
      isRemoved: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Student CANNOT self-approve voice on join or update
  await assertFails(
    alice.collection("live_classrooms").doc(roomRef).collection("participants").doc(ALICE_UID).update({
      voiceApproved: true,
      isMutedByTeacher: false,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  // Student CAN raise hand & toggle own self mute
  await assertSucceeds(
    alice.collection("live_classrooms").doc(roomRef).collection("participants").doc(ALICE_UID).update({
      handRaised: true,
      isSelfMuted: false,
      updatedAt: new Date(Date.now() - 350),
    })
  );

  // 3. Suspended student Bob cannot join or post messages
  await assertFails(
    bob.collection("live_classrooms").doc(roomRef).collection("participants").doc(BOB_UID).set({
      uid: BOB_UID,
      name: "Bob",
      role: "STUDENT",
      profilePhotoUrl: "",
      joinedAtMillis: Date.now() - 500,
      handRaised: false,
      voiceApproved: false,
      isMutedByTeacher: true,
      isSelfMuted: true,
      isRemoved: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 4. Admin approves Alice voice and unmutes her; Alice & Admin post live chat messages
  await assertSucceeds(
    admin.collection("live_classrooms").doc(roomRef).collection("participants").doc(ALICE_UID).update({
      voiceApproved: true,
      isMutedByTeacher: false,
      handRaised: false,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    alice.collection("live_classrooms").doc(roomRef).collection("messages").doc("chat_1").set({
      roomId: roomRef,
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "STUDENT",
      body: "Sir where should stop loss be placed?",
      sentAtMillis: Date.now() - 250,
      createdAt: ts,
    })
  );
  // Student cannot spoof ADMIN senderRole in chat
  await assertFails(
    alice.collection("live_classrooms").doc(roomRef).collection("messages").doc("chat_spoof").set({
      roomId: roomRef,
      senderId: ALICE_UID,
      senderName: "Alice",
      senderRole: "ADMIN",
      body: "Spoof",
      sentAtMillis: Date.now() - 200,
      createdAt: ts,
    })
  );

  // 5. Admin removes Alice; removed Alice can no longer update her participant record
  await assertSucceeds(
    admin.collection("live_classrooms").doc(roomRef).collection("participants").doc(ALICE_UID).update({
      isRemoved: true,
      voiceApproved: false,
      isMutedByTeacher: true,
      updatedAt: new Date(Date.now() - 150),
    })
  );
  await assertFails(
    alice.collection("live_classrooms").doc(roomRef).collection("participants").doc(ALICE_UID).update({
      handRaised: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});


