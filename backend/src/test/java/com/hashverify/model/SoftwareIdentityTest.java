package com.hashverify.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SoftwareIdentity
 */
class SoftwareIdentityTest {

    @Test
    void testConstructorAndGetters() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        
        assertEquals("example.com", identity.getSourceDomain());
        assertEquals("setup.exe", identity.getNormalizedFilename());
        assertEquals(1024L, identity.getFileSize());
    }

    @Test
    void testGenerateIdentityHash() {
        SoftwareIdentity identity1 = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        SoftwareIdentity identity2 = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        
        String hash1 = identity1.generateIdentityHash();
        String hash2 = identity2.generateIdentityHash();
        
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1, hash2);
    }

    @Test
    void testGenerateIdentityHashDifferentInputs() {
        SoftwareIdentity identity1 = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        SoftwareIdentity identity2 = new SoftwareIdentity("example.com", "setup.exe", 2048L);
        
        String hash1 = identity1.generateIdentityHash();
        String hash2 = identity2.generateIdentityHash();
        
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testEqualsAndHashCode() {
        SoftwareIdentity identity1 = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        SoftwareIdentity identity2 = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        SoftwareIdentity identity3 = new SoftwareIdentity("other.com", "setup.exe", 1024L);
        
        assertEquals(identity1, identity2);
        assertNotEquals(identity1, identity3);
        assertEquals(identity1.hashCode(), identity2.hashCode());
    }

    @Test
    void testToString() {
        SoftwareIdentity identity = new SoftwareIdentity("example.com", "setup.exe", 1024L);
        String toString = identity.toString();
        
        assertTrue(toString.contains("example.com"));
        assertTrue(toString.contains("setup.exe"));
        assertTrue(toString.contains("1024"));
    }
}