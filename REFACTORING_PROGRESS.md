# MCraze Refactoring Progress

**Started:** 2025-12-14
**Completed:** 2025-12-14
**Goal:** Clean up codebase, split large classes, improve maintainability

---

## Overall Progress

- [x] **Phase 0: Package Restructure** ✅
- [x] **Phase 1: Quick Wins (Code cleanup)** ✅
- [x] **Phase 2: Class Splitting (Architecture improvement)** ✅
- [x] **Phase 3: TODO/HACK Fixes** ✅
- [x] **Phase 4: Game Features & Multiplayer Preparation** ✅
- [x] **Phase 5: Integrated Server Architecture** ✅
- [ ] Phase 6: Dedicated Multiplayer Server (Future)
- [ ] Phase 7: Advanced Refactoring (Optional future improvements)

---

## ✅ Phase 0: Package Restructure - COMPLETED

### Package Rename
- [x] Rename `com.github.jleahey.minicraft` → `mc.sayda`
  - [x] Move directory structure
  - [x] Update package declarations in all .java files (33 files)
  - [x] Update import statements across entire codebase
  - [x] Update build.xml references (Main-Class, exclude path)
  - [x] Verify compilation ✓

### New Package Structure Created
- [x] `mc.sayda.entity` - Entity, LivingEntity, Player
- [x] `mc.sayda.world` - World, WorldGenerator, Tile, TileTemplate, TileType
- [x] `mc.sayda.item` - Item, Tool, ItemLoader, InventoryItem
- [x] `mc.sayda.ui` - UIRenderer, Inventory, MainMenu
- [x] `mc.sayda.system` - LightingEngine, BlockInteractionSystem
- [x] `mc.sayda.util` - StockMethods, SystemTimer, Template, Int2, BoundsChecker
- [x] `mc.sayda.awtgraphics` - AWT platform implementation

---

## ✅ Phase 1: Quick Wins - COMPLETED

### Code Cleanup
- [x] Remove dead commented code
  - [x] Player.java (removed 48 lines of old raycasting code)
  - [x] All other commented blocks cleaned

- [x] Remove unnecessary dependencies
  - [x] Removed timer.jar (native library dependency)
  - [x] Refactored SystemTimer to use System.nanoTime()
  - [x] Cleaned up 1 external dependency

- [x] Add boundary checking utilities
  - [x] Created `BoundsChecker.java` utility class
  - [x] Added `isInBounds()`, `isXInBounds()`, `isYInBounds()` methods

- [x] Add class-level Javadoc
  - [x] UIRenderer fully documented
  - [x] BlockInteractionSystem fully documented
  - [x] BoundsChecker fully documented
  - [x] SystemTimer updated with documentation

---

## ✅ Phase 2: Class Splitting - COMPLETED

### Game.java Refactoring ✅ (433 lines → 302 lines = 131 lines removed / 30% reduction)
- [x] Extract `UIRenderer` class (199 lines)
  - [x] Move HUD rendering
  - [x] Move health bar rendering (hearts)
  - [x] Move air bubble rendering (underwater)
  - [x] Move builder/miner icons
  - [x] Move mouse cursor rendering
  - [x] Move FPS counter
  - [x] Move centered drawing utilities

- [x] Extract `BlockInteractionSystem` class (178 lines)
  - [x] Move block breaking logic
  - [x] Move block placing logic
  - [x] Move tool durability calculations
  - [x] Move block drop mechanics
  - [x] Move crafting table interaction

### Player.java Cleanup ✅ (238 lines → 110 lines = 128 lines removed / 54% reduction)
- [x] Removed 48 lines of dead commented raycasting code
- [x] Simplified block targeting system (removed complex raycasting)
  - [x] Replaced handBreakPos and handBuildPos with unified handTargetPos
  - [x] Rewrote updateHand() method to use simple hover + distance checking
  - [x] Removed 100+ lines of complex intersection/raycasting logic
  - [x] Now uses straightforward Euclidean distance calculation

---

## ✅ Block Interaction System Simplification - COMPLETED

