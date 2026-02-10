package jack.skillingnotifier;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;

@ConfigGroup("simpleskilling")
public interface SimpleSkillingConfig extends Config
{
	@ConfigItem(
			keyName = "miningNotify",
			name = "Notify Mining",
			description = "Configures notifications for Mining",
			position = 1
	)
	default Notification miningNotify() { return Notification.ON; }

	@ConfigItem(
			keyName = "fishingNotify",
			name = "Notify Fishing",
			description = "Configures notifications for Fishing",
			position = 2
	)
	default Notification fishingNotify() { return Notification.ON; }

	@ConfigItem(
			keyName = "customMessage",
			name = "Idle Message",
			description = "The message to send when you become idle",
			position = 3
	)
	default String customMessage() { return "You are now idle!"; }
}