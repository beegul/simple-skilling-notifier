package jack.skillingnotifier;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;

// --- CONFIGURATION GROUP ---
// The @ConfigGroup name is like a hidden folder name.
// It tells RuneLite exactly where to save your settings on your computer's hard drive.
@ConfigGroup("simplemining")
public interface SimpleSkillingConfig extends Config
{
	// ==========================================
	// 1. MINING NOTIFICATION SETTINGS
	// ==========================================
	@ConfigItem(
			keyName = "notifications", // The internal hidden ID for this specific setting
			name = "Notify Mining", // The actual text you see in the RuneLite menu
			description = "Configures notifications for Mining", // The text that appears when you hover over the setting
			position = 1 // Forces this setting to appear at the very top of the menu
	)
	// By returning a "Notification" object instead of a simple true/false,
	// we tell RuneLite to automatically inject the advanced sub-menu (for custom sounds, screen flashes, etc.).
	default Notification miningNotify() { return Notification.ON; }

	// ==========================================
	// 2. FISHING NOTIFICATION SETTINGS
	// ==========================================
	@ConfigItem(
			keyName = "fishingNotify",
			name = "Notify Fishing",
			description = "Configures notifications for Fishing",
			position = 2 // Forces this to appear second in the list
	)
	// Just like mining, returning "Notification.ON" gives us the advanced sound/popup sub-menu by default.
	default Notification fishingNotify() { return Notification.ON; }

	// ==========================================
	// 3. CUSTOM IDLE MESSAGE
	// ==========================================
	@ConfigItem(
			keyName = "customMessage",
			name = "Idle Message",
			description = "The message to send when you become idle",
			position = 3 // Forces this to appear third in the list
	)
	// By returning a "String" (text), RuneLite automatically knows to create a text input box.
	// The text inside the quotation marks here is the default text it starts with.
	default String customMessage() { return "You are now idle!"; }

	// ==========================================
	// 4. IDLE DELAY (TICK TIMEOUT)
	// ==========================================
	@ConfigItem(
			keyName = "idleDelay",
			name = "Notification Delay",
			description = "How many game ticks to wait before sending the notification.",
			position = 4 // Forces this to appear at the very bottom of the menu
	)
	// By returning an "int" (a whole number), RuneLite automatically creates a number input box.
	// The number 2 here means it defaults to 2 game ticks (about 1.2 seconds) out of the box.
	default int idleDelay() { return 2; }
}