### Problem
The original block breaking/placing system had major issues:
- Separate targeting for breaking (handBreakPos) and placing (handBuildPos)
- Complex raycasting logic with line-segment intersections
- Could replace existing blocks when hovering over them
- Over-engineered prediction system

### Solution
Completely redesigned with simple hover-based targeting:
- **Unified targeting**: Single `handTargetPos` for both breaking and placing
- **Distance-based**: Simple Euclidean distance check (if distance <= armLength, target it)
- **No raycasting**: Just converts mouse position to block coordinates
- **Proper validation**: Cannot place blocks over existing blocks

### Implementation (Player.java:50-82)
```java
public void updateHand(...) {
    float playerX = this.getCenterX(tileSize);
    float playerY = this.getCenterY(tileSize);

    int targetBlockX = (int) Math.floor(mouseX);
    int targetBlockY = (int) Math.floor(mouseY);

    float dx = (targetBlockX + 0.5f) - playerX;
    float dy = (targetBlockY + 0.5f) - playerY;
    float distance = (float) Math.sqrt(dx * dx + dy * dy);

    if (distance <= armLength) {
        handTargetPos.x = targetBlockX;
        handTargetPos.y = targetBlockY;
        handEndX = targetBlockX + 0.5f;
        handEndY = targetBlockY + 0.5f;
    } else {
        handTargetPos.x = -1;
        handTargetPos.y = -1;
        handEndX = -1;
        handEndY = -1;
    }
}
```

### Results
- Reduced Player.java from 183 lines to 110 lines (40% reduction in this phase)
- Removed 100+ lines of complex raycasting code
- Fixed block replacement bug
- Much clearer, more maintainable code

---

## 📊 Code Metrics - COMPLETED WORK

### Lines of Code Reduced
- **Game.java**: 433 → 302 lines (-131 lines / -30%)
- **Player.java**: 238 → 110 lines (-128 lines / -54%)
- **SystemTimer.java**: 50 → 34 lines (-16 lines / -32%)
- **Total reduction**: 275+ lines of code removed
- **New classes created**: 3 (UIRenderer, BlockInteractionSystem, BoundsChecker)
- **Files moved/organized**: 24 files into proper packages

### Files Created
1. `UIRenderer.java` - 199 lines of UI rendering logic
2. `BlockInteractionSystem.java` - 178 lines of block interaction
3. `BoundsChecker.java` - 54 lines of boundary utilities
4. `REFACTORING_PROGRESS.md` - Progress tracking
5. `.idea/runConfigurations/` - IntelliJ IDEA launch configs

### Dependencies Cleaned
- Removed: `timer.jar` (native Windows library with fallback issues)
- Remaining: 4 JARs (gson, easyogg, jogg, jorbis - all functional)

---

---

## ✅ Phase 3: TODO/HACK Fixes - COMPLETED

### All TODOs and HACKs Fixed
- [x] **MainMenu.java** - Fixed mouse X-value checking (2 locations)
  - [x] Added proper button hit detection using both X and Y coordinates
  - [x] Buttons now only highlight/click when mouse is within their bounds
  - [x] Eliminated false clicks from hovering at wrong X position

- [x] **LivingEntity.java** - Fixed water physics HACKs (2 locations)
  - [x] Removed `dy = -maxWaterDY - .000001f` hack
  - [x] Created proper `swimUpVelocity` constant in Entity.java
  - [x] Clean, documented solution for swimming/climbing in water

- [x] **AwtGraphicsHandler.java** - Fixed color allocation performance
  - [x] Added color caching to avoid creating new Color objects every frame
  - [x] Implemented equals() and hashCode() in mc.sayda.Color class
  - [x] Significant performance improvement for repeated color usage

- [x] **Game.java:247** - Moved toss item logic into Player class
  - [x] Created `Player.tossSelectedItem()` method
  - [x] Simplified Game.tossItem() to 4 lines
  - [x] Better separation of concerns

