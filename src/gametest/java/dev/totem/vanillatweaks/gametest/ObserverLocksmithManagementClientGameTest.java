package dev.totem.vanillatweaks.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Verifies the explicit unsupported state when the owning module is absent. */
public final class ObserverLocksmithManagementClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        ObserverAbsentOwnerProviderAssertions.verify(context, "locksmith_management", "dev.totem.locksmith.client.LocksmithManagementScreen");
    }
}
