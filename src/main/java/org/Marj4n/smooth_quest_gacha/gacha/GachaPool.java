package org.Marj4n.smooth_quest_gacha.gacha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GachaPool {

    private final List<GachaEntry> entries = new ArrayList<>();

    public void add(GachaEntry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    public List<GachaEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int getTotalWeight() {
        int total = 0;

        for (GachaEntry entry : entries) {
            total += entry.getWeight();
        }

        return total;
    }
}