/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.entity.buff;

import mc.sayda.mcraze.entity.LivingEntity;

public class Buff {
    private BuffType type;
    private int duration; // In ticks
    private int amplifier; // Level (0 = Level I, 1 = Level II)
    private LivingEntity source; // Who applied it (optional)

    public Buff(BuffType type, int duration, int amplifier, LivingEntity source) {
        this.type = type;
        this.duration = duration;
        this.amplifier = amplifier;
        this.source = source;
    }

    public Buff(BuffType type, int duration, int amplifier) {
        this(type, duration, amplifier, null);
    }

    public void tick() {
        if (duration > 0) {
            duration--;
        }
    }

    public boolean isExpired() {
        return duration <= 0;
    }

    public BuffType getType() {
        return type;
    }

    public int getDuration() {
        return duration;
    }

    public int getAmplifier() {
        return amplifier;
    }

    // For refreshing duration/amplifier
    public void combine(Buff newBuff) {
        if (newBuff.amplifier > this.amplifier) {
            this.amplifier = newBuff.amplifier;
            this.duration = newBuff.duration;
        } else if (newBuff.amplifier == this.amplifier) {
            if (newBuff.duration > this.duration) {
                this.duration = newBuff.duration;
            }
        }
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = amplifier;
    }
}
