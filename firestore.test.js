const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write reminders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").set({
      userId: ALICE_UID,
      topic: "Call Dentist",
      organizedNote: "Book checkup at 3 PM.",
      rawTranscript: "Remind me to call dentist at 3 PM",
      category: "Health",
      completed: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid Kotlin create, partial updates, and list query succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const scheduled = Date.now() + 3600000;

  // Empty list query must succeed (no existing() errors on empty subcollection)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).get()
  );

  // Valid create payload (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").set({
      userId: ALICE_UID,
      topic: "Submit Q4 Budget",
      organizedNote: "• Send deck to Sarah\n• Verify travel items",
      rawTranscript: "Remind me tomorrow at 8:30 to send Q4 budget deck to Sarah and verify travel items",
      category: "Work",
      scheduledAtEpochMillis: scheduled,
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Action 1: editContent partial update
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      topic: "Submit Q4 Budget Deck",
      organizedNote: "• Send deck to Sarah\n• Double check travel expenses",
      category: "Finance",
      scheduledAtEpochMillis: scheduled + 1800000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action 2: toggleComplete partial update
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").update({
      completed: true,
      completedAtEpochMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_1").delete()
  );
});

test("Security bounds: reject cross-user access, ghost fields, @DocumentId id field, immutable mutations, and bad timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_alice").set({
      userId: ALICE_UID,
      topic: "Pick up groceries",
      organizedNote: "Milk and sourdough bread",
      rawTranscript: "Pick up milk and sourdough bread",
      category: "Errand",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user read/list/write/delete must fail
  await assertFails(bobDb.collection(`users/${ALICE_UID}/reminders`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_alice").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_alice").update({
      topic: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_alice").delete());

  // Reject @DocumentId `id` field in create payload
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_id_test").set({
      id: "rem_id_test",
      userId: ALICE_UID,
      topic: "Bad ID payload",
      organizedNote: "Note",
      rawTranscript: "Transcript",
      category: "General",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_ghost").set({
      userId: ALICE_UID,
      topic: "Ghost field test",
      organizedNote: "Note",
      rawTranscript: "Transcript",
      category: "General",
      completed: false,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_future").set({
      userId: ALICE_UID,
      topic: "Future timestamp",
      organizedNote: "Note",
      rawTranscript: "Transcript",
      category: "General",
      completed: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Reject immutable createdAt or rawTranscript modification on update
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_alice").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_alice").update({
      rawTranscript: "Tampered transcript",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid category or oversized topic
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_bad_cat").set({
      userId: ALICE_UID,
      topic: "Invalid category",
      organizedNote: "Note",
      rawTranscript: "Transcript",
      category: "UnknownCategory",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).doc("rem_big_topic").set({
      userId: ALICE_UID,
      topic: "a".repeat(101),
      organizedNote: "Note",
      rawTranscript: "Transcript",
      category: "General",
      completed: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});