- [x] **AwtEventsHandler.java:26** - Refactored event handling
  - [x] Created input handling methods in Game class
  - [x] Created `Player.scrollHotbar()` method
  - [x] Eliminated direct field access from event handlers
  - [x] Cleaner, more maintainable input system

- [x] **AwtGraphicsHandler.java:78** - Restored missing feature
  - [x] Window close listener was already present
  - [x] Removed outdated TODO comment

- [x] **AwtGraphicsHandler.java:138** - Refactored sprite serialization
  - [x] Implemented lazy loading pattern in AwtSprite.getImage()
  - [x] Eliminated redundant null checks on every draw call
  - [x] Cleaner separation of serialization concerns

- [x] **ItemLoader.java:28** - Addressed Gson streaming API TODO
  - [x] Kept fromJson() approach (appropriate for small config files)
  - [x] Added better error handling with printStackTrace()
  - [x] Documented design decision

- [x] **LivingEntity.java:162** - Added death handling
  - [x] Created `isDead()` method
  - [x] Created `onDeath()` method with extensibility for subclasses
  - [x] Added sound effect placeholders for future implementation

### Code Quality Improvements
- **Removed**: 2 BIG HACKs, 7 TODOs
- **Added**: Proper abstractions and documented solutions
- **Improved**: Input handling, serialization, physics, rendering performance

---

## ✅ Phase 4: Game Features & Multiplayer Preparation - COMPLETED

### Death & Respawn System
- [x] **Death Handler** (Player.java, LivingEntity.java, Game.java)
  - [x] Added `dead` flag to prevent multiple death triggers
  - [x] Death triggers exactly at 0 health (not below)
  - [x] `Player.dropAllItems()` - scatters inventory with physics
  - [x] `Game.handlePlayerDeath()` - orchestrates death logic
  - [x] Death screen overlay with respawn instructions
  - [x] Game pauses on death

- [x] **Respawn Mechanic**
  - [x] `Player.respawn()` - resets player state
  - [x] Spawn location tracking (`spawnX`, `spawnY`)
  - [x] 'R' key to respawn after death
  - [x] Game resumes on respawn

- [x] **Game Rules System**
  - [x] `keepInventory` - keep items on death (default: false)
  - [x] `daylightCycle` - enable/disable day-night cycle (default: true)
  - [x] Configurable via `/gamerule` command

### Chat & Command System
- [x] **Chat UI** (Chat.java - 230 lines)
  - [x] Message history with auto-hide after 5 seconds
  - [x] Color-coded messages
  - [x] Open with 'T' key
  - [x] Input box with cursor
  - [x] Blocks game input when open

- [x] **Command Handler** (CommandHandler.java - 190 lines)
  - [x] Command parsing with `/` prefix
  - [x] Regular chat messages (for future multiplayer)
  - [x] Implemented commands:
    - [x] `/help` - show available commands
    - [x] `/gamerule <rule> [value]` - manage game rules
    - [x] `/time <set|add> <value>` - control world time
    - [x] `/kill` - suicide command

- [x] **Dynamic Tab Completion** (Context-aware, multi-level)
  - [x] **Level 1**: Command completion (`/` → `/help`, `/gamerule`, etc.)
  - [x] **Level 2**: Argument completion (`/gamerule ` → `keepInventory`, `daylightCycle`)
  - [x] **Level 3**: Value completion (`/gamerule keepInventory ` → `true`, `false`)
  - [x] Automatic discovery from CommandHandler registry
  - [x] Prefix matching (e.g., `/g` → `/gamerule`)
  - [x] TAB key cycles through matches
  - [x] Focus traversal disabled for TAB capture

- [x] **Command History**
  - [x] Arrow UP/DOWN to navigate through past commands
  - [x] Stores last 50 commands
  - [x] Resets when submitting new command

### Input Handling Improvements
- [x] **Chat Input** (AwtEventsHandler.java)
  - [x] ESC closes chat without going to menu
  - [x] ENTER submits command/message
  - [x] BACKSPACE deletes characters
  - [x] TAB for auto-completion
  - [x] Arrow UP/DOWN for history
  - [x] Regular typing for chat input
  - [x] Event consumption to prevent conflicts

