# Encrypted Room/SQLCipher migration

- New records are persisted through Room's SecureEntries DAO using SQLCipher.
- The SQLite file shiv-personal-v1.db is app-private and uses a random 32-byte
  SQLCipher passphrase. The passphrase is encrypted under Android Keystore
  AES-256-GCM in existing legacy SharedPreferences as __sqlcipher_passphrase_v1.
- Legacy keys are migrated lazily, preserving ciphertext if the new SQLCipher
  write fails. Once written, each old encrypted entry is removed.
- No plaintext SQLite database or fallback is created.
- Losing the Keystore key makes the old data/passphrase inaccessible and must
  trigger explicit recovery/deletion, not silent reinitialization.
- Owner hardware test MUST validate migration of existing notes, cloud settings,
  reminders, macros and conversation history before declaring production-ready.
- CI compilation cannot establish actual on-disk encryption. Test the db with
  stock SQLite and verify it refuses to read the schema.
- WARNING: Room allowMainThreadQueries is a temporary compatibility compromise;
  move all call sites to suspend repositories before production.
- Schema v1 requires explicit SQLCipher migration tests for all future versions.
