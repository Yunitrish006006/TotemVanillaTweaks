package dev.totem.vanillatweaks.observer;

/**
 * Single server-side bootstrap seam for the Observer subsystem.
 *
 * <p>Keep VanillaTweaks integration limited to this facade so the Observer
 * runtime can move to its own module without leaking session or transport
 * registration back into unrelated vanilla gameplay code.</p>
 */
public final class ObserverServerRuntime {
    private ObserverServerRuntime() {}

    public static void register() {
        ObserverPayloadRegistration.register();
        ObserverSessionManager.register();
    }
}
