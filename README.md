# Jack's Simple Skilling Notifier

A robust, focused idle notifier for **Mining** and **Fishing** in Old School RuneScape, built for [RuneLite](https://runelite.net).

Unlike standard notifiers that simply check if a rock or fishing spot is empty, this plugin monitors your player's **animation state**, **movement**, and **specific target**. This ensures you are notified exactly when you stop skilling, even if the resource spot has moved or you were interrupted by combat.

## Features

### 🎣 Fishing Support (New!)
- **Smart Spot Tracking:** Automatically tracks the specific NPC you are fishing from. If the fishing spot moves or despawns, you get notified immediately.
- **Barbarian Fishing:** Fully supports "Use Rod" interactions for 3-tick or standard Barbarian Fishing.
- **Supported Methods:**
    - Net, Bait, Lure, Harpoon, Cage, and Big Net fishing.
    - Barbarian Fishing (Otto's Grotto).
    - Aerial Fishing.
    - Minnows & Karambwans.

### ⛏️ Mining Support
- **Rock Detection:** Tracks the specific rock you are mining. If it depletes, you are notified.
- **Animation Tracking:** Monitors your pickaxe animation. If you stop swinging for more than 1.8 seconds (3 ticks), you get notified.
- **Supported Actions:**
    - Standard Mining (Rocks, Motherlode Mine, Amethyst, etc.)
    - Dense Essence (Chipping).

### 🛡️ Smart Filters
- **Movement Filtering:** Intelligently detects when you are running to a bank or moving between spots. It suppresses "Idle" notifications while you are moving to prevent spam.
- **Interaction Safety:** Clicking your inventory (to drop fish/ore), the chatbox, or the ground does **not** falsely trigger an idle notification. The plugin knows you are still busy.
- **Custom Config:** Toggle Mining and Fishing notifications independently and set your own custom idle messages.

## Installation

1. Open RuneLite.
2. Navigate to the **Plugin Hub** (Configuration > Plugin Hub).
3. Search for **"Simple Skilling Notifier"**.
4. Click **Install**.

## Usage

1. Enable the plugin in the configuration panel.
2. Ensure **"Notify Mining"** or **"Notify Fishing"** is toggled ON.
3. Simply click a rock or fishing spot to start.
4. The plugin will automatically track your status and notify you when:
    - The resource depletes or moves.
    - Your inventory is full.
    - You stop animating (idle) for any other reason.

## License

This project is licensed under the BSD 2-Clause License.