package jack.skillingnotifier;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

@Slf4j
@PluginDescriptor(
		name = "Jack's Simple Skilling Notifier",
		description = "Notifies you when you stop mining or fishing",
		tags = {"mining", "fishing", "idle", "notify", "skilling"}
)
public class SimpleSkillingPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private SimpleSkillingConfig config;

	@Inject
	private Notifier notifier;

	private NPC currentFishingSpot = null;
	private TileObject currentRock = null;

	private WorldPoint lastPlayerLocation = null;
	private int lastBusyTick = 0;

	// Standard names for fishing spots (lowercase for case-insensitive matching)
	private static final List<String> FISHING_SPOT_NAMES = Arrays.asList(
			"fishing spot", "rod fishing spot", "grand exchange fishing spot"
	);

	@Override
	protected void startUp()
	{
		reset();
	}

	@Override
	protected void shutDown()
	{
		reset();
	}

	private void reset()
	{
		currentFishingSpot = null;
		currentRock = null;
		lastPlayerLocation = null;
		lastBusyTick = 0;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	// Helper: Valid interactions for Rocks
	private boolean isMiningOption(String option)
	{
		String clean = Text.removeTags(option).toLowerCase();
		return clean.equals("mine") || clean.equals("chip");
	}

	// Helper: Valid interactions for Fishing Spots
	private boolean isFishingOption(String option)
	{
		String clean = Text.removeTags(option).toLowerCase();

		// FIX: Allow any option starting with "use" to catch "Use-rod", "Use rod", "Use", etc.
		if (clean.startsWith("use"))
		{
			return true;
		}

		switch (clean)
		{
			case "fish":
			case "net":
			case "small net":
			case "bait":
			case "lure":
			case "harpoon":
			case "cage":
			case "big net":
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked ev)
	{
		String option = ev.getMenuOption();

		if (option == null)
		{
			return;
		}

		// 1. Protection for Fossil Island Volcano
		if (ev.getId() == ObjectID.FOSSIL_VOLCANO_CHAMBER_BLOCKED)
		{
			return;
		}

		MenuAction action = ev.getMenuAction();

		// --- Handle Mining (Game Objects) ---
		// STRICT CHECK: Only allow mining options here. "Use" on a rock should NOT trigger this.
		if ((action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
				|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
				|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION) && isMiningOption(option))
		{
			WorldPoint wp = WorldPoint.fromScene(client.getTopLevelWorldView(), ev.getParam0(), ev.getParam1(), client.getTopLevelWorldView().getPlane());
			Tile tile = client.getTopLevelWorldView().getScene().getTiles()[wp.getPlane()][ev.getParam0()][ev.getParam1()];

			if (tile != null)
			{
				Arrays.stream(tile.getGameObjects())
						.filter(obj -> {
							if (obj == null) return false;
							if (obj.getId() == ev.getId()) return true;

							ObjectComposition def = client.getObjectDefinition(obj.getId());
							if (def.getImpostorIds() != null)
							{
								def = def.getImpostor();
							}
							return def.getId() == ev.getId();
						})
						.findFirst()
						.ifPresent(obj -> {
							currentRock = obj;
							currentFishingSpot = null;
							lastBusyTick = client.getTickCount();
						});
			}
		}
		// --- Handle Fishing (NPCs) ---
		// Allow standard options OR "Use-rod" (WIDGET_TARGET_ON_NPC)
		else if ((action == MenuAction.NPC_FIRST_OPTION || action == MenuAction.NPC_SECOND_OPTION
				|| action == MenuAction.NPC_THIRD_OPTION || action == MenuAction.NPC_FOURTH_OPTION
				|| action == MenuAction.NPC_FIFTH_OPTION || action == MenuAction.WIDGET_TARGET_ON_NPC)
				&& isFishingOption(option))
		{
			// Search NPC by index
			NPC target = client.getTopLevelWorldView().npcs().stream()
					.filter(n -> n.getIndex() == ev.getId())
					.findFirst()
					.orElse(null);

			// Double check: Is the target actually a fishing spot?
			if (target != null && target.getName() != null)
			{
				String cleanName = Text.removeTags(target.getName()).toLowerCase();
				if (FISHING_SPOT_NAMES.contains(cleanName))
				{
					currentFishingSpot = target;
					currentRock = null;
					lastBusyTick = client.getTickCount();
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick ev)
	{
		if (currentRock == null && currentFishingSpot == null)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null) return;

		// 1. Check Movement/Animation
		boolean isMoving = false;
		WorldPoint currentLocation = player.getWorldLocation();
		if (lastPlayerLocation != null && !currentLocation.equals(lastPlayerLocation))
		{
			isMoving = true;
		}
		lastPlayerLocation = currentLocation;

		boolean isAnimating = player.getAnimation() != -1;

		if (isMoving || isAnimating)
		{
			lastBusyTick = client.getTickCount();
			return;
		}

		// 2. Check Resource Existence
		boolean resourceExists = false;

		if (currentFishingSpot != null)
		{
			// Use stream().anyMatch() to be safe
			boolean isStillInWorld = client.getTopLevelWorldView().npcs().stream()
					.anyMatch(n -> n == currentFishingSpot);

			// Note: Fishing spots often stay "alive" (HP > 0) but despawn from the world list when they move.
			resourceExists = !currentFishingSpot.isDead() && isStillInWorld;
		}
		else if (currentRock != null)
		{
			WorldPoint rockLoc = currentRock.getWorldLocation();
			if (client.getTopLevelWorldView().getPlane() == rockLoc.getPlane())
			{
				Tile tile = client.getTopLevelWorldView().getScene().getTiles()[rockLoc.getPlane()][currentRock.getLocalLocation().getSceneX()][currentRock.getLocalLocation().getSceneY()];
				if (tile != null)
				{
					resourceExists = Arrays.asList(tile.getGameObjects()).contains(currentRock);
				}
			}
		}

		// 3. Check Inventory
		boolean inventoryFull = false;
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			inventoryFull = inventory.count() == 28;
		}

		// 4. Notify if Idle
		if (!resourceExists || inventoryFull)
		{
			if (client.getTickCount() - lastBusyTick > 2)
			{
				notifyPlayer();
			}
		}
	}

	private void notifyPlayer()
	{
		if (currentFishingSpot != null && config.fishingNotify().isEnabled())
		{
			notifier.notify(config.fishingNotify(), config.customMessage());
		}
		else if (currentRock != null && config.miningNotify().isEnabled())
		{
			notifier.notify(config.miningNotify(), config.customMessage());
		}
		reset();
	}

	@Provides
	SimpleSkillingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SimpleSkillingConfig.class);
	}
}