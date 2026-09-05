package com.ayushdebbarma.myaiagent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AutonomousRepairCoordinatorTest {
    @Test public void safeRepairTargetsAreAccepted() {
        assertTrue(SelfCodingEngine.isSafeSourcePath("app/src/main/java/com/ayushdebbarma/myaiagent/AxtorAgent.java"));
        assertTrue(SelfCodingEngine.isSafeSourcePath("app/src/main/java/com/ayushdebbarma/myaiagent/LlamaRuntime.kt"));
    }

    @Test public void unsafeRepairTargetsAreRejected() {
        assertFalse(SelfCodingEngine.isSafeSourcePath("/etc/passwd"));
        assertFalse(SelfCodingEngine.isSafeSourcePath("app/src/main/AndroidManifest.xml"));
        assertFalse(SelfCodingEngine.isSafeSourcePath("app/src/main/java/../secrets.java"));
    }
}