- [x] **World Time Control**
  - [x] `World.getTicksAlive()` and `setTicksAlive()` methods
  - [x] Encapsulated time access
  - [x] `/time set 0` (dawn), `6000` (noon), `12000` (dusk), `18000` (midnight)
  - [x] `/time add <ticks>` for relative time changes

### Multiplayer Preparation
- [x] **Chat message support**
  - [x] Non-command messages display as `<Player> message`
  - [x] Console logging: `[CHAT] Player: message`
  - [x] TODO comment for multiplayer server integration

- [x] **Architecture ready for multiplayer**
  - [x] Chat system separates commands from messages
  - [x] CommandHandler can be extended for server commands
  - [x] Message formatting prepared for player names
  - [x] Network layer placeholder ready

### Code Quality
- **New Files**: 2 (Chat.java, CommandHandler.java)
- **Lines Added**: 420+ lines of new functionality
- **Dynamic System**: Tab completion automatically discovers new commands
- **Extensible**: Easy to add new commands with full tab completion support

---

## ✅ Phase 5: Integrated Server Architecture - COMPLETED

**Goal:** Prepare for multiplayer by implementing client-server separation with integrated server for singleplayer

### Network Infrastructure Created
- [x] **mc.sayda.network package** - Network communication layer
  - [x] `Connection.java` (37 lines) - Abstract connection interface
  - [x] `LocalConnection.java` (83 lines) - In-process communication for integrated server
  - [x] `Packet.java` (28 lines) - Base class for all network packets
  - [x] `PacketHandler.java` (30 lines) - Interface for processing packets

- [x] **mc.sayda.network.packet package** - 6 packet types
  - [x] `PacketPlayerInput.java` - Client → Server player input (movement, clicks, hotbar)
  - [x] `PacketBlockChange.java` - Client → Server block break/place
  - [x] `PacketChatSend.java` - Client → Server chat message/command
  - [x] `PacketChatMessage.java` - Server → Client chat display (with color)
  - [x] `PacketWorldUpdate.java` - Server → Client world state sync
  - [x] `PacketEntityUpdate.java` - Server → Client entity positions

### Architecture Separation
- [x] **Server.java** (281 lines) - ALL game logic and world state
  - [x] World simulation and entity management
  - [x] Game rules (keepInventory, daylightCycle)
  - [x] Packet processing from clients
  - [x] Block interaction system
  - [x] Player death handling
  - [x] Tick-based game loop

- [x] **Client.java** (278 lines) - ALL rendering and input
  - [x] Graphics rendering (world, entities, UI)
  - [x] Input handling (mouse, keyboard)
  - [x] UI management (chat, menu, inventory)
  - [x] Block interaction system (for integrated server)
  - [x] Audio playback
  - [x] FPS display

- [x] **Game.java** - Refactored (433 → 152 lines / 65% reduction)
  - [x] Creates LocalConnection pair for client-server communication
  - [x] Instantiates Server and Client
  - [x] Coordinates game loop (server.tick() + client.render())
  - [x] Handles save/load operations
  - [x] Provides accessor methods (getServer(), getClient())

### Integration & Fixes
- [x] **Input Routing** - Updated to use Server/Client getters
  - [x] AwtEventsHandler.java - Routes input through game.getServer()/game.getClient()
  - [x] AwtGraphicsHandler.java - Updated window close handler
  - [x] Movement keys → server.player methods
  - [x] Mouse position → client.setMousePosition()
  - [x] Mouse clicks → client.setLeftClick()/setRightClick()

- [x] **MainMenu Integration** - Fixed circular dependency
  - [x] Added MainMenu.setGame() method
  - [x] Added Client.setGame() method
  - [x] Game calls client.setGame(this) after construction
  - [x] MainMenu accesses client fields via game.getClient()

- [x] **GraphicsHandler Initialization** - Fixed timing
  - [x] Moved GraphicsHandler.init() from Client constructor to setGame()
  - [x] Prevents NullPointerException from null Game reference
  - [x] Ensures proper initialization order

