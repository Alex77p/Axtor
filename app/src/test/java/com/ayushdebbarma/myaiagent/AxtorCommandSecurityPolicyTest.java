package com.ayushdebbarma.myaiagent;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AxtorCommandSecurityPolicyTest {
    @Test public void snapAllowListAcceptsSafeActions() {
        assertEquals("OK", AxtorCommandSecurityPolicy.authorizeSnap(null, "volume down"));
        assertEquals("OK", AxtorCommandSecurityPolicy.authorizeSnap(null, "wake screen"));
        assertEquals("OK", AxtorCommandSecurityPolicy.authorizeSnap(null, "stop all"));
        assertEquals("OK", AxtorCommandSecurityPolicy.authorizeSnap(null, "open settings"));
    }

    @Test public void snapBlocksArbitraryExecution() {
        assertEquals("ARBITRARY_EXECUTION_BLOCKED", AxtorCommandSecurityPolicy.authorizeSnap(null, "shell rm -rf /"));
        assertEquals("SNAP_ACTION_NOT_ALLOWLISTED", AxtorCommandSecurityPolicy.authorizeSnap(null, "launch calculator"));
        assertEquals("SNAP_ACTION_NOT_ALLOWLISTED", AxtorCommandSecurityPolicy.authorizeSnap(null, "set alarm for 7 am"));
    }

    @Test public void snapBlocksSecurityBypass() {
        assertEquals("SECURITY_BYPASS_BLOCKED", AxtorCommandSecurityPolicy.authorizeSnap(null, "unlock phone"));
        assertEquals("SECURITY_BYPASS_BLOCKED", AxtorCommandSecurityPolicy.authorizeSnap(null, "disable security"));
        assertEquals("DESTRUCTIVE_ACTION_REQUIRES_UI", AxtorCommandSecurityPolicy.authorizeSnap(null, "factory reset"));
    }
}
