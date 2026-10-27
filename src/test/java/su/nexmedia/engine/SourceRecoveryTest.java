package su.nexmedia.engine;

import static org.junit.Assert.*;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.Assume;
import org.junit.Test;
import su.nexmedia.engine.utils.EntityUtil;
import su.nexmedia.engine.utils.Evaluator;
import su.nexmedia.engine.utils.StringUtil;
import su.nexmedia.engine.utils.random.MTRandom;
import su.nexmedia.engine.utils.values.UniInt;

public class SourceRecoveryTest {
    @Test
    public void randomSequencesMatchTheDeployedEngine() throws Exception {
        Path original = Path.of(System.getProperty("originalJar"));
        Assume.assumeTrue("Supply -PoriginalJar to compare deployed bytecode", Files.isRegularFile(original));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{original.toUri().toURL()}, null)) {
            Class<?> type = loader.loadClass(MTRandom.class.getName());
            for (long seed : new long[]{0, 1, -1, 5489, Long.MAX_VALUE}) {
                Random before = (Random) type.getConstructor().newInstance();
                before.setSeed(seed);
                Random after = new MTRandom();
                after.setSeed(seed);
                for (int index = 0; index < 1000; index++) {
                    assertEquals(before.nextInt(), after.nextInt());
                    assertEquals(before.nextDouble(), after.nextDouble(), 0);
                }
            }
        }
    }

    @Test
    public void expressionParsingKeepsPrecedenceAndValidation() {
        assertEquals(14, Evaluator.evaluate("2 + 3 * 4"), 0);
        assertEquals(20, Evaluator.evaluate("(2 + 3) * 4"), 0);
        assertEquals(8, Evaluator.evaluate("2^3"), 0);
        assertThrows(RuntimeException.class, () -> Evaluator.evaluate("2+garbage"));
    }

    @Test
    public void numericFallbackAndRangesKeepTheirValues() {
        assertEquals(42, StringUtil.getInteger("42", 7));
        assertEquals(7, StringUtil.getInteger("invalid", 7));
        assertEquals(2.5, StringUtil.getDouble("2.5", 7), 0);
        assertEquals(7, StringUtil.getDouble("invalid", 7), 0);
        UniInt range = UniInt.of(3, 9);
        assertEquals(3, range.getMinValue());
        assertEquals(9, range.getMaxValue());
        for (int index = 0; index < 100; index++) {
            int value = range.roll();
            assertTrue(value >= 3 && value <= 9);
        }
    }

    @Test
    public void legacyCounterRetainsItsApiWithoutMinecraftReflection() {
        int initial = EntityUtil.ENTITY_COUNTER.get();
        try {
            assertTrue(initial < 0);
            assertTrue(EntityUtil.ENTITY_COUNTER.incrementAndGet() < 0);
            assertTrue(EntityUtil.ENTITY_COUNTER.decrementAndGet() < 0);
        } finally {
            EntityUtil.ENTITY_COUNTER.set(initial);
        }
    }
}