- [x] **Save/Load System** - Updated for Server architecture
  - [x] SaveLoad.doSave() - Saves game.getServer().world and entities
  - [x] SaveLoad.doLoad() - Restores world, entities, and player reference
  - [x] Game.startGame() - Properly handles load vs new game
  - [x] Game.saveGame() - Public method to save current state
  - [x] Client.goToMainMenu() - Auto-saves before returning to menu

- [x] **Chat System Integration** - Server-side processing
  - [x] Server.setChat() - Connects chat reference for command output
  - [x] CommandHandler - Updated to work with Server instead of Game
  - [x] Chat messages flow: Client → PacketChatSend → Server → CommandHandler → PacketChatMessage → Client
  - [x] Game.submitChat() - Sends chat via packet system

- [x] **Block Interactions** - Fixed for integrated server
  - [x] Client has persistent BlockInteractionSystem instance
  - [x] Mining progress tracked across frames
  - [x] Left click calls handleBlockBreaking() every frame while held
  - [x] Right click calls handleBlockPlacing()
  - [x] Clicks persist while mouse held (not consumed immediately)

- [x] **Color Serialization** - Network transmission support
  - [x] Color.toRGB() - Converts ARGB to int
  - [x] Color.fromRGB() - Reconstructs Color from int
  - [x] Enables color transmission in PacketChatMessage

### Design Decisions

**Integrated Server Approach:**
- Singleplayer runs Server and Client in same JVM
- Communication via LocalConnection (synchronized packet queues)
- Direct server access used for performance (e.g., movement, block interactions)
- Packet system ready but not fully utilized for integrated server
- Same Server code will work for dedicated multiplayer server

**Input Handling Strategy:**
- Movement: Direct calls to server.player (via AwtEventsHandler)
- Block breaking/placing: Direct calls to blockInteractionSystem (in Client)
- Chat: Packet-based (PacketChatSend → Server → PacketChatMessage)
- Future multiplayer: All input will go through packets

**Initialization Order:**
1. Game creates LocalConnection pair
2. Game creates Server with connection
3. Game creates Client with connection + localServer reference
4. Game calls server.setChat(client.chat)
5. Game calls client.setGame(this)
6. Client.setGame() initializes GraphicsHandler and MainMenu

### Code Metrics
- **New Files Created**: 10 (Connection, LocalConnection, Packet, PacketHandler, 6 packet types)
- **Major Refactors**: 3 (Game.java, Server.java created, Client.java created)
- **Files Updated**: 8 (AwtEventsHandler, AwtGraphicsHandler, MainMenu, SaveLoad, CommandHandler, Color, Game, Client)
- **Lines Added**: ~800 lines of network/server/client code
- **Game.java Reduction**: 433 → 152 lines (65% reduction)
- **Architecture**: Fully separated client-server model ready for multiplayer

### Testing Results
- [x] Project compiles successfully
- [x] Game launches with integrated server
- [x] Player movement works (WASD, climbing)
- [x] Block breaking works (hold mouse to mine, progress tracking)
- [x] Block placing works (right click)
- [x] Inventory works (E to open, mouse wheel scroll)
- [x] Chat works (T to open, commands process server-side)
- [x] Commands work (/help, /gamerule, /time, /kill)
- [x] Save/load works (Escape saves, Load button restores)
- [x] Main menu works (New game sizes, Load, Quit)
- [x] Death/respawn works
- [x] Day/night cycle works

### Known Limitations
- Movement input not sent via packets (direct server access used)
- Block interactions not sent via packets (direct server access used)
- Server tick rate not configurable (runs at render FPS)
- No network optimization (not needed for integrated server)

These limitations are intentional for integrated server performance and will be addressed when implementing dedicated multiplayer server.

---

## 📡 Phase 6: Dedicated Multiplayer Server (Future)

### Network Architecture
- [ ] **Client-Server Model**
  - [ ] Dedicated server mode
  - [ ] Client connection management
  - [ ] TCP/IP socket communication
  - [ ] Protocol design (packet format)
  - [ ] Connection handshake

