package com.kelvin.hpoi.exhobby.hook;

import com.kelvin.hpoi.exhobby.ui.ExhobbyCard;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Activity -> card, so both hooks find the same instance without leaking it. */
public final class CardRegistry {

    private static final Map<Object, ExhobbyCard> CARDS =
            Collections.synchronizedMap(new WeakHashMap<Object, ExhobbyCard>());

    private CardRegistry() {
    }

    public static ExhobbyCard get(Object activity) {
        return CARDS.get(activity);
    }

    public static void put(Object activity, ExhobbyCard card) {
        CARDS.put(activity, card);
    }

    public static void remove(Object activity) {
        CARDS.remove(activity);
    }
}
