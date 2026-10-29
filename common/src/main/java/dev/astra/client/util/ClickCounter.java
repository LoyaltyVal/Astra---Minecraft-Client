package dev.astra.client.util;

import dev.astra.client.input.Mouse;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks left/right clicks over the last second. Polled once per frame. */
public final class ClickCounter {

    private final Deque<Long> left = new ArrayDeque<Long>();
    private final Deque<Long> right = new ArrayDeque<Long>();
    private boolean leftWasDown;
    private boolean rightWasDown;

    public void update() {
        long now = System.currentTimeMillis();

        boolean leftDown = Mouse.isButtonDown(0);
        if (leftDown && !leftWasDown) {
            left.addLast(now);
        }
        leftWasDown = leftDown;

        boolean rightDown = Mouse.isButtonDown(1);
        if (rightDown && !rightWasDown) {
            right.addLast(now);
        }
        rightWasDown = rightDown;
    }

    public int getLeftCps() {
        return count(left);
    }

    public int getRightCps() {
        return count(right);
    }

    private static int count(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }
        return clicks.size();
    }
}
