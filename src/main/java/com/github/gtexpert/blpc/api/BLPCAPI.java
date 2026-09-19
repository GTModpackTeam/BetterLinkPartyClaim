package com.github.gtexpert.blpc.api;

import com.github.gtexpert.blpc.Tags;
import com.github.gtexpert.blpc.api.integration.IntegrationPanelRegistry;
import com.github.gtexpert.blpc.api.modules.IModuleManager;
import com.github.gtexpert.blpc.api.party.IPartyProvider;
import com.github.gtexpert.blpc.api.party.PartyProviderRegistry;
import com.github.gtexpert.blpc.modules.ModuleManager;

/**
 * Central access point and discoverability index for BLPC — the first class a new
 * contributor or addon author should read.
 * A single façade that points at every public subsystem and extension point so nothing has
 * to be hunted for.
 *
 * <h2>Where things live</h2>
 * <table>
 * <caption>Package map</caption>
 * <tr>
 * <th>Package</th>
 * <th>Contents</th>
 * </tr>
 * <tr>
 * <td>{@code api.modules}</td>
 * <td>Module framework SPI ({@code IModule}, {@code @TModule}, containers).</td>
 * </tr>
 * <tr>
 * <td>{@code api.party}</td>
 * <td>Party backend SPI ({@link IPartyProvider}, {@link PartyProviderRegistry}).</td>
 * </tr>
 * <tr>
 * <td>{@code api.event}</td>
 * <td>Public Forge events addons can subscribe to ({@code ChunkModifiedEvent},
 * {@code PartyEvent} — Pre/Post hierarchy for party lifecycle mutations).</td>
 * </tr>
 * <tr>
 * <td>{@code api.util}</td>
 * <td>Shared helpers ({@code Mods}, {@code ModUtility}, {@code PartyQueryUtil} — query
 * party data without depending on internal packages).</td>
 * </tr>
 * <tr>
 * <td>{@code api.integration}</td>
 * <td>{@link IntegrationPanelRegistry} — registry for per-mod settings panels surfaced in the
 * party menu's Addons hub.</td>
 * </tr>
 * <tr>
 * <td>{@code common.party} / {@code common.chunk}</td>
 * <td>Party &amp; claim data models and persistence.</td>
 * </tr>
 * <tr>
 * <td>{@code common.network}</td>
 * <td>Wire messages (multiplexed {@code PartyAction} / {@code ClientNotify}).</td>
 * </tr>
 * <tr>
 * <td>{@code client.gui}</td>
 * <td>ModularUI screens. {@code Screens} = catalog of all GUIs, {@code BLPCGuiTextures} = shared drawables,
 * {@code GuiColors} = palette.</td>
 * </tr>
 * <tr>
 * <td>{@code integration.*}</td>
 * <td>Soft-dependency integrations (BQu, JourneyMap), each behind its own module.</td>
 * </tr>
 * </table>
 *
 * <h2>Extension points for addons</h2>
 * <ul>
 * <li><b>Custom party backend</b> — implement {@link IPartyProvider} and call
 * {@link PartyProviderRegistry#register(IPartyProvider, int)} with a priority
 * ({@link PartyProviderRegistry#PRIORITY_HIGH} to override BQu,
 * {@link PartyProviderRegistry#PRIORITY_LOW} for fallback-only).
 * {@code DefaultPartyProvider} is registered at {@link PartyProviderRegistry#PRIORITY_DEFAULT};
 * {@code BQuPartyProvider} at {@link PartyProviderRegistry#PRIORITY_HIGH}.</li>
 * <li><b>Query party data</b> — use {@code api.util.PartyQueryUtil} ({@code findByName},
 * {@code allPartyNames}, {@code pendingInvitesFor}, {@code resolveName}) instead of depending
 * on internal packages. Delegates to the active {@link IPartyProvider}.</li>
 * <li><b>React to party lifecycle</b> — subscribe to {@code api.event.PartyEvent} on
 * {@code MinecraftForge.EVENT_BUS}. {@code Pre} variants (e.g. {@code Pre.Disbanded}) are
 * {@code @Cancelable}; {@code Post} variants fire after successful mutations.</li>
 * <li><b>React to claims</b> — subscribe to {@code api.event.ChunkModifiedEvent} on the Forge event bus.</li>
 * <li><b>Conditional content</b> — annotate a class with {@code @TModule} and declare
 * {@code modDependencies}; it is discovered automatically (no manual registration).</li>
 * <li><b>Settings panel in the Addons hub</b> — build a ModularUI panel and call
 * {@link IntegrationPanelRegistry#register(String, String, java.util.function.BooleanSupplier,
 * java.util.function.Function)} from a client-guarded {@code init} (lazy method reference,
 * so the panel class is never loaded on a dedicated server). It appears automatically under
 * the party menu's Addons entry once the {@code available} predicate returns {@code true}.</li>
 * </ul>
 */
public final class BLPCAPI {

    /** The mod ID — stable public constant, decoupled from the build-generated {@code Tags}. */
    public static final String MODID = Tags.MODID;

    private BLPCAPI() {}

    /** The active party backend. Replace it via {@link PartyProviderRegistry#register(IPartyProvider)}. */
    public static IPartyProvider partyProvider() {
        return PartyProviderRegistry.get();
    }

    /** The module manager — query enabled modules and lifecycle stage. */
    public static IModuleManager moduleManager() {
        return ModuleManager.getInstance();
    }
}
