package com.ayushdebbarma.myaiagent;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure routing-policy tests for the Phase 6-11 integration surface. */
public class AxtorCoreTest {
    @Test public void allowsOnlyCanonicalActions() {
        assertEquals("GO_HOME", AxtorAgent.canonicalModelAction("GO_HOME"));
        assertEquals("VOLUME_DOWN", AxtorAgent.canonicalModelAction("volume_down"));
        assertEquals("OPEN_APP:Settings", AxtorAgent.canonicalModelAction("OPEN_APP:Settings"));
        assertNull(AxtorAgent.canonicalModelAction("RUN_SHELL:rm -rf /"));
        assertNull(AxtorAgent.canonicalModelAction("OPEN_URL:https://example.com"));
        assertNull(AxtorAgent.canonicalModelAction("LOCK_SCREEN\nRUN_SHELL"));
    }
}
