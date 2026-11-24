package com.deskweave.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashesWithRandomSaltAndVerifiesCorrectPassword() {
        String hash1 = PasswordHasher.hash("secret123");
        String hash2 = PasswordHasher.hash("secret123");

        assertNotEquals(hash1, hash2, "Random salts should produce distinct hashes");
        assertTrue(PasswordHasher.verify("secret123", hash1));
        assertTrue(PasswordHasher.verify("secret123", hash2));
        assertFalse(PasswordHasher.verify("wrong", hash1));
        assertFalse(PasswordHasher.verify("secret123", "malformed-without-colon"));
        assertFalse(PasswordHasher.verify(null, hash1));
    }

    @Test
    void verifiesSeedSqlDemoHashes() {
        String adminHash = "dwadmin2026salt0:84e4fcdc235b2cfe874e72e7d62546c2688615d99d398c99577324378b4a6a3b";
        String employeeHash = "dwemp2026salt000:eae5416047fd96c984422a985076cc773d828067e27d97d04ed46b30c5d2c2df";

        assertTrue(PasswordHasher.verify("admin123", adminHash));
        assertFalse(PasswordHasher.verify("pass123", adminHash));

        assertTrue(PasswordHasher.verify("pass123", employeeHash));
        assertFalse(PasswordHasher.verify("admin123", employeeHash));
    }
}
