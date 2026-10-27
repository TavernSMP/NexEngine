package su.nexmedia.engine.utils.values;

import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.api.config.JYML;
import su.nexmedia.engine.utils.random.Rnd;

public final class UniInt {

    private final int minInclusive;

    private final int maxInclusive;

    private UniInt(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    @NotNull
    public static UniInt of(int minInclusive, int maxInclusive) {
        return new UniInt(minInclusive, maxInclusive);
    }

    @NotNull
    public static UniInt read(@NotNull JYML cfg, @NotNull String path) {
        int min = cfg.getInt(path + ".Min");
        int max = cfg.getInt(path + ".Max");
        return of(min, max);
    }

    public void write(@NotNull JYML cfg, @NotNull String path) {
        cfg.set(path + ".Min", this.getMinValue());
        cfg.set(path + ".Max", this.getMaxValue());
    }

    public int roll() {
        return Rnd.get(this.minInclusive, this.maxInclusive);
    }

    public int getMinValue() {
        return this.minInclusive;
    }

    public int getMaxValue() {
        return this.maxInclusive;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
