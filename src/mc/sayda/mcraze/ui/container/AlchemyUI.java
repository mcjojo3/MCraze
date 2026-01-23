package mc.sayda.mcraze.ui.container;

import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketAlchemyAction;
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.ui.DragHandler;
import mc.sayda.mcraze.util.Int2;

/**
 * UI logic for the Alchemy Table.
 * 3 slots: [0]=Input, [1]=Progress Indicator, [2]=Output
 */
public class AlchemyUI {
    private boolean visible = false;
    private int blockX, blockY;
    private Inventory playerInventory;

    private InventoryItem[] alchemyItems = new InventoryItem[5];
    public int storedEssence = 0;
    public int recipeCost = 0; // [NEW]
    public int brewProgress = 0;
    public int brewTimeTotal = 100;

    private DragHandler dragHandler = new DragHandler(
            mc.sayda.mcraze.network.packet.PacketInventoryDrag.ContainerType.PLAYER_INVENTORY);

    public AlchemyUI() {
        for (int i = 0; i < 5; i++) {
            alchemyItems[i] = new InventoryItem(null);
        }
    }

    public void open(int x, int y, Inventory playerInv) {
        this.blockX = x;
        this.blockY = y;
        this.playerInventory = playerInv;
        this.visible = true;
    }

    public void close() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public InventoryItem[] getAlchemySlots() {
        return alchemyItems;
    }

    public Inventory getPlayerInventory() {
        return playerInventory;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public void updateState(int essence, int progress, int total, InventoryItem[] items, int cost) {
        this.storedEssence = essence;
        this.brewProgress = progress;
        this.brewTimeTotal = total;
        this.recipeCost = cost;
        if (items != null && items.length == 5) {
            this.alchemyItems = items;
        }
    }

    public boolean update(int screenWidth, int screenHeight, Int2 mousePos,
            boolean leftClicked, boolean rightClicked, boolean leftHeld, boolean rightHeld,
            Connection connection, boolean shiftPressed) {
        if (!visible)
            return false;

        if (playerInventory != null) {
            playerInventory.holdingX = mousePos.x - 8;
            playerInventory.holdingY = mousePos.y - 8;
        }

        // Layout constants (matching AlchemyView)
        int tileSize = 16, sep = 15;
        int panelW = 5 * (tileSize + sep) + sep;
        int alcH = 3 * (tileSize + sep) + sep;
        int invW = 10 * (tileSize + sep) + sep;
        int invH = 4 * (tileSize + sep) + sep;
        int totalH = alcH + invH + sep;

        int alcX = screenWidth / 2 - panelW / 2;
        int alcY = screenHeight / 2 - totalH / 2;
        int invX = screenWidth / 2 - invW / 2;
        int invY = alcY + alcH + sep;

        int slotIdx = -1;
        boolean isAlchemy = false;

        // Check Alchemy Slots
        // Ingredients 0, 1, 2
        for (int i = 0; i < 3; i++) {
            int sx = alcX + sep + i * (tileSize + sep);
            int sy = alcY + sep + 10;
            if (mousePos.x >= sx && mousePos.x <= sx + tileSize && mousePos.y >= sy && mousePos.y <= sy + tileSize) {
                slotIdx = i;
                isAlchemy = true;
                break;
            }
        }

        // Bottle 3
        if (slotIdx == -1) {
            int fX = alcX + sep;
            int fY = alcY + sep + tileSize + sep + 10;
            if (mousePos.x >= fX && mousePos.x <= fX + tileSize && mousePos.y >= fY && mousePos.y <= fY + tileSize) {
                slotIdx = 3;
                isAlchemy = true;
            }
        }

        // Result 4
        if (slotIdx == -1) {
            int rX = alcX + panelW - sep - tileSize;
            int rY = alcY + sep + tileSize / 2 + 10;
            if (mousePos.x >= rX && mousePos.x <= rX + tileSize && mousePos.y >= rY && mousePos.y <= rY + tileSize) {
                slotIdx = 4;
                isAlchemy = true;
            }
        }

        // Check Player Inventory
        if (slotIdx == -1 && mousePos.x >= invX && mousePos.x <= invX + invW && mousePos.y >= invY
                && mousePos.y <= invY + invH) {
            int relX = mousePos.x - invX;
            int relY = mousePos.y - invY;
            Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(relX, relY, tileSize, sep, sep, sep, 10, 4);
            if (slot != null) {
                int invRow = (slot.y < 3) ? (slot.y + 3) : 6;
                slotIdx = slot.x + (invRow == 6 ? 30 : (invRow - 3) * 10);
                isAlchemy = false;
            }
        }

        boolean overAlc = (mousePos.x >= alcX && mousePos.x <= alcX + panelW && mousePos.y >= alcY
                && mousePos.y <= alcY + alcH);
        boolean overInv = (mousePos.x >= invX && mousePos.x <= invX + invW && mousePos.y >= invY
                && mousePos.y <= invY + invH);
        boolean overUI = overAlc || overInv;

        if (slotIdx != -1 && (leftClicked || rightClicked)) {
            if (connection != null) {
                if (shiftPressed && leftClicked) {
                    connection.sendPacket(new mc.sayda.mcraze.network.packet.PacketShiftClick(
                            slotIdx % 10, isAlchemy ? (slotIdx) : (slotIdx / 10),
                            mc.sayda.mcraze.network.packet.PacketShiftClick.ContainerType.ALCHEMY,
                            isAlchemy, 0));
                } else {
                    connection.sendPacket(
                            new PacketAlchemyAction(blockX, blockY, slotIdx, isAlchemy, rightClicked, false));
                }
            }
            return true;
        }
        return true;
    }
}