- [ ] **Game State Synchronization**
  - [ ] World state broadcasting
  - [ ] Entity position updates
  - [ ] Block changes propagation
  - [ ] Inventory synchronization
  - [ ] Player actions replication

### Multiplayer Features
- [ ] **Player Management**
  - [ ] Player list tracking
  - [ ] Player join/leave events
  - [ ] Player name display
  - [ ] Player skin support (optional)
  - [ ] Spawn point assignment

- [ ] **Chat System Integration**
  - [ ] Broadcast chat messages to all players
  - [ ] Player name prefixes
  - [ ] Server messages (join/leave notifications)
  - [ ] Private messaging (optional)
  - [ ] Chat distance limits (optional)

- [ ] **World Management**
  - [ ] Shared world instance
  - [ ] Chunk loading/unloading coordination
  - [ ] Block break/place validation
  - [ ] Conflict resolution (simultaneous edits)
  - [ ] World saving (server-side)

- [ ] **Permissions & Administration**
  - [ ] Operator (OP) system
  - [ ] Command permissions
  - [ ] Player kick/ban
  - [ ] Server configuration file
  - [ ] MOTD (Message of the Day)

### Technical Requirements
- [ ] **Serialization**
  - [ ] Entity serialization for network
  - [ ] World chunk serialization
  - [ ] Inventory serialization
  - [ ] Efficient delta updates

- [ ] **Security**
  - [ ] Input validation
  - [ ] Anti-cheat measures
  - [ ] Rate limiting
  - [ ] Authenticated connections (optional)

- [ ] **Performance**
  - [ ] Client-side prediction
  - [ ] Server tick rate optimization
  - [ ] Bandwidth optimization
  - [ ] Entity interpolation

### Implementation Steps
1. [ ] Create `mc.sayda.network` package
2. [ ] Implement basic server (ServerMain.java)
3. [ ] Implement basic client (ClientConnection.java)
4. [ ] Create packet system (Packet.java, PacketHandler.java)
5. [ ] Implement player synchronization
6. [ ] Implement world synchronization
7. [ ] Integrate chat broadcasting
8. [ ] Add commands (/kick, /ban, /op, /deop, /list)
9. [ ] Implement spawn management
10. [ ] Add server configuration
11. [ ] Testing and debugging
12. [ ] Performance optimization

---

## 🎯 Phase 7: Advanced Refactoring (Optional)

### World.java Refactoring (417 lines → could be ~250 lines)
- [ ] Extract `WorldRenderer` class
  - [ ] Move draw() method
  - [ ] Move sky color interpolation
  - [ ] Move tile rendering logic

- [ ] Extract `BlockPhysicsSystem` class
  - [ ] Move chunk update logic
  - [ ] Move grass growth
  - [ ] Move sand falling
  - [ ] Move water flow
  - [ ] Move sapling growth

### Additional Potential Improvements
- [ ] Extract magic numbers to Constants.java
- [ ] Replace instanceof checks with polymorphism
- [ ] Encapsulate public fields with getters/setters
- [ ] Add unit tests (JUnit framework)
  - [ ] Stack management

- [ ] Extract `InventoryRenderer` class
  - [ ] Move all rendering logic
  - [ ] Mouse coordinate calculations
  - [ ] UI layout

- [ ] Extract `CraftingSystem` class
  - [ ] Recipe matching logic
  - [ ] Crafting table logic
  - [ ] 2x2 and 3x3 grid handling

### Entity.java Refactoring (268 lines → ~150 lines)
- [ ] Extract `CollisionSystem` class
  - [ ] Pixel-perfect collision detection
  - [ ] Collision resolution

- [ ] Extract `PhysicsSystem` class
  - [ ] Gravity simulation
  - [ ] Water physics
  - [ ] Movement calculations

---

## Phase 3: Advanced Refactoring

### Architecture Improvements
- [ ] Replace instanceof checks with polymorphism
  - [ ] Game.java lines 186, 251
  - [ ] Use visitor pattern or double dispatch

