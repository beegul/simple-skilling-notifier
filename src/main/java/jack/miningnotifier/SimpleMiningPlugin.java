package jack.miningnotifier;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.Arrays;

@Slf4j
@PluginDescriptor(
		name = "Jack's Simple Mining Notifier",
		description = "Notifies you when you stop mining rocks or dense essence",
		tags = {"mining", "idle", "notify", "skilling"}
)
public class SimpleMiningPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private SimpleMiningConfig config;

	@Inject
	private Notifier notifier;

	private int id = -1;
	private WorldPoint loc = null;
	private WorldPoint lastPlayerLocation = null;
	private int lastBusyTick = 0;

	@Override
	protected void startUp()
	{
		id = -1;
		loc = null;
		lastPlayerLocation = null;
		lastBusyTick = 0;
	}

	private boolean shouldNotify(MenuOptionClicked ev)
	{
		String option = ev.getMenuOption();
		switch (option)
		{
			case "Mine":
				return ev.getId() != ObjectID.FOSSIL_VOLCANO_CHAMBER_BLOCKED;
			case "Chip":
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked ev)
	{
		String actionName = ev.getMenuAction().name();

		if (actionName.startsWith("GAME_OBJECT") && shouldNotify(ev))
		{
			ObjectComposition lc = client.getObjectDefinition(ev.getId());
			if (lc.getImpostorIds() != null)
			{
				lc = lc.getImpostor();
			}
			id = lc.getId();
			loc = WorldPoint.fromScene(client.getTopLevelWorldView(), ev.getParam0(), ev.getParam1(), client.getTopLevelWorldView().getPlane());

			lastBusyTick = client.getTickCount();
		}
	}

	private boolean check(TileObject o)
	{
		if (o == null)
		{
			return false;
		}

		ObjectComposition lc = client.getObjectDefinition(o.getId());
		if (lc.getImpostorIds() != null)
		{
			lc = lc.getImpostor();
		}

		return lc.getId() == id;
	}

	@Subscribe
	public void onGameTick(GameTick ev)
	{
		if (loc == null)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

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
		}

		if (isMoving)
		{
			return;
		}

		LocalPoint lp = LocalPoint.fromWorld(client, loc);
		if (lp == null ||
				lp.getSceneX() < 0 || lp.getSceneY() < 0 ||
				lp.getSceneX() >= Constants.SCENE_SIZE || lp.getSceneY() >= Constants.SCENE_SIZE)
		{
			resetAndNotify();
			return;
		}

		Tile t = client.getTopLevelWorldView().getScene().getTiles()[loc.getPlane()][lp.getSceneX()][lp.getSceneY()];
		if (t == null)
		{
			resetAndNotify();
			return;
		}

		boolean found = check(t.getWallObject());
		if (!found && t.getGameObjects() != null)
		{
			for (TileObject o : t.getGameObjects())
			{
				if (check(o))
				{
					found = true;
					break;
				}
			}
		}

		if (found)
		{
			ItemContainer ic = client.getItemContainer(InventoryID.INV);

			if (ic == null || ic.getItems().length < 28 || Arrays.stream(ic.getItems()).anyMatch(i -> i.getQuantity() == 0))
			{
				if (client.getTickCount() - lastBusyTick > 3)
				{
					resetAndNotify();
				}
				return;
			}
		}

		resetAndNotify();
	}

	private void resetAndNotify()
	{
		id = -1;
		loc = null;
		notifier.notify(config.notification(), config.customMessage());
	}

	@Provides
	SimpleMiningConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SimpleMiningConfig.class);
	}
}