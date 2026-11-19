package com.appllp7.glanceauto;

/** Pure Java state machine used unchanged by the Android service and offline stress tests. */
public final class AutoPolicy {
    public enum State { NEW, PASSIVE, ASLEEP_SHIELDED, CHECKING_WAKE, CLOCK, STOPPED }
    public enum Action { ATTACH_PASSIVE, PROMOTE_SHIELD, CLASSIFY_WAKE, SHOW_CLOCK,
        RESTORE_PASSIVE, REQUEST_SLEEP, IGNORE, REFUSE, STOP }
    private State state = State.NEW;
    private int cycles;
    public State state() { return state; }
    public int cycles() { return cycles; }
    public Action start(boolean awake) {
        if (state != State.NEW || !awake) return Action.REFUSE;
        state = State.PASSIVE;return Action.ATTACH_PASSIVE;
    }
    public Action screenOff() {
        if (state == State.STOPPED || state == State.NEW) return Action.IGNORE;
        if (state == State.ASLEEP_SHIELDED) return Action.IGNORE;
        state = State.ASLEEP_SHIELDED;cycles++;return Action.PROMOTE_SHIELD;
    }
    public Action screenOn() {
        if (state != State.ASLEEP_SHIELDED) return Action.IGNORE;
        state = State.CHECKING_WAKE;return Action.CLASSIFY_WAKE;
    }
    public Action wakeResolved(boolean gesture) {
        if (state != State.CHECKING_WAKE) return Action.IGNORE;
        state = gesture ? State.CLOCK : State.PASSIVE;
        return gesture ? Action.SHOW_CLOCK : Action.RESTORE_PASSIVE;
    }
    public Action wakeGuardExpired() {
        if (state != State.CHECKING_WAKE) return Action.IGNORE;
        state = State.PASSIVE;return Action.RESTORE_PASSIVE;
    }
    public Action tap() {
        if (state != State.CLOCK) return Action.IGNORE;
        state = State.PASSIVE;return Action.RESTORE_PASSIVE;
    }
    public Action clockTimeout() {
        if (state != State.CLOCK) return Action.IGNORE;
        return Action.REQUEST_SLEEP;
    }
    public Action sleepFailed() {
        if (state != State.CLOCK) return Action.IGNORE;
        state = State.PASSIVE;return Action.RESTORE_PASSIVE;
    }
    public Action stop() {state = State.STOPPED;return Action.STOP;}
}
