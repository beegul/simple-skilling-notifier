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
	// --- DEPENDENCY INJECTIONS ---
	// These pull in the core tools from RuneLite that our plugin needs to function.
	@Inject
	private Client client; // The game client itself

	@Inject
	private SimpleSkillingConfig config; // Our custom settings menu

	@Inject
	private Notifier notifier; // RuneLite's built-in alert system

	// --- TRACKING VARIABLES ---
	// These remember what the player is currently doing so we can check on it later.
	private NPC currentFishingSpot = null;
	private TileObject currentRock = null;
	private WorldPoint lastPlayerLocation = null;
	private int lastBusyTick = 0;

	// A list of standard names for fishing spots so we know what to look for.
	private static final List<String> FISHING_SPOT_NAMES = Arrays.asList(
			"fishing spot", "rod fishing spot", "grand exchange fishing spot"
	);

	// --- PLUGIN LIFECYCLE ---
	// What happens when you turn the plugin on or off in the RuneLite sidebar.
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

	// A simple helper to wipe the slate clean and stop tracking things.
	private void reset()
	{
		currentFishingSpot = null;
		currentRock = null;
		lastPlayerLocation = null;
		lastBusyTick = 0;
	}

	// --- EVENT: GAME STATE CHANGED ---
	// This runs when the game loads, logs out, or hops worlds.
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// If the player logs out or hops, we want to stop tracking their current rock/fishing spot.
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	// --- HELPER: IS MINING OPTION ---
	// Checks if the text we clicked on is a valid mining action.
	private boolean isMiningOption(String option)
	{
		String clean = Text.removeTags(option).toLowerCase();
		return clean.equals("mine") || clean.equals("chip");
	}

	// --- HELPER: IS FISHING OPTION ---
	// Checks if the text we clicked on is a valid fishing action.
	private boolean isFishingOption(String option)
	{
		String clean = Text.removeTags(option).toLowerCase();

		// Catch variations of using items on a spot, like "Use-rod" or "Use"
		if (clean.startsWith("use"))
		{
			return true;
		}

		// Catch standard fishing actions
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

	// --- EVENT: MENU OPTION CLICKED ---
	// This runs every time the player clicks an action in the game.
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked ev)
	{
		String option = ev.getMenuOption();

		if (option == null)
		{
			return;
		}

		// Ignore the Fossil Island Volcano rocks, as they behave differently and can cause bugs.
		if (ev.getId() == ObjectID.FOSSIL_VOLCANO_CHAMBER_BLOCKED)
		{
			return;
		}

		MenuAction action = ev.getMenuAction();

		// ==========================================
		// 1. HANDLE MINING CLICKS
		// ==========================================
		// Check if we clicked a Game Object (like a rock) AND the action was "Mine" or "Chip"
		if ((action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
				|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
				|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION) && isMiningOption(option))
		{
			// Figure out exactly which tile in the world the player clicked on
			WorldPoint wp = WorldPoint.fromScene(client.getTopLevelWorldView(), ev.getParam0(), ev.getParam1(), client.getTopLevelWorldView().getPlane());
			Tile tile = client.getTopLevelWorldView().getScene().getTiles()[wp.getPlane()][ev.getParam0()][ev.getParam1()];

			if (tile != null)
			{
				// Step A: Search for standard rocks (Game Objects) on that tile
				TileObject target = Arrays.stream(tile.getGameObjects())
						.filter(obj -> {
							if (obj == null) return false;
							if (obj.getId() == ev.getId()) return true;

							// Check for "impostors" (objects that change states visually)
							ObjectComposition def = client.getObjectDefinition(obj.getId());
							if (def.getImpostorIds() != null)
							{
								def = def.getImpostor();
							}
							return def.getId() == ev.getId();
						})
						.findFirst()
						.orElse(null);

				// Step B: If no standard rock was found, check the walls.
				// This is required because Motherlode Mine ore veins are considered "Wall Objects".
				if (target == null && tile.getWallObject() != null && tile.getWallObject().getId() == ev.getId())
				{
					target = tile.getWallObject();
				}

				// If we found the rock or vein, start tracking it and reset our idle timer!
				if (target != null)
				{
					currentRock = target;
					currentFishingSpot = null;
					lastBusyTick = client.getTickCount();
				}
			}
		}

		// ==========================================
		// 2. HANDLE FISHING CLICKS
		// ==========================================
		// Check if we clicked an NPC (fishing spots are technically NPCs) AND the action was valid
		else if ((action == MenuAction.NPC_FIRST_OPTION || action == MenuAction.NPC_SECOND_OPTION
				|| action == MenuAction.NPC_THIRD_OPTION || action == MenuAction.NPC_FOURTH_OPTION
				|| action == MenuAction.NPC_FIFTH_OPTION || action == MenuAction.WIDGET_TARGET_ON_NPC)
				&& isFishingOption(option))
		{

			// Find the specific fishing spot we clicked on in the world
			NPC target = client.getTopLevelWorldView().npcs().stream()
					.filter(n -> n.getIndex() == ev.getId())
					.findFirst()
					.orElse(null);

			// Verify it's actually a fishing spot by checking its name against our list
			if (target != null && target.getName() != null)
			{
				String cleanName = Text.removeTags(target.getName()).toLowerCase();
				if (FISHING_SPOT_NAMES.contains(cleanName))
				{
					// Start tracking the fishing spot and reset our idle timer!
					currentFishingSpot = target;
					currentRock = null;
					lastBusyTick = client.getTickCount();
				}
			}
		}
	}

	// --- EVENT: GAME TICK ---
	// This is the heartbeat of the plugin. It runs 100 times a minute (every 0.6 seconds).
	@Subscribe
	public void onGameTick(GameTick ev)
	{
		// If we aren't currently tracking a rock or a fishing spot, do nothing and exit early.
		if (currentRock == null && currentFishingSpot == null)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null) return;

		// ==========================================
		// STEP 1: CHECK IF PLAYER IS BUSY
		// ==========================================
		boolean isMoving = false;
		WorldPoint currentLocation = player.getWorldLocation();

		// Compare where we are now to where we were last tick to see if we are walking
		if (lastPlayerLocation != null && !currentLocation.equals(lastPlayerLocation))
		{
			isMoving = true;
		}
		lastPlayerLocation = currentLocation;

		// Check if our character is performing an animation (like swinging a pickaxe)
		boolean isAnimating = player.getAnimation() != -1;

		// If we are moving or animating, we are NOT idle. Reset the timer and stop checking for now.
		if (isMoving || isAnimating)
		{
			lastBusyTick = client.getTickCount();
			return;
		}

		// ==========================================
		// STEP 2: CHECK IF THE RESOURCE VANISHED
		// ==========================================
		boolean resourceExists = false;

		if (currentFishingSpot != null)
		{
			// Loop through all NPCs to make sure our fishing spot hasn't despawned.
			// We use a simple loop here instead of Streams to save memory.
			boolean isStillInWorld = false;
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (npc == currentFishingSpot)
				{
					isStillInWorld = true;
					break;
				}
			}
			// The spot exists if it hasn't despawned and isn't "dead"
			resourceExists = !currentFishingSpot.isDead() && isStillInWorld;
		}
		else if (currentRock != null)
		{
			// Check if the rock or ore vein we were mining is still on the ground/wall.
			WorldPoint rockLoc = currentRock.getWorldLocation();
			if (client.getTopLevelWorldView().getPlane() == rockLoc.getPlane())
			{
				Tile tile = client.getTopLevelWorldView().getScene().getTiles()[rockLoc.getPlane()][currentRock.getLocalLocation().getSceneX()][currentRock.getLocalLocation().getSceneY()];
				if (tile != null)
				{
					// First check if it's a Wall Object (like Motherlode Mine)
					if (tile.getWallObject() == currentRock)
					{
						resourceExists = true;
					}
					else
					{
						// If not a wall, check the standard Game Objects.
						// We use a simple loop here to avoid heavy memory allocation.
						GameObject[] gameObjects = tile.getGameObjects();
						if (gameObjects != null)
						{
							for (GameObject obj : gameObjects)
							{
								if (obj == currentRock)
								{
									resourceExists = true;
									break;
								}
							}
						}
					}
				}
			}
		}

		// ==========================================
		// STEP 3: CHECK IF INVENTORY IS FULL
		// ==========================================
		boolean inventoryFull = false;
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			// A full inventory in OSRS is 28 items
			inventoryFull = inventory.count() == 28;
		}

		// ==========================================
		// STEP 4: SEND THE NOTIFICATION
		// ==========================================
		// If the rock/spot is gone, OR our inventory is full, we are idle!
		if (!resourceExists || inventoryFull)
		{
			// Have we been idle longer than the user's custom delay setting?
			if (client.getTickCount() - lastBusyTick > config.idleDelay())
			{
				notifyPlayer();
			}
		}
	}

	// --- HELPER: NOTIFY PLAYER ---
	// Actually fires off the alert using RuneLite's notification system.
	private void notifyPlayer()
	{
		// Double check the user's configuration before sending the alert to prevent spam
		if (currentFishingSpot != null && config.fishingNotify().isEnabled())
		{
			notifier.notify(config.fishingNotify(), config.customMessage());
		}
		else if (currentRock != null && config.miningNotify().isEnabled())
		{
			notifier.notify(config.miningNotify(), config.customMessage());
		}

		// Once we notify them, wipe the tracking so we don't notify them again until they click a new spot.
		reset();
	}

	// --- CONFIG PROVIDER ---
	// Tells RuneLite where to find our custom settings menu
	@Provides
	SimpleSkillingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SimpleSkillingConfig.class);
	}
}