- [ ] Encapsulate public fields
  - [ ] Game.leftClick, rightClick, paused → getters/setters
  - [ ] World.tiles → accessor methods
  - [ ] Entity position fields → getters/setters

- [ ] Implement proper logging
  - [ ] Replace printStackTrace() calls
  - [ ] Add logging framework (SLF4J or java.util.logging)
  - [ ] Add debug logging levels

- [ ] Fix static mutable state
  - [ ] StockMethods.java shared state issues
  - [ ] Make thread-safe or remove static

- [ ] Improve error handling
  - [ ] Remove System.exit(5) calls
  - [ ] Throw proper exceptions
  - [ ] Add exception handling strategy

### Code Quality
- [x] Address all TODOs (7 total) ✅ COMPLETED
  - [x] Game.java:247 - Moved to Player class
  - [x] LivingEntity.java:162 - Added death handling
  - [x] MainMenu.java (2 TODOs) - Fixed mouse X-value checking
  - [x] ItemLoader.java - Addressed Gson streaming API
  - [x] AwtGraphicsHandler.java (2 TODOs) - Fixed serialization, removed outdated TODO
  - [x] AwtEventsHandler.java - Refactored event handling

- [x] Address all HACKs (2 total) ✅ COMPLETED
  - [x] LivingEntity.java (2 HACKs) - Fixed water physics

- [ ] Remove @SuppressWarnings
  - [ ] SaveLoad.java unchecked warnings
  - [ ] Fix type safety properly

### Testing
- [ ] Add unit test framework (JUnit)
- [ ] Write tests for:
  - [ ] WorldGenerator (deterministic seed testing)
  - [ ] LightingEngine (light propagation)
  - [ ] Inventory (item stacking, crafting)
  - [ ] Entity physics (collision, gravity)
  - [ ] SaveLoad (serialization)

---

## Package Structure (Target)

```
src/mc/sayda/
├── core/
│   ├── Game.java (slimmed down)
│   ├── GameStateManager.java (new)
│   └── Constants.java (expanded)
├── world/
│   ├── World.java (data only)
│   ├── WorldGenerator.java
│   ├── WorldRenderer.java (new)
│   └── BlockPhysicsSystem.java (new)
├── entity/
│   ├── Entity.java (slimmed down)
│   ├── LivingEntity.java
│   ├── Player.java
│   ├── Zombie.java
│   └── Pig.java
├── system/
│   ├── PhysicsSystem.java (new)
│   ├── CollisionSystem.java (new)
│   ├── BlockInteractionSystem.java (new)
│   └── LightingEngine.java
├── ui/
│   ├── UIRenderer.java (new)
│   ├── Inventory.java / InventoryModel.java
│   ├── InventoryRenderer.java (new)
│   ├── MainMenu.java
│   └── Button.java (new)
├── input/
│   └── InputHandler.java (new)
├── item/
│   ├── Item.java
│   ├── Tool.java
│   ├── ItemLoader.java
│   └── CraftingSystem.java (new)
├── util/
│   ├── StockMethods.java (cleaned up)
│   ├── BoundsChecker.java (new)
│   └── Logger.java (new)
└── awtgraphics/
    └── [existing AWT classes]
```

---

## Testing Checklist

After each major refactoring phase:
- [ ] Project compiles without errors
- [ ] Game launches successfully
- [ ] Main menu works (new game, load, controls, quit)
- [ ] Player movement works (WASD, jumping)
- [ ] Block breaking works (all tool types)
- [ ] Block placing works
- [ ] Inventory opens and closes (E key)
- [ ] Crafting works (2x2 and 3x3)
- [ ] Lighting system works (day/night, torches)
- [ ] World generation works
- [ ] Save/load works
- [ ] Entities work (zombies, pigs)
- [ ] Water/swimming works
- [ ] Health/damage works

---

## Notes

- Each checkbox represents a completed task
- Update this file after completing each item
- Run tests after each phase
- Commit after each successful phase
